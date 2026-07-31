-- Núcleo do CRM: contato, funil, oportunidade e tarefa.
--
-- Mesmas convenções das anteriores: UUID v7 na aplicação, TIMESTAMPTZ em UTC,
-- exclusão lógica, RLS ENABLE + FORCE em toda tabela com tenant_id, índice
-- composto sempre começando por tenant_id.

-- ---------------------------------------------------------------------------
-- contact
-- ---------------------------------------------------------------------------

CREATE TABLE contact
(
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES tenant (id),
    name           text        NOT NULL,
    email          text,
    phone          text,
    company_name   text,
    notes          text,
    owner_user_id  uuid        REFERENCES app_user (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    deleted_at     timestamptz
);

-- NOTA DELIBERADA: não existe coluna de CPF/CNPJ aqui. O
-- 01-padroes-tecnicos.md exige documento cifrado em repouso, fora de listagem
-- e com acesso registrado em auditoria. Criar a coluna antes disso convidaria
-- a gravar dado pessoal sensível em claro — o mesmo raciocínio que adiou a
-- coluna de credencial na V2.

-- Duplicidade por e-mail dentro do tenant. LOWER porque endereço de e-mail é
-- insensível a maiúsculas na prática, e sem isso "Joao@" e "joao@" viram dois
-- contatos que ninguém consegue conciliar depois.
CREATE UNIQUE INDEX contact_email_unico_por_tenant
    ON contact (tenant_id, lower(email))
    WHERE email IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX contact_listagem ON contact (tenant_id, name) WHERE deleted_at IS NULL;
CREATE INDEX contact_por_responsavel ON contact (tenant_id, owner_user_id) WHERE deleted_at IS NULL;

CREATE TRIGGER contact_updated_at BEFORE UPDATE ON contact
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE contact FORCE ROW LEVEL SECURITY;
CREATE POLICY contact_isolamento ON contact
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- pipeline e pipeline_stage
-- ---------------------------------------------------------------------------
--
-- Funil e etapas são configuráveis por tenant desde já. Fixar as etapas em
-- enum pareceria mais simples agora e seria retrabalho na primeira
-- franqueadora que quisesse um funil próprio — o que é o caso de uso central
-- do produto, não uma exceção.

CREATE TABLE pipeline
(
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenant (id),
    name       text        NOT NULL,
    is_default boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted_at timestamptz
);

-- No máximo um funil padrão por tenant. Dois padrões fariam a escolha
-- depender da ordem de retorno da consulta, que não é determinística.
CREATE UNIQUE INDEX pipeline_padrao_unico
    ON pipeline (tenant_id) WHERE is_default AND deleted_at IS NULL;

CREATE INDEX pipeline_por_tenant ON pipeline (tenant_id) WHERE deleted_at IS NULL;

CREATE TRIGGER pipeline_updated_at BEFORE UPDATE ON pipeline
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE pipeline ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline FORCE ROW LEVEL SECURITY;
CREATE POLICY pipeline_isolamento ON pipeline
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE pipeline_stage
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenant (id),
    pipeline_id uuid        NOT NULL REFERENCES pipeline (id),
    name        text        NOT NULL,
    -- Ordem de exibição no kanban. Inteiro esparso (10, 20, 30) para permitir
    -- inserir entre duas etapas sem renumerar todas.
    position    integer     NOT NULL,
    -- Etapas terminais. Separadas em duas colunas e não em um enum de tipo
    -- porque um funil pode ter várias etapas de perda com motivos distintos.
    is_won      boolean     NOT NULL DEFAULT false,
    is_lost     boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT pipeline_stage_terminal_coerente CHECK (NOT (is_won AND is_lost))
);

CREATE INDEX pipeline_stage_do_funil
    ON pipeline_stage (tenant_id, pipeline_id, position) WHERE deleted_at IS NULL;

CREATE TRIGGER pipeline_stage_updated_at BEFORE UPDATE ON pipeline_stage
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE pipeline_stage ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_stage FORCE ROW LEVEL SECURITY;
CREATE POLICY pipeline_stage_isolamento ON pipeline_stage
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- deal
-- ---------------------------------------------------------------------------

