package br.com.pnp.crm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("carga")
class PerfisDeCargaContratoTest {

    @TestFactory
    Stream<DynamicTest> perfisSaoCompletosESeguros() throws IOException {
        Properties propriedades = new Properties();
        try (InputStream arquivo = getClass().getResourceAsStream("/carga/perfis.properties")) {
            assertThat(arquivo).as("arquivo de perfis de carga").isNotNull();
            propriedades.load(arquivo);
        }

        return Arrays.stream(propriedades.getProperty("perfis").split(","))
                .map(String::trim)
                .map(nome -> DynamicTest.dynamicTest(nome, () -> validar(propriedades, nome)));
    }

    private static void validar(Properties propriedades, String nome) {
        assertThat(propriedades.getProperty(nome + ".metodo"))
                .isIn("GET", "POST");
        assertThat(propriedades.getProperty(nome + ".rota"))
                .startsWith("/api/")
                .doesNotContain("token", "senha", "password");
        assertThat(Integer.parseInt(propriedades.getProperty(nome + ".concorrencia")))
                .isBetween(1, 500);
        assertThat(Duration.parse(propriedades.getProperty(nome + ".duracao")))
                .isBetween(Duration.ofSeconds(10), Duration.ofMinutes(15));
        assertThat(Integer.parseInt(propriedades.getProperty(nome + ".p95.ms")))
                .isPositive();
        assertThat(Double.parseDouble(propriedades.getProperty(nome + ".erro.maximo")))
                .isBetween(0.0, 0.05);
    }
}
