package br.com.pnp.crm;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@TesteDeIntegracao
class BancoSegurancaTest {

    private static final Set<String> TABELAS_TENANT = Set.of(
            "tenant", "app_user", "refresh_token", "channel_connection",
            "conversation", "message", "channel_credential", "inbound_event",
            "contact", "pipeline", "pipeline_stage", "deal", "task",
            "tenant_profile", "organizational_unit", "organization_membership",
            "app_role", "role_permission", "membership_scope",
            "password_reset_token", "mfa_authenticator", "mfa_recovery_code",
            "mfa_enrollment");

    private static final Set<String> TABELAS_OPERACIONAIS = Set.of(
            "event_publication", "flyway_schema_history");

    private static final Set<String> FUNCOES_RUNTIME = Set.of(
            "current_tenant_id",
            "resolve_tenant_id_por_slug",
            "resolve_tenant_id_por_refresh_hash",
            "resolve_tenant_id_por_password_reset_hash",
            "resolve_tenant_id_por_mfa_enrollment_hash",
            "resolve_tenant_id_por_channel_connection",
            "reservar_mensagens_para_envio",
            "reservar_eventos_para_processar");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void todaTabelaPublicaEstaClassificadaERlsEhForcadoNasTabelasTenant() {
        List<String> tabelas = jdbc.queryForList("""
                SELECT tablename
                  FROM pg_tables
                 WHERE schemaname = 'public'
                 ORDER BY tablename
                """, String.class);

        assertThat(tabelas).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(TABELAS_TENANT.stream(), TABELAS_OPERACIONAIS.stream())
                        .toList());

        for (String tabela : TABELAS_TENANT) {
            Map<String, Object> protecao = jdbc.queryForMap("""
                    SELECT c.relrowsecurity AS rls,
                           c.relforcerowsecurity AS rls_forcado,
                           count(p.polname) AS politicas
                      FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                 LEFT JOIN pg_policy p ON p.polrelid = c.oid
                     WHERE n.nspname = 'public' AND c.relname = ?
                  GROUP BY c.relrowsecurity, c.relforcerowsecurity
                    """, tabela);
            assertThat(protecao.get("rls")).as(tabela).isEqualTo(true);
            assertThat(protecao.get("rls_forcado")).as(tabela).isEqualTo(true);
            assertThat(((Number) protecao.get("politicas")).longValue()).as(tabela).isPositive();
        }
    }

    @Test
    void papelRuntimeNaoAdministraBancoNemIgnoraRls() {
        Map<String, Object> papel = jdbc.queryForMap("""
                SELECT current_user AS nome, rolsuper, rolbypassrls,
                       rolcreatedb, rolcreaterole
                  FROM pg_roles
                 WHERE rolname = current_user
                """);

        assertThat(papel.get("nome")).isEqualTo("crm_runtime_test");
        assertThat(papel.get("rolsuper")).isEqualTo(false);
        assertThat(papel.get("rolbypassrls")).isEqualTo(false);
        assertThat(papel.get("rolcreatedb")).isEqualTo(false);
        assertThat(papel.get("rolcreaterole")).isEqualTo(false);
        assertThat(jdbc.queryForObject(
                "SELECT has_database_privilege(current_user, current_database(), 'CREATE')",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT has_schema_privilege(current_user, 'public', 'CREATE')",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'tenant', 'TRUNCATE')",
                Boolean.class)).isFalse();
    }

    @Test
    void funcoesPrivilegiadasTemSearchPathFixoESuperficieMinima() {
        List<String> securityDefiners = jdbc.queryForList("""
                SELECT p.proname
                  FROM pg_proc p
                  JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = 'public' AND p.prosecdef
                   AND p.proconfig @> ARRAY['search_path=pg_catalog, public']::text[]
                 ORDER BY p.proname
                """, String.class);
        assertThat(securityDefiners).containsExactlyInAnyOrder(
                "resolve_tenant_id_por_slug",
                "resolve_tenant_id_por_refresh_hash",
                "resolve_tenant_id_por_password_reset_hash",
                "resolve_tenant_id_por_mfa_enrollment_hash",
                "resolve_tenant_id_por_channel_connection",
                "reservar_mensagens_para_envio",
                "reservar_eventos_para_processar");

        List<String> executaveis = jdbc.queryForList("""
                SELECT p.proname
                  FROM pg_proc p
                  JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = 'public'
                   AND has_function_privilege(current_user, p.oid, 'EXECUTE')
                 ORDER BY p.proname
                """, String.class);
        assertThat(executaveis).containsExactlyInAnyOrderElementsOf(FUNCOES_RUNTIME);

        Long publicas = jdbc.queryForObject("""
                SELECT count(*)
                  FROM pg_proc p
                  JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = 'public'
                   AND has_function_privilege('public', p.oid, 'EXECUTE')
                """, Long.class);
        assertThat(publicas).isZero();
    }

    @Test
    void flywayValidaHistoricoLimpoAteV13() {
        flyway.validate();
        List<String> versoes = jdbc.queryForList("""
                SELECT version
                  FROM flyway_schema_history
                 WHERE success AND version IS NOT NULL
                 ORDER BY installed_rank
                """, String.class);
        assertThat(versoes).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
    }
}
