-- Sessões com limite absoluto/inatividade, recuperação de senha e MFA TOTP.
-- Todos os segredos aleatórios são persistidos somente como hash; o segredo
-- TOTP é a exceção necessária, cifrado com chave externa e versionada.

ALTER TABLE refresh_token
    ADD COLUMN session_started_at timestamptz,
    ADD COLUMN mfa_verified boolean NOT NULL DEFAULT false;

UPDATE refresh_token
   SET session_started_at = issued_at
 WHERE session_started_at IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN session_started_at SET NOT NULL;

CREATE INDEX refresh_token_sessao
    ON refresh_token (tenant_id, user_id, family_id, session_started_at);

CREATE TABLE password_reset_token
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenant (id),
    user_id     uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    CONSTRAINT password_reset_usuario_mesmo_tenant
        FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id)
);

CREATE UNIQUE INDEX password_reset_hash_unico
    ON password_reset_token (token_hash);
CREATE INDEX password_reset_usuario_ativo
    ON password_reset_token (tenant_id, user_id, expires_at)
    WHERE used_at IS NULL;

ALTER TABLE password_reset_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_token FORCE ROW LEVEL SECURITY;
CREATE POLICY password_reset_token_isolamento ON password_reset_token
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE mfa_authenticator
(
    id                  uuid PRIMARY KEY,
    tenant_id           uuid        NOT NULL REFERENCES tenant (id),
    user_id             uuid        NOT NULL,
    secret_ciphertext   text        NOT NULL,
    key_version         integer     NOT NULL,
    activated_at        timestamptz NOT NULL,
    last_used_at        timestamptz,
    last_used_step      bigint,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    CONSTRAINT mfa_authenticator_usuario_mesmo_tenant
        FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id),
    CONSTRAINT mfa_authenticator_chave_positiva CHECK (key_version > 0),
    CONSTRAINT mfa_authenticator_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX mfa_authenticator_usuario_unico
    ON mfa_authenticator (tenant_id, user_id) WHERE deleted_at IS NULL;

CREATE TRIGGER mfa_authenticator_updated_at
    BEFORE UPDATE ON mfa_authenticator
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE mfa_authenticator ENABLE ROW LEVEL SECURITY;
ALTER TABLE mfa_authenticator FORCE ROW LEVEL SECURITY;
CREATE POLICY mfa_authenticator_isolamento ON mfa_authenticator
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE mfa_recovery_code
(
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES tenant (id),
    authenticator_id uuid      NOT NULL,
    code_hash      varchar(64) NOT NULL,
    used_at        timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mfa_recovery_authenticator_mesmo_tenant
        FOREIGN KEY (tenant_id, authenticator_id)
        REFERENCES mfa_authenticator (tenant_id, id)
);

CREATE UNIQUE INDEX mfa_recovery_hash_unico
    ON mfa_recovery_code (code_hash);
CREATE INDEX mfa_recovery_disponivel
    ON mfa_recovery_code (tenant_id, authenticator_id) WHERE used_at IS NULL;

ALTER TABLE mfa_recovery_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE mfa_recovery_code FORCE ROW LEVEL SECURITY;
CREATE POLICY mfa_recovery_code_isolamento ON mfa_recovery_code
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE mfa_enrollment
(
    id                uuid PRIMARY KEY,
    tenant_id         uuid        NOT NULL REFERENCES tenant (id),
    user_id           uuid        NOT NULL,
    challenge_hash    varchar(64) NOT NULL,
    secret_ciphertext text        NOT NULL,
    key_version       integer     NOT NULL,
    expires_at        timestamptz NOT NULL,
    used_at           timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mfa_enrollment_usuario_mesmo_tenant
        FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id),
    CONSTRAINT mfa_enrollment_chave_positiva CHECK (key_version > 0)
);

CREATE UNIQUE INDEX mfa_enrollment_desafio_unico
    ON mfa_enrollment (challenge_hash);
CREATE INDEX mfa_enrollment_usuario_ativo
    ON mfa_enrollment (tenant_id, user_id, expires_at) WHERE used_at IS NULL;

ALTER TABLE mfa_enrollment ENABLE ROW LEVEL SECURITY;
ALTER TABLE mfa_enrollment FORCE ROW LEVEL SECURITY;
CREATE POLICY mfa_enrollment_isolamento ON mfa_enrollment
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- Resoluções mínimas anteriores ao contexto de tenant. A entrada é sempre o
-- hash de um segredo aleatório de 256 bits; nenhum dado de conta é enumerado.
CREATE FUNCTION resolve_tenant_id_por_password_reset_hash(p_token_hash text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
SELECT tenant_id FROM password_reset_token WHERE token_hash = p_token_hash
$$;

CREATE FUNCTION resolve_tenant_id_por_mfa_enrollment_hash(p_challenge_hash text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
SELECT tenant_id FROM mfa_enrollment WHERE challenge_hash = p_challenge_hash
$$;

REVOKE EXECUTE ON FUNCTION resolve_tenant_id_por_password_reset_hash(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION resolve_tenant_id_por_mfa_enrollment_hash(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_tenant_id_por_password_reset_hash(text)
    TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION resolve_tenant_id_por_mfa_enrollment_hash(text)
    TO "${runtime_role}";

COMMENT ON FUNCTION resolve_tenant_id_por_password_reset_hash(text) IS
    'Resolve somente o tenant associado a um segredo de recuperação de alta entropia.';
COMMENT ON FUNCTION resolve_tenant_id_por_mfa_enrollment_hash(text) IS
    'Resolve somente o tenant associado a um desafio de cadastro MFA de alta entropia.';
