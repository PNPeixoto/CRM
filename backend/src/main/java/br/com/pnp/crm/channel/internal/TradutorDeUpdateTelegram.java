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

/**
 * Traduz o formato do Telegram para {@link InboundMessage}.
 *
 * <p>Este é o único arquivo do projeto que conhece a estrutura de um update do
 * Telegram. É de propósito: se {@code chat.id} ou {@code message_id}
 * aparecerem em qualquer outro lugar, a normalização deixou de existir e o
 * domínio voltou a conhecer o provedor.
 */
@Component
class TradutorDeUpdateTelegram {

    private final ObjectMapper json;

    TradutorDeUpdateTelegram(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @return vazio quando o update não é uma mensagem que nos interessa —
     * edição, entrada em grupo, resultado de enquete. Ignorar é a resposta
     * certa: não é erro, e transformar em exceção encheria o log de ruído e
     * consumiria tentativas.
     */
    Optional<InboundMessage> traduzir(UUID channelConnectionId, String payloadCru) {
        JsonNode raiz = json.readTree(payloadCru);
        JsonNode mensagem = raiz.path("message");

        if (mensagem.isMissingNode()) {
            return Optional.empty();
        }

        JsonNode chat = mensagem.path("chat");
        if (chat.path("id").isMissingNode()) {
            return Optional.empty();
        }

        // chat.id é o interlocutor. NÃO é o from.id: em grupo, o chat é o
        // destino da conversa e o from é quem falou. Trocar os dois faria cada
        // participante do grupo virar uma conversa separada.
        String externalContactId = chat.path("id").asString();

        return Optional.of(new InboundMessage(
                channelConnectionId,
                mensagem.path("message_id").asString(),
                externalContactId,
                nomeDe(chat),
                classificar(mensagem),
                textoDe(mensagem),
                instanteDe(mensagem),
                diagnostico(raiz)));
    }

    /**
     * O Telegram entrega o nome em partes, e nem sempre todas. Em chat de
     * grupo há {@code title}; em conversa direta, {@code first_name} e
     * opcionalmente {@code last_name}.
     */
    private String nomeDe(JsonNode chat) {
        if (!chat.path("title").isMissingNode()) {
            return chat.path("title").asString();
        }
        String primeiro = chat.path("first_name").asString("");
        String ultimo = chat.path("last_name").asString("");
        String completo = (primeiro + " " + ultimo).trim();
        return completo.isEmpty() ? null : completo;
    }

    private TipoConteudo classificar(JsonNode mensagem) {
        if (!mensagem.path("photo").isMissingNode()) return TipoConteudo.IMAGE;
        if (!mensagem.path("voice").isMissingNode()) return TipoConteudo.AUDIO;
        if (!mensagem.path("audio").isMissingNode()) return TipoConteudo.AUDIO;
        if (!mensagem.path("video").isMissingNode()) return TipoConteudo.VIDEO;
        if (!mensagem.path("document").isMissingNode()) return TipoConteudo.DOCUMENT;
        if (!mensagem.path("location").isMissingNode()) return TipoConteudo.LOCATION;
        if (!mensagem.path("text").isMissingNode()) return TipoConteudo.TEXT;
        // Adesivo, contato, enquete: reconhecidos como mensagem, sem tratamento
        // específico ainda. Melhor registrar como OTHER do que descartar.
        return TipoConteudo.OTHER;
    }

    private String textoDe(JsonNode mensagem) {
        if (!mensagem.path("text").isMissingNode()) {
            return mensagem.path("text").asString();
        }
        // Legenda de foto ou vídeo é texto do cliente e precisa aparecer na
        // conversa; descartá-la perderia o conteúdo da mensagem.
        if (!mensagem.path("caption").isMissingNode()) {
            return mensagem.path("caption").asString();
        }
        return null;
    }

    /**
     * O Telegram envia {@code date} em segundos desde a época. Usar o instante
     * do provedor e não {@code Instant.now()} preserva a latência real da
     * entrega — informação que some se carimbarmos com a hora local.
     */
    private Instant instanteDe(JsonNode mensagem) {
        JsonNode data = mensagem.path("date");
        return data.isMissingNode() ? Instant.now() : Instant.ofEpochSecond(data.asLong());
    }

    /**
     * Recorte do payload para diagnóstico. Não é o update inteiro: o cru já
     * está em {@code inbound_event}, e duplicá-lo em toda mensagem inflaria a
     * tabela mais consultada do sistema.
     */
    private Map<String, Object> diagnostico(JsonNode raiz) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("provedor", "telegram");
        if (!raiz.path("update_id").isMissingNode()) {
            dados.put("update_id", raiz.path("update_id").asString());
        }
        return dados;
    }
}
