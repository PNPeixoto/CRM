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
        assertThat(environment.getProperty("app.schema.expected-version")).isEqualTo("28");
        assertThat(environment.getPropertySources().get("base")
                .getProperty("app.security.report-export-encryption-key"))
                .isEqualTo("${REPORT_EXPORT_ENCRYPTION_KEY}");
        assertThat(environment.getPropertySources().get("base")
                .getProperty("app.security.report-export-signing-key"))
                .isEqualTo("${REPORT_EXPORT_SIGNING_KEY}");
        assertThat(environment.getProperty(
                "app.http-connector.require-egress-proxy", Boolean.class)).isTrue();
        assertThat(environment.getPropertySources().get("prod")
                .getProperty("app.http-connector.egress-proxy-url"))
                .isEqualTo("${HTTP_CONNECTOR_EGRESS_PROXY_URL}");
        assertThat(environment.getProperty("app.providers.evolution.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("app.providers.meta.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("app.providers.meta.graph-api-version"))
                .isEqualTo("v24.0");
        assertThat(environment.getProperty("app.providers.telegram.bot-api-version"))
                .isEqualTo("10.2");
    }
}
