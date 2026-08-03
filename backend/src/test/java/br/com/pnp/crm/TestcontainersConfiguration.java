package br.com.pnp.crm;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.flyway.autoconfigure.FlywayConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * As imagens são as MESMAS do docker-compose de desenvolvimento, e não
 * {@code :latest}.
 *
 * <p>Testar em uma versão de banco diferente da que roda em produção anula
 * boa parte do motivo de usar Testcontainers em vez de mock. Aqui a
 * diferença é concreta: {@code uuidv7()} existe no Postgres 18 e não no 17.
 * Com {@code :latest}, um teste passaria usando uma função que o ambiente
 * real não tem.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
				.withDatabaseName("crm_test")
				.withUsername("crm_migrator_test")
				.withPassword("migrator-test-password")
				.withInitScript("db/test/init_roles.sql");
	}

	@Bean
	JdbcConnectionDetails runtimeConnectionDetails(PostgreSQLContainer container) {
		return new JdbcConnectionDetails() {
			@Override
			public String getUsername() {
				return "crm_runtime_test";
			}

			@Override
			public String getPassword() {
				return "runtime-test-password";
			}

			@Override
			public String getJdbcUrl() {
				return container.getJdbcUrl();
			}
		};
	}

	@Bean
	FlywayConnectionDetails flywayConnectionDetails(PostgreSQLContainer container) {
		return new FlywayConnectionDetails() {
			@Override
			public String getUsername() {
				return container.getUsername();
			}

			@Override
			public String getPassword() {
				return container.getPassword();
			}

			@Override
			public String getJdbcUrl() {
				return container.getJdbcUrl();
			}
		};
	}

	@Bean
	TestDatabaseCleaner testDatabaseCleaner(PostgreSQLContainer container) {
		return new TestDatabaseCleaner(container);
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

}
