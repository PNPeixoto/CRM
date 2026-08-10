package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.channel.api.TipoConteudo;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Traduz somente MESSAGES_UPSERT da Evolution para o contrato normalizado. */
@Component
class TradutorDeWebhookEvolution {

    private final ObjectMapper json;

    TradutorDeWebhookEvolution(ObjectMapper json) {
        this.json = json;
    }

    Optional<InboundMessage> traduzir(UUID connectionId, String payloadCru) {
        JsonNode raiz = json.readTree(payloadCru);
        if (!"messages.upsert".equalsIgnoreCase(raiz.path("event").asString())) {
            return Optional.empty();
        }
        JsonNode dados = raiz.path("data");
        if (dados.isArray()) {
            dados = dados.isEmpty() ? json.createObjectNode() : dados.get(0);
        }
        JsonNode chave = dados.path("key");
        if (chave.path("fromMe").asBoolean(false)) {
            // O envio ja foi persistido pela outbox. Reingeri-lo duplicaria a
            // mensagem como se ela tivesse vindo do cliente.
            return Optional.empty();
        }

        String externalId = chave.path("id").asString("");
        String contato = contatoDe(chave);
        if (externalId.isBlank() || contato.isBlank()) {
            return Optional.empty();
        }

        JsonNode mensagem = dados.path("message");
        TipoConteudo tipo = tipoDe(mensagem);
        String texto = textoDe(mensagem);
        Instant ocorridoEm = instanteDe(dados.path("messageTimestamp"));

        Map<String, Object> diagnostico = new LinkedHashMap<>();
        diagnostico.put("provedor", "evolution");
        diagnostico.put("evento", "messages.upsert");
        if (!raiz.path("instance").asString("").isBlank()) {
            diagnostico.put("instancia", raiz.path("instance").asString());
        }

        return Optional.of(new InboundMessage(
                connectionId, externalId, contato,
                dados.path("pushName").asString(null), tipo, texto,
                ocorridoEm, diagnostico));
    }

    private String contatoDe(JsonNode chave) {
        String principal = chave.path("remoteJid").asString("");
        String alternativo = chave.path("remoteJidAlt").asString("");
        String escolhido = principal.endsWith("@lid")
                && alternativo.endsWith("@s.whatsapp.net") ? alternativo : principal;
        return escolhido.endsWith("@s.whatsapp.net")
                ? escolhido.substring(0, escolhido.length() - "@s.whatsapp.net".length())
                : escolhido;
    }

    private TipoConteudo tipoDe(JsonNode mensagem) {
        if (!mensagem.path("imageMessage").isMissingNode()) return TipoConteudo.IMAGE;
        if (!mensagem.path("audioMessage").isMissingNode()) return TipoConteudo.AUDIO;
        if (!mensagem.path("videoMessage").isMissingNode()) return TipoConteudo.VIDEO;
        if (!mensagem.path("documentMessage").isMissingNode()) return TipoConteudo.DOCUMENT;
        if (!mensagem.path("locationMessage").isMissingNode()) return TipoConteudo.LOCATION;
        if (!mensagem.path("conversation").isMissingNode()
                || !mensagem.path("extendedTextMessage").isMissingNode()) {
            return TipoConteudo.TEXT;
        }
        return TipoConteudo.OTHER;
    }

    private String textoDe(JsonNode mensagem) {
        if (!mensagem.path("conversation").isMissingNode()) {
            return mensagem.path("conversation").asString(null);
        }
        if (!mensagem.path("extendedTextMessage").path("text").isMissingNode()) {
            return mensagem.path("extendedTextMessage").path("text").asString(null);
        }
        for (String campo : java.util.List.of(
                "imageMessage", "videoMessage", "documentMessage")) {
            JsonNode caption = mensagem.path(campo).path("caption");
            if (!caption.isMissingNode()) return caption.asString(null);
        }
        return null;
    }

    private Instant instanteDe(JsonNode timestamp) {
        if (timestamp.canConvertToLong() && timestamp.asLong() > 0) {
            return Instant.ofEpochSecond(timestamp.asLong());
        }
        try {
            long segundos = Long.parseLong(timestamp.asString(""));
            return segundos > 0 ? Instant.ofEpochSecond(segundos) : Instant.now();
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }
}