CREATE TABLE deal
(
    id                  uuid PRIMARY KEY,
    tenant_id           uuid        NOT NULL REFERENCES tenant (id),
    pipeline_id         uuid        NOT NULL REFERENCES pipeline (id),
    stage_id            uuid        NOT NULL REFERENCES pipeline_stage (id),
    contact_id          uuid        REFERENCES contact (id),
    title               text        NOT NULL,
    -- Dinheiro em CENTAVOS, como inteiro. Nunca float: 0.1 + 0.2 não dá 0.3
    -- em ponto flutuante binário, e num relatório de vendas o erro acumula
    -- linha a linha até virar divergência que ninguém explica.
    value_cents         bigint      NOT NULL DEFAULT 0,
    status              text        NOT NULL DEFAULT 'OPEN',
    expected_close_date date,
    closed_at           timestamptz,
    lost_reason         text,
    owner_user_id       uuid        REFERENCES app_user (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    deleted_at          timestamptz,
    CONSTRAINT deal_status_valido CHECK (status IN ('OPEN', 'WON', 'LOST')),
    CONSTRAINT deal_valor_nao_negativo CHECK (value_cents >= 0)
);

-- Kanban: carrega as oportunidades de um funil agrupadas por etapa.
CREATE INDEX deal_do_funil
    ON deal (tenant_id, pipeline_id, stage_id) WHERE deleted_at IS NULL;

CREATE INDEX deal_por_responsavel
    ON deal (tenant_id, owner_user_id, status) WHERE deleted_at IS NULL;

CREATE INDEX deal_do_contato
    ON deal (tenant_id, contact_id) WHERE deleted_at IS NULL;

CREATE TRIGGER deal_updated_at BEFORE UPDATE ON deal
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE deal ENABLE ROW LEVEL SECURITY;
ALTER TABLE deal FORCE ROW LEVEL SECURITY;
CREATE POLICY deal_isolamento ON deal
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- task
-- ---------------------------------------------------------------------------

CREATE TABLE task
(
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    title            text        NOT NULL,
    description      text,
    -- Vínculos opcionais. Uma tarefa pode ser solta ("ligar para o
    -- fornecedor"), presa a um contato ou a uma oportunidade.
    contact_id       uuid        REFERENCES contact (id),
    deal_id          uuid        REFERENCES deal (id),
    assigned_user_id uuid        REFERENCES app_user (id),
    due_at           timestamptz,
    done_at          timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    deleted_at       timestamptz
);

-- "Minhas tarefas em aberto, por vencimento". O índice parcial exclui as
-- concluídas: elas são a maioria com o tempo e nunca aparecem nessa consulta.
CREATE INDEX task_abertas_por_responsavel
    ON task (tenant_id, assigned_user_id, due_at)
    WHERE done_at IS NULL AND deleted_at IS NULL;

CREATE INDEX task_do_contato ON task (tenant_id, contact_id) WHERE deleted_at IS NULL;
CREATE INDEX task_da_oportunidade ON task (tenant_id, deal_id) WHERE deleted_at IS NULL;

CREATE TRIGGER task_updated_at BEFORE UPDATE ON task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE task ENABLE ROW LEVEL SECURITY;
ALTER TABLE task FORCE ROW LEVEL SECURITY;
CREATE POLICY task_isolamento ON task
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- Ligação conversa ↔ contato
-- ---------------------------------------------------------------------------
--
-- A V2 registrou que não criava esta coluna porque o módulo contact não
-- existia e forçar a ligação inventaria abstração sem caso concreto. Agora
-- existe. Fica NULL até haver regra de identificação — casar telefone do
-- WhatsApp com contato é trabalho de deduplicação, não de migration.

ALTER TABLE conversation
    ADD COLUMN contact_id uuid REFERENCES contact (id);

CREATE INDEX conversation_do_contato
    ON conversation (tenant_id, contact_id) WHERE deleted_at IS NULL;
