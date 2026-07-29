package br.com.pnp.crm;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

}
