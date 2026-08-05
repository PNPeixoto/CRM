package br.com.pnp.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    @Test
    void productionClosesOpenApiAndPreservesStrictInputAllowlist() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var environment = new StandardEnvironment();
        loader.load("base", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addFirst);
        loader.load("prod", new ClassPathResource("application-prod.yml"))
                .forEach(environment.getPropertySources()::addFirst);

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty(
                "spring.jackson.deserialization.fail-on-unknown-properties", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("app.schema.expected-version")).isEqualTo("13");
    }
}
