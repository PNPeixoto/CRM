-- O container nasce com crm_migrator_test como administrador e dono do banco.
-- Este script cria somente o papel usado pela aplicação, que nunca recebe
-- privilégio capaz de ignorar RLS.

CREATE ROLE crm_runtime_test
    LOGIN
    PASSWORD 'runtime-test-password'
    NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

GRANT CONNECT ON DATABASE crm_test TO crm_runtime_test;
GRANT USAGE ON SCHEMA public TO crm_runtime_test;

ALTER DEFAULT PRIVILEGES FOR ROLE crm_migrator_test IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_runtime_test;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_migrator_test IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO crm_runtime_test;
ALTER DEFAULT PRIVILEGES FOR ROLE crm_migrator_test IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

REVOKE CREATE ON SCHEMA public FROM crm_runtime_test;
