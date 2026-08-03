package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.DomainException;
import br.com.pnp.crm.shared.api.ErroResponse;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantNaoDefinidoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundUsesRfc9457AndPreservesValidCorrelation() {
        String correlationId = UUID.randomUUID().toString();
        MockHttpServletRequest request = request("/api/contatos/ausente", correlationId);

        ResponseEntity<ErroResponse> response = handler.notFound(
                new RecursoNaoEncontradoException("Contato"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isEqualTo(correlationId);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type().toString())
                .isEqualTo("/problemas/recurso-nao-encontrado");
        assertThat(response.getBody().instance().toString())
                .isEqualTo("/api/contatos/ausente");
        assertThat(response.getBody().codigo()).isEqualTo("RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void conflictAndSemanticValidationRemainDistinct() {
        ResponseEntity<ErroResponse> conflict = handler.domain(
                domain("PERFIL_INICIAL_JA_CONCLUIDO"), request("/api/empresa/perfil-inicial", null));
        ResponseEntity<ErroResponse> validation = handler.domain(
                domain("MFA_INVALIDO"), request("/api/auth/mfa/activation", null));

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).isNotNull();
        assertThat(conflict.getBody().status()).isEqualTo(409);
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(validation.getBody()).isNotNull();
        assertThat(validation.getBody().status()).isEqualTo(422);
    }

    @Test
    void invalidCorrelationIsReplacedAndInternalDetailIsSafe() {
        ResponseEntity<ErroResponse> response = handler.missingTenant(
                new TenantNaoDefinidoException(), request("/api/protegida", "quebra-de-log"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().codigo()).isEqualTo("ERRO_INTERNO");
        assertThat(response.getBody().detail()).doesNotContain("tenant", "contexto");
        assertThatCodeIsUuid(response.getHeaders().getFirst("X-Correlation-ID"));
    }

    private static MockHttpServletRequest request(String uri, String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        if (correlationId != null) request.addHeader("X-Correlation-ID", correlationId);
        return request;
    }

    private static DomainException domain(String code) {
        return new DomainException(code, "Mensagem segura.") { };
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotNull();
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
