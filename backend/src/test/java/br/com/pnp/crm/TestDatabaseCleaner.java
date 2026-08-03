package br.com.pnp.crm;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Limpeza administrativa isolada; nunca é injetada em código de produção. */
public final class TestDatabaseCleaner {

    private final PostgreSQLContainer container;

    TestDatabaseCleaner(PostgreSQLContainer container) {
        this.container = container;
    }

    public void clean() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), "crm_migrator_test", "migrator-test-password");
        new JdbcTemplate(dataSource).execute(
                "TRUNCATE TABLE event_publication, tenant CASCADE");
    }
}
