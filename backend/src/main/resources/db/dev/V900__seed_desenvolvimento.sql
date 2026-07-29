-- TESTE — TROCAR ANTES DE PRODUÇÃO
--
-- Dados de desenvolvimento. Esta pasta (db/dev) só entra em
-- spring.flyway.locations no profile dev, em application-dev.yml. Produção
-- carrega apenas classpath:db/migration, então não existe caminho pelo qual
-- este arquivo chegue lá por esquecimento — é uma questão de configuração,
-- não de disciplina.
--
-- A numeração começa em V900 de propósito: as migrations de estrutura vivem
-- na faixa baixa e podem crescer por anos sem alcançar o seed. Flyway ordena
-- por versão, não por pasta, então sem essa separação um V2 de estrutura
-- rodaria DEPOIS de um seed V2 conflitante e a ordem dependeria de sorte.
--
-- SENHA DOS USUÁRIOS ABAIXO: não está escrita em lugar nenhum, e não deve
-- ser. Os hashes são Argon2id com o APP_PEPPER de desenvolvimento definido em
-- application-dev.yml. Trocar o pepper invalida estes hashes — é o efeito
-- pretendido do pepper.

-- ---------------------------------------------------------------------------
-- Tenant 1 — a franqueadora principal de desenvolvimento
-- ---------------------------------------------------------------------------

-- Cada bloco precisa do SET LOCAL porque as políticas de RLS estão FORCE e
-- valem inclusive para o dono do banco, que é quem o Flyway usa. Sem isto, o
-- WITH CHECK da política rejeita o INSERT. É a prova mais barata de que o RLS
-- realmente está ativo: se este seed passasse sem SET LOCAL, o isolamento
-- estaria desligado.
SET LOCAL app.tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42';

INSERT INTO tenant (id, slug, name)
VALUES ('019fa91c-0f63-75f7-b4a0-1494c1304c42', 'pnp', 'PNP Franqueadora');

INSERT INTO app_user (id, tenant_id, login, email, full_name, password_hash)
VALUES ('019fa91c-0f66-7a68-8384-20e85b6c8f5a',
        '019fa91c-0f63-75f7-b4a0-1494c1304c42',
        'peixoto',
        'peixoto@pnp.exemplo.com.br',
        'Pedro Peixoto',
        '$argon2id$v=19$m=16384,t=2,p=1$3nUgapjwXyOihLKkHEykhQ$+y6IgEuBhsJemmGjKglEZv3MQvbtcuGvu9Tln3JwzaQ');

-- ---------------------------------------------------------------------------
-- Tenant 2 — existe para tornar o vazamento entre tenants visível
-- ---------------------------------------------------------------------------
--
-- Sem um segundo tenant povoado, qualquer bug de isolamento passa despercebido
-- em desenvolvimento: uma consulta sem filtro devolve exatamente o mesmo
-- resultado de uma consulta correta. O login `peixoto` se repete de propósito,
-- para exercitar a unicidade por tenant.

SET LOCAL app.tenant_id = '019fa91c-0f66-7d34-9a39-2f79328d02c9';

INSERT INTO tenant (id, slug, name)
VALUES ('019fa91c-0f66-7d34-9a39-2f79328d02c9', 'acme', 'Acme Locações');

INSERT INTO app_user (id, tenant_id, login, email, full_name, password_hash)
VALUES ('019fa91c-0f67-74ec-8e9c-1be4dbf398a7',
        '019fa91c-0f66-7d34-9a39-2f79328d02c9',
        'peixoto',
        'peixoto@acme.exemplo.com.br',
        'Maria Peixoto',
        '$argon2id$v=19$m=16384,t=2,p=1$via+bUyUAhgg9HLeOoBfKg$xuJ4Z8mplRCY4Ras9JNPA1rtcBSC43Tq6y5em5WHjO8');
