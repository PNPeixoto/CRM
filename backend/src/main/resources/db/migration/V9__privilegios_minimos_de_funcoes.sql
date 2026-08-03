-- Fecha a superfície de funções do schema public.
--
-- V1–V8 já podem estar aplicadas e não são alteradas. O papel de runtime é um
-- placeholder do Flyway porque teste, desenvolvimento e produção usam logins
-- distintos. O valor vem da configuração confiável do deploy, nunca de uma
-- requisição.

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM "${runtime_role}";

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM "${runtime_role}";

-- Usada pelas policies de RLS em toda consulta do runtime.
GRANT EXECUTE ON FUNCTION current_tenant_id()
    TO "${runtime_role}";

-- Superfície SECURITY DEFINER deliberadamente pequena. Cada função devolve
-- somente o identificador necessário para estabelecer o tenant ou reservar
-- um lote; dados de negócio são lidos depois sob RLS.
GRANT EXECUTE ON FUNCTION resolve_tenant_id_por_slug(text)
    TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION resolve_tenant_id_por_refresh_hash(text)
    TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION resolve_tenant_id_por_channel_connection(uuid)
    TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION reservar_mensagens_para_envio(integer, integer, integer)
    TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION reservar_eventos_para_processar(integer, integer, integer)
    TO "${runtime_role}";

COMMENT ON SCHEMA public IS
    'Funções não são executáveis por PUBLIC. O runtime recebe apenas current_tenant_id e cinco entradas auditadas.';
