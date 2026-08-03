-- Modelo organizacional tenant-scoped.
--
-- A identidade interna continua em app_user. Membership define vigência do
-- vínculo e membership_scope atribui papel em toda a empresa, em uma unidade
-- ou sobre registros próprios. NETWORK exige identidade de plataforma e TEAM
-- exige um agregado de equipe; ambos ficam no contrato conceitual, não como
-- strings polimórficas sem FK.

ALTER TABLE contact
    ADD COLUMN contact_kind text NOT NULL DEFAULT 'PERSON',
    ADD CONSTRAINT contact_kind_valido
        CHECK (contact_kind IN ('PERSON', 'ORGANIZATION'));

CREATE TABLE organizational_unit
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenant (id),
    code        text        NOT NULL,
    name        text        NOT NULL,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT organizational_unit_code_formato
        CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$'),
    CONSTRAINT organizational_unit_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX organizational_unit_code_unico
    ON organizational_unit (tenant_id, lower(code)) WHERE deleted_at IS NULL;
CREATE INDEX organizational_unit_ativas
    ON organizational_unit (tenant_id, name) WHERE active AND deleted_at IS NULL;

CREATE TRIGGER organizational_unit_updated_at
    BEFORE UPDATE ON organizational_unit
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE organizational_unit ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizational_unit FORCE ROW LEVEL SECURITY;
CREATE POLICY organizational_unit_isolamento ON organizational_unit
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE organization_membership
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenant (id),
    user_id     uuid        NOT NULL,
    status      text        NOT NULL DEFAULT 'ACTIVE',
    valid_from  timestamptz NOT NULL DEFAULT now(),
    valid_until timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT organization_membership_status_valido
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT organization_membership_vigencia_valida
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT organization_membership_tenant_id_unico UNIQUE (tenant_id, id),
    CONSTRAINT organization_membership_usuario_mesmo_tenant
        FOREIGN KEY (tenant_id, user_id) REFERENCES app_user (tenant_id, id)
);

CREATE UNIQUE INDEX organization_membership_usuario_unico
    ON organization_membership (tenant_id, user_id) WHERE deleted_at IS NULL;
CREATE INDEX organization_membership_vigentes
    ON organization_membership (tenant_id, user_id, status, valid_until)
    WHERE deleted_at IS NULL;

CREATE TRIGGER organization_membership_updated_at
    BEFORE UPDATE ON organization_membership
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE organization_membership ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_membership FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_membership_isolamento ON organization_membership
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE app_role
(
    id          uuid PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenant (id),
    code        text        NOT NULL,
    name        text        NOT NULL,
    description text,
    system_role boolean     NOT NULL DEFAULT false,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT app_role_code_formato CHECK (code ~ '^[A-Z][A-Z0-9_]{1,48}$'),
    CONSTRAINT app_role_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX app_role_code_unico
    ON app_role (tenant_id, code) WHERE deleted_at IS NULL;

CREATE TRIGGER app_role_updated_at
    BEFORE UPDATE ON app_role
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE app_role ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_role FORCE ROW LEVEL SECURITY;
CREATE POLICY app_role_isolamento ON app_role
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE role_permission
(
    tenant_id      uuid        NOT NULL REFERENCES tenant (id),
    role_id        uuid        NOT NULL,
    permission_code text       NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    PRIMARY KEY (tenant_id, role_id, permission_code),
    CONSTRAINT role_permission_codigo_formato
        CHECK (permission_code ~ '^[a-z][a-z0-9_.:-]{2,100}$'),
    CONSTRAINT role_permission_papel_mesmo_tenant
        FOREIGN KEY (tenant_id, role_id) REFERENCES app_role (tenant_id, id)
);

ALTER TABLE role_permission ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permission FORCE ROW LEVEL SECURITY;
CREATE POLICY role_permission_isolamento ON role_permission
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE TABLE membership_scope
(
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    membership_id uuid        NOT NULL,
    role_id       uuid        NOT NULL,
    scope_type    text        NOT NULL,
    unit_id       uuid,
    status        text        NOT NULL DEFAULT 'ACTIVE',
    valid_from    timestamptz NOT NULL DEFAULT now(),
    valid_until   timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    deleted_at    timestamptz,
    CONSTRAINT membership_scope_tipo_valido
        CHECK (scope_type IN ('TENANT', 'UNIT', 'OWN')),
    CONSTRAINT membership_scope_unidade_coerente CHECK (
        (scope_type = 'UNIT' AND unit_id IS NOT NULL)
        OR (scope_type IN ('TENANT', 'OWN') AND unit_id IS NULL)),
    CONSTRAINT membership_scope_status_valido
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT membership_scope_vigencia_valida
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT membership_scope_membership_mesmo_tenant
        FOREIGN KEY (tenant_id, membership_id)
        REFERENCES organization_membership (tenant_id, id),
    CONSTRAINT membership_scope_papel_mesmo_tenant
        FOREIGN KEY (tenant_id, role_id) REFERENCES app_role (tenant_id, id),
    CONSTRAINT membership_scope_unidade_mesmo_tenant
        FOREIGN KEY (tenant_id, unit_id) REFERENCES organizational_unit (tenant_id, id)
);

CREATE UNIQUE INDEX membership_scope_sem_unidade_unico
    ON membership_scope (tenant_id, membership_id, role_id, scope_type)
    WHERE unit_id IS NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX membership_scope_unidade_unico
    ON membership_scope (tenant_id, membership_id, role_id, scope_type, unit_id)
    WHERE unit_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX membership_scope_vigentes
    ON membership_scope (tenant_id, membership_id, status, valid_until)
    WHERE deleted_at IS NULL;

CREATE TRIGGER membership_scope_updated_at
    BEFORE UPDATE ON membership_scope
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE membership_scope ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_scope FORCE ROW LEVEL SECURITY;
CREATE POLICY membership_scope_isolamento ON membership_scope
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
