package br.com.pnp.crm.integration.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validação fail-closed da definição aprovada, antes de persistir. */
@Component
class ValidadorConectorHttp {

    private static final Pattern CABECALHO =
            Pattern.compile("^[A-Za-z][A-Za-z0-9-]{0,63}$");
    private static final Set<String> PROIBIDOS = Set.of(
            "host", "content-length", "transfer-encoding", "connection",
            "cookie", "set-cookie", "proxy-authorization", "proxy-authenticate",
            "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
            "accept", "content-type", "user-agent");

    private final TemplateHttpSeguro templates;
    private final int maxTimeout;
    private final int maxResposta;
    private final int maxPorMinuto;

    ValidadorConectorHttp(
            TemplateHttpSeguro templates,
            @Value("${app.http-connector.max-timeout-ms:30000}") int maxTimeout,
            @Value("${app.http-connector.max-response-bytes:1048576}") int maxResposta,
            @Value("${app.http-connector.max-requests-per-minute:120}") int maxPorMinuto) {
        this.templates = templates;
        this.maxTimeout = Math.clamp(maxTimeout, 100, 30000);
        this.maxResposta = Math.clamp(maxResposta, 1024, 1048576);
        this.maxPorMinuto = Math.clamp(maxPorMinuto, 1, 1000);
    }

    ConfiguracaoValidada validar(HttpConnectorModel.ConnectorRequest request) {
        if (request == null) throw invalida();
        String metodo = request.metodo().toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "POST", "PUT", "PATCH").contains(metodo)) throw invalida();
        String origem = normalizarOrigem(request.origem());
        templates.validarCaminho(request.caminhoTemplate());
        templates.validarJson(request.corpoTemplate());
        if (metodo.equals("GET") && request.corpoTemplate() != null) throw invalida();

        String idempotencia = vazioParaNulo(request.cabecalhoIdempotencia());
        if (metodo.equals("GET")) {
            if (idempotencia != null) throw invalida();
        } else {
            validarCabecalho(idempotencia, false);
        }
        if (request.timeoutMs() < 100 || request.timeoutMs() > maxTimeout
                || request.maxRespostaBytes() < 1024
                || request.maxRespostaBytes() > maxResposta
                || request.requisicoesPorMinuto() < 1
                || request.requisicoesPorMinuto() > maxPorMinuto) {
            throw invalida();
        }
        return new ConfiguracaoValidada(
                request.codigo(), request.nome().trim(), metodo, origem,
                request.caminhoTemplate(), request.corpoTemplate(), idempotencia,
                request.timeoutMs(), request.maxRespostaBytes(),
                request.requisicoesPorMinuto());
    }

    SegredoValidado validar(HttpConnectorModel.SecretRequest request) {
        if (request == null || request.segredo() == null || request.segredo().isBlank()
                || request.segredo().indexOf('\r') >= 0 || request.segredo().indexOf('\n') >= 0) {
            throw invalida();
        }
        String tipo = request.tipo().toUpperCase(Locale.ROOT);
        if (tipo.equals("BEARER")) {
            if (vazioParaNulo(request.cabecalho()) != null) throw invalida();
            return new SegredoValidado(tipo, null, request.segredo());
        }
        if (!tipo.equals("HEADER")) throw invalida();
        String cabecalho = request.cabecalho();
        validarCabecalho(cabecalho, true);
        if (cabecalho.equalsIgnoreCase("authorization")) throw invalida();
        return new SegredoValidado(tipo, cabecalho, request.segredo());
    }

    private static String normalizarOrigem(String valor) {
        try {
            URI uri = URI.create(valor.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                    && !uri.getRawPath().equals("/"))) {
                throw invalida();
            }
            String host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) throw invalida();
            return new URI("https", null, host, uri.getPort(), null, null, null).toString();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException ilegal) throw ilegal;
            throw invalida();
        }
    }

    private static void validarCabecalho(String nome, boolean autenticacao) {
        if (nome == null || !CABECALHO.matcher(nome).matches()
                || PROIBIDOS.contains(nome.toLowerCase(Locale.ROOT))
                || (!autenticacao && nome.equalsIgnoreCase("authorization"))) {
            throw invalida();
        }
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static IllegalArgumentException invalida() {
        return new IllegalArgumentException("Configuracao do conector HTTP invalida.");
    }

    record ConfiguracaoValidada(
            String codigo, String nome, String metodo, String origem,
            String caminho, String corpo, String idempotencia, int timeoutMs,
            int maxRespostaBytes, int requisicoesPorMinuto) {
    }

    record SegredoValidado(String tipo, String cabecalho, String segredo) {
    }
}
