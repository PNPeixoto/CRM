-- Ambiente de DESENVOLVIMENTO apenas.
-- O POSTGRES_USER (crm_migrator) continua administrativo para o Flyway. A
-- aplicação usa este papel separado, sem qualquer forma de ignorar RLS.
-- TESTE — TROCAR AS SENHAS ANTES DE PRODUÇÃO.

CREATE ROLE crm_runtime
    LOGIN
    PASSWORD 'crm_runtime_dev_local'
    NOSUPERUSER
    NOBYPASSRLS
    NOCREATEDB
    NOCREATEROLE;

GRANT CONNECT ON DATABASE crm TO crm_runtime;
GRANT USAGE ON SCHEMA public TO crm_runtime;

-- O script roda antes do Flyway. Default privileges garantem que cada tabela,
-- sequência e função futura criada pelo migrator já nasça acessível ao runtime
-- sem conceder CREATE, TRUNCATE ou privilégios administrativos.
ALTER DEFAULT PRIVILEGES FOR ROLE crm_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO crm_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_migrator IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

REVOKE CREATE ON SCHEMA public FROM crm_runtime;
