-- Contextos organizacionais fictícios do profile dev. Repeatable para também
-- alcançar volumes onde V900 já havia sido aplicada antes da V10.

SET LOCAL app.tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42';

INSERT INTO organizational_unit (id, tenant_id, code, name)
VALUES ('019fa91c-1000-7000-8000-000000000001',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42', 'matriz', 'Unidade Matriz')
ON CONFLICT DO NOTHING;
INSERT INTO organization_membership (id, tenant_id, user_id, valid_from)
VALUES ('019fa91c-1000-7000-8000-000000000002',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        '019fa91c-0f66-7a68-8384-20e85b6c8f5a', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO app_role (id, tenant_id, code, name, system_role)
VALUES ('019fa91c-1000-7000-8000-000000000003',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42', 'OWNER', 'Proprietário', true)
ON CONFLICT DO NOTHING;
INSERT INTO role_permission (tenant_id, role_id, permission_code)
SELECT '019fa91c-0f63-75f7-b4a0-1494c1304c42',
       '019fa91c-1000-7000-8000-000000000003', permission
FROM unnest(ARRAY[
    'dashboard.read', 'contacts.read', 'contacts.write', 'deals.read', 'deals.write',
    'tasks.read', 'tasks.write', 'conversations.read', 'conversations.write',
      'channels.read', 'channels.write', 'automations.read', 'automations.write',
      'integrations.read', 'integrations.write', 'reports.read', 'audit.read',
    'organization.manage'
]) AS permission
ON CONFLICT DO NOTHING;
INSERT INTO membership_scope
    (id, tenant_id, membership_id, role_id, scope_type, valid_from)
VALUES ('019fa91c-1000-7000-8000-000000000004',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        '019fa91c-1000-7000-8000-000000000002',
        '019fa91c-1000-7000-8000-000000000003', 'TENANT', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;

SET LOCAL app.tenant_id = '019fa91c-0f66-7d34-9a39-2f79328d02c9';

INSERT INTO organizational_unit (id, tenant_id, code, name)
VALUES ('019fa91c-2000-7000-8000-000000000001',
        '019fa91c-0f66-7d34-9a39-2f79328d02c9', 'matriz', 'Unidade Matriz')
ON CONFLICT DO NOTHING;
INSERT INTO organization_membership (id, tenant_id, user_id, valid_from)
VALUES ('019fa91c-2000-7000-8000-000000000002',
        '019fa91c-0f66-7d34-9a39-2f79328d02c9',
        '019fa91c-0f67-74ec-8e9c-1be4dbf398a7', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;
INSERT INTO app_role (id, tenant_id, code, name, system_role)
VALUES ('019fa91c-2000-7000-8000-000000000003',
        '019fa91c-0f66-7d34-9a39-2f79328d02c9', 'OWNER', 'Proprietário', true)
ON CONFLICT DO NOTHING;
INSERT INTO role_permission (tenant_id, role_id, permission_code)
SELECT '019fa91c-0f66-7d34-9a39-2f79328d02c9',
       '019fa91c-2000-7000-8000-000000000003', permission
FROM unnest(ARRAY[
    'dashboard.read', 'contacts.read', 'contacts.write', 'deals.read', 'deals.write',
    'tasks.read', 'tasks.write', 'conversations.read', 'conversations.write',
      'channels.read', 'channels.write', 'automations.read', 'automations.write',
      'integrations.read', 'integrations.write', 'reports.read', 'audit.read',
    'organization.manage'
]) AS permission
ON CONFLICT DO NOTHING;
INSERT INTO membership_scope
    (id, tenant_id, membership_id, role_id, scope_type, valid_from)
VALUES ('019fa91c-2000-7000-8000-000000000004',
        '019fa91c-0f66-7d34-9a39-2f79328d02c9',
        '019fa91c-2000-7000-8000-000000000002',
        '019fa91c-2000-7000-8000-000000000003', 'TENANT', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Atendente com carteira própria e operação compartilhada, no tenant `pnp`
-- ---------------------------------------------------------------------------
--
-- Existe para que haja um caso negativo real em desenvolvimento e nos testes.
-- Sem um papel de alcance restrito, toda verificação de autorização passaria
-- por construção — e uma regra que nunca é exercida no caminho negativo é uma
-- regra que ninguém sabe se funciona.
--
-- Ele lê e escreve contatos e tarefas sob sua responsabilidade. Funil e
-- conversas são recursos coletivos e vêm de um segundo papel com alcance
-- TENANT; colocá-los neste papel OWN faria usuários da mesma empresa enxergarem
-- quadros diferentes e esconderia toda oportunidade ainda sem responsável.
-- Não recebe `reports.read`: o consolidado soma registros de todo o tenant.

SET LOCAL app.tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42';

INSERT INTO app_user (id, tenant_id, login, email, full_name, password_hash)
VALUES ('019fa91c-3000-7000-8000-000000000001',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        'atendente',
        'atendente@pnp.exemplo.com.br',
        'Ana Atendente',
        -- TESTE — mesmo hash Argon2id do usuário de desenvolvimento.
        '$argon2id$v=19$m=16384,t=2,p=1$3nUgapjwXyOihLKkHEykhQ$+y6IgEuBhsJemmGjKglEZv3MQvbtcuGvu9Tln3JwzaQ')
ON CONFLICT DO NOTHING;

INSERT INTO organization_membership (id, tenant_id, user_id, valid_from)
VALUES ('019fa91c-3000-7000-8000-000000000002',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        '019fa91c-3000-7000-8000-000000000001', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO app_role (id, tenant_id, code, name, system_role)
VALUES ('019fa91c-3000-7000-8000-000000000003',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42', 'ATTENDANT', 'Atendente', false)
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (tenant_id, role_id, permission_code)
SELECT '019fa91c-0f63-75f7-b4a0-1494c1304c42',
       '019fa91c-3000-7000-8000-000000000003', permission
FROM unnest(ARRAY[
    'contacts.read', 'contacts.write', 'tasks.read', 'tasks.write'
]) AS permission
ON CONFLICT DO NOTHING;

-- Remove concessões de versões anteriores do repeatable. A linha é necessária
-- porque ON CONFLICT não apaga permissão que saiu do catálogo do papel.
DELETE FROM role_permission
 WHERE tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42'
   AND role_id = '019fa91c-3000-7000-8000-000000000003'
   AND permission_code IN (
       'conversations.read', 'conversations.write', 'deals.read', 'deals.write'
   );

INSERT INTO membership_scope
    (id, tenant_id, membership_id, role_id, scope_type, valid_from)
VALUES ('019fa91c-3000-7000-8000-000000000004',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        '019fa91c-3000-7000-8000-000000000002',
        '019fa91c-3000-7000-8000-000000000003', 'OWN', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;

INSERT INTO app_role (id, tenant_id, code, name, system_role)
VALUES ('019fa91c-3000-7000-8000-000000000005',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        'ATTENDANT_SHARED', 'Atendimento compartilhado', false)
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (tenant_id, role_id, permission_code)
SELECT '019fa91c-0f63-75f7-b4a0-1494c1304c42',
       '019fa91c-3000-7000-8000-000000000005', permission
FROM unnest(ARRAY[
    'conversations.read', 'conversations.write', 'deals.read', 'deals.write'
]) AS permission
ON CONFLICT DO NOTHING;

INSERT INTO membership_scope
    (id, tenant_id, membership_id, role_id, scope_type, valid_from)
VALUES ('019fa91c-3000-7000-8000-000000000006',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        '019fa91c-3000-7000-8000-000000000002',
        '019fa91c-3000-7000-8000-000000000005', 'TENANT', '2026-01-01T00:00:00Z')
ON CONFLICT DO NOTHING;
