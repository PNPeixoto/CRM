-- Conversa, mensagem e conexão de canal.
--
-- Mesmas convenções da V1: UUID v7 gerado na aplicação, TIMESTAMPTZ em UTC,
-- exclusão lógica, RLS ENABLE + FORCE em toda tabela com tenant_id.

-- ---------------------------------------------------------------------------
-- channel_connection — módulo channel
-- ---------------------------------------------------------------------------

CREATE TABLE channel_connection
(
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenant (id),
    -- Provedor. CHECK em vez de ENUM do Postgres: acrescentar um valor a um
    -- ENUM exige ALTER TYPE, que não roda dentro de transação em versões mais
    -- antigas e complica o rollback da migration. CHECK é um ALTER TABLE comum.
    kind       text        NOT NULL,
    -- Nome exibido ao atendente: "WhatsApp Unidade Centro", "Bot de suporte".
    name       text        NOT NULL,
    -- Identificador da conta no provedor: chat id do bot no Telegram,
    -- phone_number_id no WhatsApp Cloud. NULL no chat ao vivo, que não tem
    -- conta externa.
    external_account_id text,
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT channel_connection_kind_valido
        CHECK (kind IN ('LIVE_CHAT', 'TELEGRAM', 'WHATSAPP_CLOUD', 'INSTAGRAM'))
);

-- NOTA DELIBERADA: não existe coluna de credencial aqui. O token do bot do
-- Telegram e o token da Meta entram na Fase 3/4, e quando entrarem serão
-- cifrados em repouso com chave separada da aplicação, conforme o
-- 01-padroes-tecnicos.md. Criar a coluna agora convidaria alguém a gravar o
-- token em claro "só por enquanto".

CREATE UNIQUE INDEX channel_connection_conta_unica
    ON channel_connection (tenant_id, kind, external_account_id)
    WHERE deleted_at IS NULL AND external_account_id IS NOT NULL;

CREATE INDEX channel_connection_por_tenant
    ON channel_connection (tenant_id, active) WHERE deleted_at IS NULL;

CREATE TRIGGER channel_connection_updated_at
    BEFORE UPDATE ON channel_connection
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE channel_connection ENABLE ROW LEVEL SECURITY;
ALTER TABLE channel_connection FORCE ROW LEVEL SECURITY;

CREATE POLICY channel_connection_isolamento ON channel_connection
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- conversation — módulo conversation
-- ---------------------------------------------------------------------------

CREATE TABLE conversation
(
    id                    uuid        PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    -- Chave estrangeira entre módulos existe no BANCO, e é proposital: a
    -- fronteira do Spring Modulith proíbe dependência de CÓDIGO (injetar
    -- repositório ou referenciar entidade de outro módulo), não integridade
    -- referencial. Abrir mão da FK não tornaria os módulos mais independentes,
    -- só permitiria conversa órfã.
    channel_connection_id uuid        NOT NULL REFERENCES channel_connection (id),
    -- Identificador do interlocutor NO PROVEDOR: chat_id no Telegram, wa_id no
    -- WhatsApp, id de sessão no chat ao vivo. Não é o contato do CRM — o
    -- módulo contact ainda não existe, e forçar a ligação agora inventaria uma
    -- abstração sem o segundo caso concreto.
    external_contact_id   text        NOT NULL,
    -- Denormalizado até o módulo contact existir. Quando existir, vira
    -- referência e esta coluna passa a ser só o que o provedor informou.
    contact_display_name  text,
    status                text        NOT NULL DEFAULT 'OPEN',
    assigned_user_id      uuid        REFERENCES app_user (id),
    -- Ordena a caixa de entrada. Coluna própria em vez de subconsulta em
    -- message: a lista da inbox é a tela mais acessada do produto, e um
    -- MAX(created_at) por conversa a cada carregamento não escala.
    last_message_at       timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz,
    CONSTRAINT conversation_status_valido
        CHECK (status IN ('OPEN', 'PENDING', 'CLOSED'))
);

-- No máximo UMA conversa não encerrada por interlocutor em cada conexão.
--
-- Sem isto, duas mensagens que chegam quase juntas — o caso normal quando
-- alguém manda três mensagens seguidas — criam duas conversas, e o
-- atendimento aparece dividido na tela. Deixar a checagem só no código não
-- resolve: são duas transações concorrentes, e as duas leem "não existe"
-- antes de qualquer uma inserir. Só o banco arbitra isso.
CREATE UNIQUE INDEX conversation_ativa_unica_por_contato
    ON conversation (channel_connection_id, external_contact_id)
    WHERE status <> 'CLOSED' AND deleted_at IS NULL;

