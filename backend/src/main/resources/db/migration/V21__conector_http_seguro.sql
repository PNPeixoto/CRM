-- Conectores HTTP aprovados, segredos cifrados e diagnósticos sanitizados.
-- O executor nunca persiste request, response, token ou URL renderizada.

CREATE TABLE http_connector
(
    id                    uuid PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    code                  text        NOT NULL,
    name                  text        NOT NULL,
    status                text        NOT NULL DEFAULT 'PAUSED',
    http_method           text        NOT NULL,
    origin                text        NOT NULL,
    path_template         text        NOT NULL,
    body_template         text,
    content_type          text        NOT NULL DEFAULT 'application/json',
    idempotency_header    text,
    timeout_ms            integer     NOT NULL,
    max_response_bytes    integer     NOT NULL,
    requests_per_minute   integer     NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz,
    CONSTRAINT http_connector_code_formato
        CHECK (code ~ '^[a-z][a-z0-9_-]{2,79}$'),
    CONSTRAINT http_connector_nome_valido
        CHECK (char_length(name) BETWEEN 1 AND 200),
    CONSTRAINT http_connector_status_valido
        CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT http_connector_metodo_valido
        CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH')),
    CONSTRAINT http_connector_origin_valida
        CHECK (char_length(origin) BETWEEN 8 AND 500),
    CONSTRAINT http_connector_path_valido
        CHECK (char_length(path_template) BETWEEN 1 AND 2048),
    CONSTRAINT http_connector_body_valido
        CHECK (body_template IS NULL OR char_length(body_template) <= 65536),
    CONSTRAINT http_connector_content_type_valido
        CHECK (content_type = 'application/json'),
    CONSTRAINT http_connector_idempotencia_coerente CHECK (
        (http_method = 'GET' AND idempotency_header IS NULL)
        OR (http_method IN ('POST', 'PUT', 'PATCH') AND idempotency_header IS NOT NULL)
    ),
    CONSTRAINT http_connector_timeout_valido CHECK (timeout_ms BETWEEN 100 AND 30000),
    CONSTRAINT http_connector_resposta_valida
        CHECK (max_response_bytes BETWEEN 1024 AND 1048576),
    CONSTRAINT http_connector_orcamento_valido
        CHECK (requests_per_minute BETWEEN 1 AND 1000),
    CONSTRAINT http_connector_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX http_connector_code_unico
    ON http_connector (tenant_id, code) WHERE deleted_at IS NULL;
CREATE INDEX http_connector_por_status
    ON http_connector (tenant_id, status, id) WHERE deleted_at IS NULL;

CREATE TRIGGER http_connector_updated_at
    BEFORE UPDATE ON http_connector
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE http_connector_secret
(
    id                 uuid PRIMARY KEY,
    tenant_id          uuid        NOT NULL REFERENCES tenant (id),
    connector_id       uuid        NOT NULL,
    auth_type          text        NOT NULL,
    header_name        text,
    secret_ciphertext  text        NOT NULL,
    key_version        integer     NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    updated_by         uuid,
    CONSTRAINT http_connector_secret_auth_valida
        CHECK (auth_type IN ('BEARER', 'HEADER')),
    CONSTRAINT http_connector_secret_header_coerente CHECK (
        (auth_type = 'BEARER' AND header_name IS NULL)
        OR (auth_type = 'HEADER' AND header_name IS NOT NULL)
    ),
    CONSTRAINT http_connector_secret_chave_valida CHECK (key_version > 0),
    CONSTRAINT http_connector_secret_mesmo_tenant
        FOREIGN KEY (tenant_id, connector_id)
        REFERENCES http_connector (tenant_id, id),
    CONSTRAINT http_connector_secret_unico UNIQUE (tenant_id, connector_id),
    CONSTRAINT http_connector_secret_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE TRIGGER http_connector_secret_updated_at
    BEFORE UPDATE ON http_connector_secret
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE http_connector_attempt
(
    id                       uuid PRIMARY KEY,
    tenant_id                uuid        NOT NULL REFERENCES tenant (id),
    connector_id             uuid        NOT NULL,
    automation_execution_id  uuid        NOT NULL,
    step_key                 text        NOT NULL,
    idempotency_key_hash     text        NOT NULL,
    status                   text        NOT NULL DEFAULT 'STARTED',
    http_status              integer,
    failure_code             text,
    response_bytes           integer,
    duration_ms              bigint,
    target_hash              text        NOT NULL,
    started_at               timestamptz NOT NULL DEFAULT now(),
    completed_at             timestamptz,
    CONSTRAINT http_connector_attempt_step_valido
        CHECK (step_key ~ '^[a-z][a-z0-9_-]{0,79}$'),
    CONSTRAINT http_connector_attempt_hash_valido
        CHECK (idempotency_key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT http_connector_attempt_target_hash_valido
        CHECK (target_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT http_connector_attempt_status_valido CHECK (status IN (
        'STARTED', 'SUCCEEDED', 'TRANSIENT_FAILURE', 'PERMANENT_FAILURE'
    )),
    CONSTRAINT http_connector_attempt_failure_valida
        CHECK (failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT http_connector_attempt_http_status_valido
        CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT http_connector_attempt_response_bytes_valido
        CHECK (response_bytes IS NULL OR response_bytes >= 0),
    CONSTRAINT http_connector_attempt_duration_valida
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT http_connector_attempt_conector_mesmo_tenant
        FOREIGN KEY (tenant_id, connector_id)
        REFERENCES http_connector (tenant_id, id),
    CONSTRAINT http_connector_attempt_execucao_mesmo_tenant
        FOREIGN KEY (tenant_id, automation_execution_id)
        REFERENCES automation_execution (tenant_id, id),
    CONSTRAINT http_connector_attempt_unico
        UNIQUE (tenant_id, automation_execution_id, step_key)
);

CREATE INDEX http_connector_attempt_historico
    ON http_connector_attempt (tenant_id, started_at DESC, id DESC);

ALTER TABLE http_connector ENABLE ROW LEVEL SECURITY;
ALTER TABLE http_connector FORCE ROW LEVEL SECURITY;
CREATE POLICY http_connector_isolamento ON http_connector
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE http_connector_secret ENABLE ROW LEVEL SECURITY;
ALTER TABLE http_connector_secret FORCE ROW LEVEL SECURITY;
CREATE POLICY http_connector_secret_isolamento ON http_connector_secret
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE http_connector_attempt ENABLE ROW LEVEL SECURITY;
ALTER TABLE http_connector_attempt FORCE ROW LEVEL SECURITY;
CREATE POLICY http_connector_attempt_isolamento ON http_connector_attempt
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

COMMENT ON TABLE http_connector IS
    'Destino aprovado e limitado; origin nao aceita template e redirect e desativado.';
COMMENT ON TABLE http_connector_secret IS
    'Segredo AES-GCM; nunca retornado por API nem interpolado em diagnostico.';
COMMENT ON TABLE http_connector_attempt IS
    'Diagnostico sanitizado; nao contem request, response, segredo ou URL renderizada.';
