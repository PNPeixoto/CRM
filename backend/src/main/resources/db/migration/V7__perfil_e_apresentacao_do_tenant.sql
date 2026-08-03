-- Perfil inicial e preset de apresentação do tenant.
--
-- A solicitação original chamava esta migration de V6. O repositório real já
-- contém V6__registro_de_eventos.sql; reutilizar o número faria o Flyway
-- recusar a aplicação ou divergir entre ambientes. Por isso ela é V7.

CREATE TABLE tenant_profile
(
    tenant_id             uuid PRIMARY KEY REFERENCES tenant (id),
    business_segment      text        NOT NULL,
    preset_version        integer     NOT NULL,
    onboarding_completed  boolean     NOT NULL DEFAULT false,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    CONSTRAINT tenant_profile_segmento_valido CHECK (business_segment IN
        ('GENERAL_SERVICES', 'RESTAURANT', 'CONFECTIONERY', 'RENTAL')),
    CONSTRAINT tenant_profile_versao_positiva CHECK (preset_version > 0)
);

CREATE TRIGGER tenant_profile_updated_at
    BEFORE UPDATE ON tenant_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE tenant_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_profile FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_profile_isolamento ON tenant_profile
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
