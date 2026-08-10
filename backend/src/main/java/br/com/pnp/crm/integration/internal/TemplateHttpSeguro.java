package br.com.pnp.crm.integration.internal;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Substituição finita de valores técnicos; não é linguagem de expressão. */
@Component
class TemplateHttpSeguro {

    private static final Pattern VARIAVEL =
            Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9]{0,39})}}");
    private static final Set<String> PERMITIDAS = Set.of(
            "tenantId", "executionId", "sourceReferenceId", "stepKey",
            "triggerType", "idempotencyKey");
    private static final String[] MARCADORES_PROIBIDOS = {
            "${", "#{", "@{", "<%", "%>", "{{{", "}}}"
    };

    private final ObjectMapper json;

    TemplateHttpSeguro(ObjectMapper json) {
        this.json = json;
    }

    void validarCaminho(String template) {
        if (template == null || !template.startsWith("/") || template.length() > 2048
                || template.indexOf('\r') >= 0 || template.indexOf('\n') >= 0
                || template.contains("://") || template.contains("\\")
                || template.contains("#")) {
            throw new IllegalArgumentException("Template de caminho invalido.");
        }
        validarMarcadores(template);
        String amostra = renderizar(template, valoresAmostra(), Modo.URL);
        if (!amostra.startsWith("/") || amostra.contains(" ")) {
            throw new IllegalArgumentException("Template de caminho invalido.");
        }
    }

    void validarJson(String template) {
        if (template == null) return;
        if (template.length() > 65536 || template.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Template de corpo invalido.");
        }
        validarMarcadores(template);
        try {
            json.readTree(renderizar(template, valoresAmostra(), Modo.JSON));
        } catch (Exception e) {
            throw new IllegalArgumentException("Template de corpo precisa gerar JSON valido.");
        }
    }

    String renderizarUrl(String template, Map<String, String> valores) {
        validarCaminho(template);
        return renderizar(template, valores, Modo.URL);
    }

    String renderizarJson(String template, Map<String, String> valores) {
        if (template == null) return null;
        validarJson(template);
        String renderizado = renderizar(template, valores, Modo.JSON);
        try {
            json.readTree(renderizado);
            return renderizado;
        } catch (Exception e) {
            throw new IllegalArgumentException("Corpo renderizado invalido.");
        }
    }

    private void validarMarcadores(String template) {
        for (String proibido : MARCADORES_PROIBIDOS) {
            if (template.contains(proibido)) {
                throw new IllegalArgumentException("Expressao nao permitida.");
            }
        }
        Matcher matcher = VARIAVEL.matcher(template);
        StringBuilder semVariaveis = new StringBuilder();
        while (matcher.find()) {
            if (!PERMITIDAS.contains(matcher.group(1))) {
                throw new IllegalArgumentException("Variavel de template nao permitida.");
            }
            matcher.appendReplacement(semVariaveis, "x");
        }
        matcher.appendTail(semVariaveis);
        if (semVariaveis.indexOf("{{") >= 0 || semVariaveis.indexOf("}}") >= 0) {
            throw new IllegalArgumentException("Expressao de template invalida.");
        }
    }

    private String renderizar(String template, Map<String, String> valores, Modo modo) {
        Matcher matcher = VARIAVEL.matcher(template);
        StringBuilder saida = new StringBuilder(template.length() + 64);
        while (matcher.find()) {
            String valor = valores.get(matcher.group(1));
            if (valor == null) valor = "";
            String seguro = modo == Modo.URL ? url(valor) : jsonString(valor);
            matcher.appendReplacement(saida, Matcher.quoteReplacement(seguro));
        }
        matcher.appendTail(saida);
        return saida.toString();
    }

    private String jsonString(String valor) {
        try {
            String literal = json.writeValueAsString(valor);
            return literal.substring(1, literal.length() - 1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor de template invalido.");
        }
    }

    private static String url(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Map<String, String> valoresAmostra() {
        return Map.of(
                "tenantId", "00000000-0000-0000-0000-000000000001",
                "executionId", "00000000-0000-0000-0000-000000000002",
                "sourceReferenceId", "00000000-0000-0000-0000-000000000003",
                "stepKey", "passo",
                "triggerType", "MESSAGE_RECEIVED",
                "idempotencyKey", "abc123");
    }

    private enum Modo { URL, JSON }
}
