-- Mecânica da fila de saída.
--
-- As colunas vivem em `message` e não em uma tabela `outbox` própria: a
-- mensagem JÁ É a entrada da fila (status PENDING). Uma segunda tabela
-- duplicaria a linha, exigiria mantê-las em sincronia e criaria a pergunta
-- "qual das duas é a verdade" no primeiro bug.

ALTER TABLE message
    -- Incrementado no momento em que o worker RESERVA a mensagem, não quando
    -- termina de enviar. Assim, um processo que morre no meio do envio ainda
    -- consome uma tentativa — senão uma mensagem que derruba o worker seria
    -- reprocessada para sempre, travando a fila atrás dela.
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0,
    -- Momento a partir do qual a mensagem pode ser tentada de novo. NULL
    -- significa "agora", que é o estado de quem nunca foi tentado.
    ADD COLUMN next_attempt_at timestamptz;

-- Substitui o índice da V2, que não conhecia as colunas de tentativa e faria
-- o worker varrer mensagens que ainda estão em espera de backoff.
DROP INDEX message_pendentes_de_envio;

CREATE INDEX message_pendentes_de_envio
    ON message (next_attempt_at NULLS FIRST, created_at)
    WHERE direction = 'OUTBOUND' AND status IN ('PENDING', 'FAILED') AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Reserva de mensagens pelo worker
-- ---------------------------------------------------------------------------

-- QUARTA função SECURITY DEFINER. Em 2026-07-29 registrei que a terceira
-- seria "pela previsão de hoje, a última" — a previsão estava errada, e a
-- correção está em 03-decisoes.md.
--
-- Por que precisa existir: o worker roda em segundo plano, sem sessão e sem
-- tenant no contexto. Com o RLS ativo ele enxergaria zero linhas e nenhuma
-- mensagem sairia. Ele também não pode "escolher um tenant" antes de
-- consultar, porque justamente o que ele precisa descobrir é em quais tenants
-- há mensagem pendente.
--
-- A alternativa considerada e descartada foi um papel de banco com BYPASSRLS
-- para tarefas de fundo. É a resposta de manual, mas cria uma conexão que
-- ignora TODAS as políticas de TODAS as tabelas. Esta função ignora o RLS de
-- uma tabela só, para uma pergunta só, devolvendo apenas identificadores.
--
-- FOR UPDATE SKIP LOCKED é o que torna o worker seguro com mais de uma
-- instância: cada uma reserva um lote diferente em vez de as duas pegarem as
-- mesmas linhas e enviarem a mensagem duas vezes. Sem isso, escalar
-- horizontalmente vira duplicidade de mensagem para o cliente final.
CREATE FUNCTION reservar_mensagens_para_envio(
    p_limite integer,
    p_max_tentativas integer,
    p_espera_segundos integer
) RETURNS TABLE (message_id uuid, message_tenant_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatas AS (SELECT id
                    FROM message
                    WHERE direction = 'OUTBOUND'
                      AND status IN ('PENDING', 'FAILED')
                      -- Fila morta: atingido o teto de tentativas, a linha
                      -- simplesmente deixa de ser candidata. Não é preciso um
                      -- estado novo — o teto já é a fronteira, e a mensagem
                      -- continua visível na conversa como FAILED.
                      AND attempt_count < p_max_tentativas
                      AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                      AND deleted_at IS NULL
                    ORDER BY next_attempt_at NULLS FIRST, created_at
                    LIMIT p_limite
                    FOR UPDATE SKIP LOCKED)
UPDATE message m
SET attempt_count   = m.attempt_count + 1,
    -- Backoff exponencial já gravado na reserva. Se o worker morrer antes de
    -- concluir, a mensagem só volta a ser candidata depois da espera — e não
    -- imediatamente, em laço apertado.
    next_attempt_at = now() + (p_espera_segundos * power(2, m.attempt_count)) * interval '1 second'
FROM candidatas c
WHERE m.id = c.id
RETURNING m.id, m.tenant_id;
$$;

COMMENT ON FUNCTION reservar_mensagens_para_envio(integer, integer, integer) IS
    'Reserva um lote de mensagens de saída para o worker, atravessando o RLS porque '
        'o processo de fundo não tem tenant no contexto. Devolve apenas identificadores; '
        'o conteúdo é lido depois, já sob a política do tenant correspondente.';
