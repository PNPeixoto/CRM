package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.channel.api.TipoConteudo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Traduz notificacoes {@code messages} da Instagram Messaging API. */
@Component
class TradutorDeWebhookInstagram {

    private final ObjectMapper json;

    TradutorDeWebhookInstagram(ObjectMapper json) {
        this.json = json;
    }

    List<InboundMessage> traduzir(UUID connectionId, String payloadCru) {
        JsonNode raiz = json.readTree(payloadCru);
        if (!"instagram".equals(raiz.path("object").asString())) return List.of();

        List<InboundMessage> mensagens = new ArrayList<>();
        raiz.path("entry").forEach(entry -> entry.path("messaging").forEach(evento -> {
            JsonNode mensagem = evento.path("message");
            if (!mensagem.isObject()
                    || mensagem.path("is_echo").asBoolean(false)
                    || mensagem.path("is_self").asBoolean(false)
                    || mensagem.path("is_deleted").asBoolean(false)) {
                return;
            }

            String externalId = mensagem.path("mid").asString("");
            String contato = evento.path("sender").path("id").asString("");
            if (externalId.isBlank() || contato.isBlank()) return;

            String texto = mensagem.path("text").asString(null);
            String anexo = tipoDoPrimeiroAnexo(mensagem);
            TipoConteudo tipo = tipoDe(texto, anexo);
            Map<String, Object> diagnostico = new LinkedHashMap<>();
            diagnostico.put("provedor", "instagram");
            diagnostico.put("evento", "messages");
            if (anexo != null) diagnostico.put("tipo_anexo", anexo);
            if (mensagem.path("is_unsupported").asBoolean(false)) {
                diagnostico.put("conteudo_nao_suportado", true);
            }

            mensagens.add(new InboundMessage(
                    connectionId, externalId, contato, null, tipo, texto,
                    instanteDe(evento.path("timestamp")), diagnostico));
        }));
        return List.copyOf(mensagens);
    }

    private String tipoDoPrimeiroAnexo(JsonNode mensagem) {
        JsonNode anexos = mensagem.path("attachments");
        if (!anexos.isArray() || anexos.isEmpty()) return null;
        String tipo = anexos.get(0).path("type").asString("");
        return tipo.isBlank() ? null : tipo;
    }

    private TipoConteudo tipoDe(String texto, String anexo) {
        if (texto != null && !texto.isBlank()) return TipoConteudo.TEXT;
        if (anexo == null) return TipoConteudo.OTHER;
        return switch (anexo.toLowerCase(java.util.Locale.ROOT)) {
            case "image", "share", "story_mention" -> TipoConteudo.IMAGE;
            case "audio" -> TipoConteudo.AUDIO;
            case "video", "ig_reel", "reel" -> TipoConteudo.VIDEO;
            case "file" -> TipoConteudo.DOCUMENT;
            case "location" -> TipoConteudo.LOCATION;
            default -> TipoConteudo.OTHER;
        };
    }

    private Instant instanteDe(JsonNode valor) {
        try {
            long millis = valor.canConvertToLong()
                    ? valor.asLong() : Long.parseLong(valor.asString(""));
            return millis > 0 ? Instant.ofEpochMilli(millis) : Instant.now();
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }
}