-- Lista da caixa de entrada: filtra por status e ordena por atividade.
CREATE INDEX conversation_inbox
    ON conversation (tenant_id, status, last_message_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;

-- "Minhas conversas".
CREATE INDEX conversation_por_atendente
    ON conversation (tenant_id, assigned_user_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER conversation_updated_at
    BEFORE UPDATE ON conversation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE conversation ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation FORCE ROW LEVEL SECURITY;

CREATE POLICY conversation_isolamento ON conversation
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- message — módulo conversation
-- ---------------------------------------------------------------------------

CREATE TABLE message
(
    id                    uuid        PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    conversation_id       uuid        NOT NULL REFERENCES conversation (id),
    -- Repetido aqui, embora derivável pela conversa, porque a constraint de
    -- idempotência precisa dele na própria linha.
    channel_connection_id uuid        NOT NULL REFERENCES channel_connection (id),
    -- Id da mensagem no provedor. NULL enquanto uma mensagem de saída ainda
    -- não foi aceita pelo provedor; preenchido com o retorno do envio, que é o
    -- que permite casar os eventos de entrega e leitura que chegam depois.
    external_id           text,
    direction             text        NOT NULL,
    content_type          text        NOT NULL DEFAULT 'TEXT',
    -- NULL em mensagem só de mídia.
    text_content          text,
    -- Tudo que é específico do provedor vive aqui e NÃO vaza para o domínio.
    -- Serve para diagnóstico: quando algo chega diferente do esperado, o
    -- payload original é a única forma de descobrir o quê.
    payload               jsonb,
    status                text        NOT NULL,
    -- Quem enviou, quando a mensagem parte do atendente. NULL em mensagem
    -- recebida e em mensagem enviada por automação.
    author_user_id        uuid        REFERENCES app_user (id),
    sent_at               timestamptz,
    delivered_at          timestamptz,
    read_at               timestamptz,
    -- Motivo de falha sanitizado. Resposta de provedor pode ecoar o token
    -- enviado, então o que entra aqui já passou por limpeza.
    failure_reason        text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz,
    CONSTRAINT message_direction_valida
        CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT message_content_type_valido
        CHECK (content_type IN ('TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'DOCUMENT', 'LOCATION', 'OTHER')),
    CONSTRAINT message_status_valido
        CHECK (status IN ('RECEIVED', 'PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'))
);

-- A constraint que impede mensagem duplicada na tela.
--
-- Todo provedor reenvia webhook — é o comportamento correto dele quando não
-- recebe 200 rápido o bastante. Sem esta constraint, o reenvio vira uma
-- segunda linha e o atendente vê a mensagem duas vezes. Com ela, a segunda
-- inserção falha e o processamento trata como já visto.
--
-- Parcial porque external_id é NULL até o provedor aceitar o envio, e vários
-- NULL não conflitam num UNIQUE comum — mas ser explícito evita que alguém
-- "conserte" o índice mais tarde sem entender o motivo.
CREATE UNIQUE INDEX message_idempotencia_por_provedor
    ON message (channel_connection_id, external_id)
    WHERE external_id IS NOT NULL;

-- Carregamento do histórico de uma conversa, em ordem cronológica.
CREATE INDEX message_da_conversa
    ON message (tenant_id, conversation_id, created_at)
    WHERE deleted_at IS NULL;

-- Worker da fila de saída: procura o que está pendente de envio.
CREATE INDEX message_pendentes_de_envio
    ON message (tenant_id, status, created_at)
    WHERE direction = 'OUTBOUND' AND status IN ('PENDING', 'FAILED');

CREATE TRIGGER message_updated_at
    BEFORE UPDATE ON message
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE message ENABLE ROW LEVEL SECURITY;
ALTER TABLE message FORCE ROW LEVEL SECURITY;

CREATE POLICY message_isolamento ON message
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- Resolução de tenant na entrada de mensagem
-- ---------------------------------------------------------------------------

-- Terceira e — pela previsão de hoje — última função SECURITY DEFINER do
-- projeto. Registrada em 03-decisoes.md, como a regra exige.
--
-- Motivo: a entrada de mensagem não tem sessão. O webhook do Telegram é
-- chamado pelo provedor, e o visitante do chat ao vivo não é usuário do CRM.
-- Nos dois casos o único dado disponível é a conexão de canal, que está sob
-- RLS — um SELECT normal devolveria vazio e nenhuma mensagem entraria.
--
-- A brecha é estreita de propósito: recebe um uuid de conexão e devolve um
-- uuid de tenant. Quem chamar com uuid aleatório não descobre nada além de
-- "existe ou não existe" — e uuid v7 não é enumerável na prática.
--
-- A autenticidade da requisição continua vindo da assinatura do provedor,
-- nunca desta função. Ela resolve ROTEAMENTO, não autorização.
CREATE FUNCTION resolve_tenant_id_por_channel_connection(p_connection_id uuid) RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
SELECT tenant_id
FROM channel_connection
WHERE id = p_connection_id
  AND active
  AND deleted_at IS NULL
$$;

COMMENT ON FUNCTION resolve_tenant_id_por_channel_connection(uuid) IS
    'Resolve o tenant de uma conexão de canal para que o RLS possa ser aplicado '
        'na ingestão de mensagem, que ocorre sem sessão. Roteamento, não autorização.';
