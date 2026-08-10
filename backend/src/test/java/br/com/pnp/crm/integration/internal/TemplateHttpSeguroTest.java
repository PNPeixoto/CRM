package br.com.pnp.crm.integration.internal;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateHttpSeguroTest {

    private final TemplateHttpSeguro template = new TemplateHttpSeguro(new ObjectMapper());

    @Test
    void renderizaSomenteVariaveisTecnicasComEscapePorContexto() {
        Map<String, String> valores = Map.of(
                "tenantId", "tenant com / e ?",
                "executionId", "exec\"\nlinha",
                "sourceReferenceId", "fonte",
                "stepKey", "passo",
                "triggerType", "MESSAGE_RECEIVED",
                "idempotencyKey", "idem");

        assertThat(template.renderizarUrl("/v1/{{tenantId}}", valores))
                .isEqualTo("/v1/tenant%20com%20%2F%20e%20%3F");
        assertThat(template.renderizarJson("{\"id\":\"{{executionId}}\"}", valores))
                .isEqualTo("{\"id\":\"exec\\\"\\nlinha\"}");
    }

    @Test
    void rejeitaLinguagensDeExpressaoTravessiaEVariaveisLivres() {
        String[] cargas = {
                "/${T(java.lang.Runtime).getRuntime()}",
                "/#{new java.io.File('/tmp')}",
                "/<% Runtime.getRuntime() %>",
                "/{{class}}", "/{{url}}", "/{{../arquivo}}", "/{{{tenantId}}}"
        };
        for (String carga : cargas) {
            assertThatThrownBy(() -> template.validarCaminho(carga))
                    .as(carga)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejeitaJsonQueNaoPermaneceValido() {
        assertThatThrownBy(() -> template.validarJson("{\"id\":{{tenantId}}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> template.validarJson("{\"x\":\"${secret}\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
