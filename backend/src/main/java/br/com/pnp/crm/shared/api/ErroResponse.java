package br.com.pnp.crm.shared.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** RFC 9457 com extensões estáveis do CRM. */
public record ErroResponse(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String codigo,
        String correlacaoId,
        List<ErroCampo> campos,
        Instant momento) {

    public ErroResponse {
        campos = campos == null ? List.of() : List.copyOf(campos);
    }

    public static ErroResponse de(int status, String title, String codigo, String detail,
                                  String correlacaoId, URI instance) {
        URI type = URI.create("/problemas/" + codigo.toLowerCase(java.util.Locale.ROOT)
                .replace('_', '-'));
        return new ErroResponse(type, title, status, detail, instance, codigo,
                correlacaoId, List.of(), Instant.now());
    }

    public record ErroCampo(String campo, String mensagem) {
    }
}
