-- Credencial de canal cifrada e tabela de entrada de webhook.

-- ---------------------------------------------------------------------------
-- channel_credential
-- ---------------------------------------------------------------------------
--
-- A V2 deixou explícito que NÃO criava coluna de credencial, para não convidar
-- alguém a gravar token em claro "só por enquanto". Agora que o Telegram exige
-- uma, ela nasce cifrada.
--
-- Tabela separada de channel_connection de propósito: a conexão é lida em toda
-- ingestão de mensagem, e o segredo só é necessário no envio. Separando, a
-- consulta comum nunca carrega o material sensível para a memória da
-- aplicação, e um log acidental da entidade de conexão não pode vazar token.

CREATE TABLE channel_credential
(
    id                    uuid PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    channel_connection_id uuid        NOT NULL REFERENCES channel_connection (id),
    -- Qual segredo é este: token do bot, secret do webhook, app secret.
    kind                  text        NOT NULL,
    -- AES-256-GCM, guardado como base64(nonce || ciphertext || tag).
    -- Nunca o valor em claro, nunca um hash reversível.
    ciphertext            text        NOT NULL,
    -- Versão da chave de cifra usada. Existe para que a rotação seja possível
    -- sem reescrever tudo de uma vez: linhas antigas continuam decifráveis
    -- pela chave antiga enquanto as novas já usam a nova.
    key_version           integer     NOT NULL DEFAULT 1,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz,
    CONSTRAINT channel_credential_kind_valido
        CHECK (kind IN ('TELEGRAM_BOT_TOKEN', 'TELEGRAM_WEBHOOK_SECRET',
                        'META_ACCESS_TOKEN', 'META_APP_SECRET'))
);

CREATE UNIQUE INDEX channel_credential_unica
    ON channel_credential (channel_connection_id, kind)
    WHERE deleted_at IS NULL;

CREATE TRIGGER channel_credential_updated_at
    BEFORE UPDATE ON channel_credential
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE channel_credential ENABLE ROW LEVEL SECURITY;
ALTER TABLE channel_credential FORCE ROW LEVEL SECURITY;

CREATE POLICY channel_credential_isolamento ON channel_credential
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- inbound_event
-- ---------------------------------------------------------------------------
--
-- O evento cru do provedor, gravado antes de qualquer interpretação.
--
-- Existe por três motivos concretos:
--   1. Responder 200 rápido. Provedor que não recebe 200 reenvia e acaba
--      desativando o webhook.
--   2. Diagnóstico. Quando o payload chega diferente do esperado, o original
--      é a única forma de descobrir o quê — e ele não existe mais no provedor.
--   3. Reprocessamento. Um bug no tradutor pode ser corrigido e os eventos
--      reprocessados, o que é impossível se só a interpretação foi guardada.

CREATE TABLE inbound_event
(
    id                    uuid PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    channel_connection_id uuid        NOT NULL REFERENCES channel_connection (id),
    -- Identificador do evento no provedor (update_id no Telegram). É o que
    -- torna a recepção idempotente: o mesmo webhook reenviado não vira dois
    -- eventos.
    external_event_id     text        NOT NULL,
    payload               jsonb       NOT NULL,
    received_at           timestamptz NOT NULL DEFAULT now(),
    processed_at          timestamptz,
    attempt_count         integer     NOT NULL DEFAULT 0,
    next_attempt_at       timestamptz,
    failure_reason        text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz
);

-- Idempotência da RECEPÇÃO. Distinta da idempotência da mensagem: o Telegram
-- pode reenviar o mesmo update inteiro, e barrar aqui evita até o trabalho de
-- interpretar de novo.
CREATE UNIQUE INDEX inbound_event_idempotencia
    ON inbound_event (channel_connection_id, external_event_id);

CREATE INDEX inbound_event_pendentes
    ON inbound_event (next_attempt_at NULLS FIRST, received_at)
    WHERE processed_at IS NULL AND deleted_at IS NULL;

CREATE TRIGGER inbound_event_updated_at
    BEFORE UPDATE ON inbound_event
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE inbound_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE inbound_event FORCE ROW LEVEL SECURITY;

CREATE POLICY inbound_event_isolamento ON inbound_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- Reserva de eventos pelo worker de entrada
-- ---------------------------------------------------------------------------
--
-- QUINTA função SECURITY DEFINER, e a repetição do padrão está registrada em
-- 03-decisoes.md: se aparecer uma sexta, a decisão a revisitar é adotar um
-- papel de banco com BYPASSRLS para tarefas de fundo, em vez de continuar
-- multiplicando funções.
--
-- Mesmo motivo das anteriores: o worker roda sem sessão e precisa descobrir em
-- quais tenants há evento pendente — pergunta que o RLS impede de fazer.
CREATE FUNCTION reservar_eventos_para_processar(
    p_limite integer,
    p_max_tentativas integer,
    p_espera_segundos integer
) RETURNS TABLE (event_id uuid, event_tenant_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatos AS (SELECT id
                    FROM inbound_event
                    WHERE processed_at IS NULL
                      AND attempt_count < p_max_tentativas
                      AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                      AND deleted_at IS NULL
                    ORDER BY next_attempt_at NULLS FIRST, received_at
                    LIMIT p_limite
                    FOR UPDATE SKIP LOCKED)
UPDATE inbound_event e
SET attempt_count   = e.attempt_count + 1,
    next_attempt_at = now() + (p_espera_segundos * power(2, e.attempt_count)) * interval '1 second'
FROM candidatos c
WHERE e.id = c.id
RETURNING e.id, e.tenant_id;
$$;

COMMENT ON FUNCTION reservar_eventos_para_processar(integer, integer, integer) IS
    'Reserva eventos de webhook para o worker de entrada. Mesma justificativa da '
        'reserva da fila de saída: processo de fundo, sem tenant no contexto.';
