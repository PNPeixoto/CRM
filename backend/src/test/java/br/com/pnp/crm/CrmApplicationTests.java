package br.com.pnp.crm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sobe o contexto inteiro contra Postgres e Redis reais.
 *
 * <p>Parece trivial e não é: aqui é onde as migrations do Flyway rodam de fato,
 * onde o {@code ddl-auto: validate} confronta cada entidade JPA com o schema
 * criado pela V1, e onde a exigência de {@code APP_PEPPER} e
 * {@code JWT_SIGNING_KEY} é exercida. Uma coluna com nome divergente derruba
 * este teste antes de qualquer outro.
 */
@TesteDeIntegracao
class CrmApplicationTests {

	@Test
	@DisplayName("o contexto sobe, as migrations aplicam e o schema bate com as entidades")
	void contextLoads() {
	}

}
