-- Presets distintos tornam diferenças de apresentação e vazamentos visíveis
-- durante o desenvolvimento. Idempotente para bancos onde o seed já rodou.

SET LOCAL app.tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42';
INSERT INTO tenant_profile
    (tenant_id, business_segment, preset_version, onboarding_completed)
VALUES
    ('019fa91c-0f63-75f7-b4a0-1494c1304c42', 'GENERAL_SERVICES', 1, true)
ON CONFLICT (tenant_id) DO NOTHING;

SET LOCAL app.tenant_id = '019fa91c-0f66-7d34-9a39-2f79328d02c9';
INSERT INTO tenant_profile
    (tenant_id, business_segment, preset_version, onboarding_completed)
VALUES
    ('019fa91c-0f66-7d34-9a39-2f79328d02c9', 'CONFECTIONERY', 1, true)
ON CONFLICT (tenant_id) DO NOTHING;
