package br.com.pnp.crm.shared.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Readiness falha tanto para banco atrasado quanto para imagem antiga. */
@Component("schemaVersion")
class SchemaVersionHealthIndicator implements HealthIndicator {

    private static final String CURRENT_STRUCTURAL_VERSION = """
            SELECT version
              FROM flyway_schema_history
             WHERE success
               AND version ~ '^[0-9]+$'
               AND version::integer < 900
             ORDER BY version::integer DESC
             LIMIT 1
            """;

    private final JdbcTemplate jdbc;
    private final String expectedVersion;

    SchemaVersionHealthIndicator(
            JdbcTemplate jdbc,
            @Value("${app.schema.expected-version}") String expectedVersion) {
        this.jdbc = jdbc;
        this.expectedVersion = expectedVersion;
    }

    @Override
    public Health health() {
        try {
            String applied = jdbc.queryForObject(CURRENT_STRUCTURAL_VERSION, String.class);
            if (expectedVersion.equals(applied)) {
                return Health.up()
                        .withDetail("expected", expectedVersion)
                        .withDetail("applied", applied)
                        .build();
            }
            return Health.down()
                    .withDetail("reason", "schema-version-mismatch")
                    .withDetail("expected", expectedVersion)
                    .withDetail("applied", applied == null ? "none" : applied)
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("reason", "schema-version-unavailable")
                    .withDetail("expected", expectedVersion)
                    .build();
        }
    }
}
