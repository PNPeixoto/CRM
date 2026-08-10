-- Trilha de auditoria de negocio. Nao e log operacional e nao armazena
-- payload, mensagem, arquivo, token, cookie, cabecalho ou segredo.

CREATE TABLE audit_event
(
    id                  uuid PRIMARY KEY,
    tenant_id           uuid        NOT NULL REFERENCES tenant (id),
    occurred_at         timestamptz NOT NULL,
    actor_type          text        NOT NULL,
    actor_id            uuid,
    scope_type          text        NOT NULL,
    scope_id            uuid        NOT NULL,
    action_code         text        NOT NULL,
    target_type         text        NOT NULL,
    target_id           uuid,
    outcome             text        NOT NULL,
    reason_code         text        NOT NULL,
    correlation_id      uuid        NOT NULL,
    retention_category  text        NOT NULL,
    integrity_version   smallint    NOT NULL DEFAULT 1,
    integrity_hash      text        NOT NULL,
    CONSTRAINT audit_event_actor_valido CHECK (
        (actor_type = 'HUMAN' AND actor_id IS NOT NULL)
        OR (actor_type = 'SYSTEM' AND actor_id IS NULL)
    ),
    CONSTRAINT audit_event_scope_valido
        CHECK (scope_type IN ('TENANT', 'UNIT', 'OWN', 'SYSTEM')),
    CONSTRAINT audit_event_action_valida CHECK (action_code IN (
        'AUTHORIZATION_DENIED_V1',
        'AUDIT_READ_V1',
        'EXPORT_REQUESTED_V1',
        'EXPORT_COMPLETED_V1',
        'CREDENTIAL_CHANGED_V1',
        'ROLE_CHANGED_V1',
        'SENSITIVE_CONFIGURATION_CHANGED_V1'
    )),
    CONSTRAINT audit_event_target_valido
        CHECK (target_type ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT audit_event_outcome_valido
        CHECK (outcome IN ('SUCCEEDED', 'DENIED', 'FAILED')),
    CONSTRAINT audit_event_reason_valido
        CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT audit_event_retention_valida
        CHECK (retention_category IN ('ACCESS', 'SECURITY', 'CONFIGURATION', 'EXPORT')),
    CONSTRAINT audit_event_integrity_version_valida CHECK (integrity_version = 1),
    CONSTRAINT audit_event_integrity_hash_valido
        CHECK (integrity_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT audit_event_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE INDEX audit_event_historico
    ON audit_event (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX audit_event_por_acao
    ON audit_event (tenant_id, action_code, occurred_at DESC, id DESC);
CREATE INDEX audit_event_por_resultado
    ON audit_event (tenant_id, outcome, occurred_at DESC, id DESC);

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_event_isolamento ON audit_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- A aplicacao pode consultar e acrescentar eventos, mas nunca reescrever o
-- passado. O gatilho tambem protege acessos administrativos comuns. O modo de
-- retencao e somente um ponto de extensao para o Prompt 18: ele ainda nao
-- concede DELETE a nenhum papel nem implementa prazo de descarte.
CREATE FUNCTION proteger_audit_event() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('app.audit_retention_mode', true) = 'controlled' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit_event e append-only';
END;
$$;

CREATE TRIGGER audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION proteger_audit_event();

REVOKE UPDATE, DELETE ON audit_event FROM "${runtime_role}";
REVOKE EXECUTE ON FUNCTION proteger_audit_event() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION proteger_audit_event() FROM "${runtime_role}";

COMMENT ON TABLE audit_event IS
    'Trilha append-only de metadados; separada de logs e sem conteudo sensivel.';
COMMENT ON COLUMN audit_event.integrity_hash IS
    'SHA-256 canonico dos metadados da linha; detecta alteracao, nao promete imutabilidade criptografica.';
COMMENT ON COLUMN audit_event.retention_category IS
    'Categoria para a politica de retencao e descarte controlado definida no Prompt 18.';

