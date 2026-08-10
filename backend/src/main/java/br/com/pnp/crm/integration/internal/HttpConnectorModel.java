package br.com.pnp.crm.integration.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

final class HttpConnectorModel {

    private HttpConnectorModel() {
    }

    record ConnectorRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_-]{2,79}$") String codigo,
            @NotBlank @Size(max = 200) String nome,
            @NotBlank @Pattern(regexp = "^(GET|POST|PUT|PATCH)$") String metodo,
            @NotBlank @Size(max = 500) String origem,
            @NotBlank @Size(max = 2048) String caminhoTemplate,
            @Size(max = 65536) String corpoTemplate,
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9-]{0,63}$") String cabecalhoIdempotencia,
            @Min(100) @Max(30000) int timeoutMs,
            @Min(1024) @Max(1048576) int maxRespostaBytes,
            @Min(1) @Max(1000) int requisicoesPorMinuto) {
    }

    record SecretRequest(
            @NotBlank @Pattern(regexp = "^(BEARER|HEADER)$") String tipo,
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9-]{0,63}$") String cabecalho,
            @NotBlank @Size(max = 4096) String segredo) {
    }

    record Resumo(
            UUID id,
            String codigo,
            String nome,
            String status,
            String metodo,
            String origem,
            String caminhoTemplate,
            String contentType,
            String cabecalhoIdempotencia,
            int timeoutMs,
            int maxRespostaBytes,
            int requisicoesPorMinuto,
            boolean credencialConfigurada,
            String cabecalhoAutenticacao,
            Instant criadaEm,
            Instant atualizadaEm) {
    }

    /** Preview deliberadamente não inclui corpo nem valor da credencial. */
    record Preview(
            String metodo,
            String destinoTemplate,
            String contentType,
            String cabecalhoIdempotencia,
            boolean credencialConfigurada,
            String cabecalhoAutenticacao) {
    }

    record Configuracao(
            UUID id,
            UUID tenantId,
            String status,
            String metodo,
            String origem,
            String caminhoTemplate,
            String corpoTemplate,
            String contentType,
            String cabecalhoIdempotencia,
            int timeoutMs,
            int maxRespostaBytes,
            int requisicoesPorMinuto) {
    }

    record Segredo(
            String tipo,
            String cabecalho,
            String ciphertext,
            int versaoChave) {
    }
}
