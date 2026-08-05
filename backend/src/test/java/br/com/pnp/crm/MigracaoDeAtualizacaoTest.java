package br.com.pnp.crm;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integracao")
class MigracaoDeAtualizacaoTest {

    @Test
    void bancoEmV8AtualizaAteVersaoAtualEValidaSemEditarHistorico() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("crm_test")
                .withUsername("crm_migrator_test")
                .withPassword("migrator-test-password")
                .withInitScript("db/test/init_roles.sql")) {
            postgres.start();

            Flyway ateV8 = configuracao(postgres).target("8").load();
            assertThat(ateV8.migrate().migrationsExecuted).isEqualTo(8);

            Flyway atual = configuracao(postgres).load();
            assertThat(atual.migrate().migrationsExecuted).isEqualTo(5);
            atual.validate();
            assertThat(atual.info().current().getVersion().getVersion()).isEqualTo("13");
        }
    }

    @Test
    void seedsDeDesenvolvimentoAplicamComOsPapeisDeEscopoSeparados() throws Exception {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("crm_test")
                .withUsername("crm_migrator_test")
                .withPassword("migrator-test-password")
                .withInitScript("db/test/init_roles.sql")) {
            postgres.start();

            Flyway dev = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/dev")
                    .placeholders(Map.of("runtime_role", "crm_runtime_test"))
                    .outOfOrder(true)
                    .load();
            dev.migrate();
            dev.validate();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var statement = connection.prepareStatement("""
                         SELECT count(*) FILTER (WHERE s.scope_type = 'TENANT'),
                                count(*) FILTER (WHERE s.scope_type = 'OWN')
                           FROM tenant t
                           JOIN app_user u ON u.tenant_id = t.id
                           JOIN organization_membership m
                             ON m.tenant_id = t.id AND m.user_id = u.id
                           JOIN membership_scope s
                             ON s.tenant_id = t.id AND s.membership_id = m.id
                           JOIN role_permission p
                             ON p.tenant_id = t.id AND p.role_id = s.role_id
                          WHERE t.slug = 'pnp'
                            AND u.login = 'atendente'
                            AND p.permission_code IN ('deals.read', 'deals.write')
                         """)) {
                try (var result = statement.executeQuery()) {
                    result.next();
                    assertThat(result.getLong(1)).isEqualTo(2);
                    assertThat(result.getLong(2)).isZero();
                }
            }
        }
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration configuracao(
            PostgreSQLContainer postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("runtime_role", "crm_runtime_test"));
    }
}
