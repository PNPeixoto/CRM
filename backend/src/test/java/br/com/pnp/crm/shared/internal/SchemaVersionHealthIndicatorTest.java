package br.com.pnp.crm.shared.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaVersionHealthIndicatorTest {

    @Test
    void readinessIsUpOnlyWhenImageAndDatabaseExpectTheSameSchema() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("13", "12", "14");
        SchemaVersionHealthIndicator indicator = new SchemaVersionHealthIndicator(jdbc, "13");

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    @Test
    void readinessFailsClosedWhenHistoryCannotBeRead() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(new SchemaVersionHealthIndicator(jdbc, "13").health().getStatus().getCode())
                .isEqualTo("DOWN");
    }
}
