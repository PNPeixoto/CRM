package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ChannelAdapter;
import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Adaptador oficial do Telegram Bot API, fixado no contrato 10.2. */
@Component
class TelegramAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAdapter.class);
    private static final String BASE_API = "https://api.telegram.org/bot";
    private static final String BASE_ARQUIVO = "https://api.telegram.org/file/bot";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern SEGREDO_WEBHOOK = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    private final ObjectMapper json;
    private final CredenciaisDeCanal credenciais;
    private final TelegramTransport transport;
    private final TelegramRateLimiter rateLimiter;
    private final String versaoDoContrato;

    TelegramAdapter(ObjectMapper json, CredenciaisDeCanal credenciais,
                    TelegramTransport transport, TelegramRateLimiter rateLimiter,
                    @Value("${app.providers.telegram.bot-api-version}") String versaoDoContrato) {
        this.json = json;
        this.credenciais = credenciais;
        this.transport = transport;
        this.rateLimiter = rateLimiter;
        this.versaoDoContrato = versaoDoContrato;
        if (!"10.2".equals(versaoDoContrato)) {
            throw new IllegalStateException(
                    "Contrato Telegram nao revisado. Esperado Bot API 10.2.");
        }
    }

    @Override
    public TipoCanal tipo() {
        return TipoCanal.TELEGRAM;
    }

    @Override
    public String enviar(OutboundMessage mensagem) {
        rateLimiter.exigir(mensagem.channelConnectionId());
        String token = token(mensagem.channelConnectionId());
        TelegramTransport.Resposta resposta = chamar(
                token, "sendMessage", json.writeValueAsString(Map.of(
                        "chat_id", mensagem.externalContactId(),
                        "text", mensagem.texto() == null ? "" : mensagem.texto())));
        return extrairMessageId(resposta);
    }

    WebhookState obterEstadoWebhook(UUID channelConnectionId) {
        TelegramTransport.Resposta resposta = chamar(
                token(channelConnectionId), "getWebhookInfo", "{}");
        JsonNode corpo = exigirSucesso(resposta);
        JsonNode estado = corpo.path("result");
        if (!estado.isObject()) {
            throw EnvioDeMensagemException.temporaria(
                    "Resposta de reconciliacao malformada.");
        }
        return new WebhookState(
                estado.path("url").asString(""),
                estado.path("pending_update_count").asInt(0),
                instanteOpcional(estado.path("last_error_date")),
                estado.path("allowed_updates").isArray()
                        ? json.convertValue(estado.path("allowed_updates"),
                        json.getTypeFactory().constructCollectionType(List.class, String.class))
                        : List.of());
    }

    ArquivoRemoto prepararDownload(UUID channelConnectionId, String fileId) {
        String token = token(channelConnectionId);
        TelegramTransport.Resposta resposta = chamar(
                token, "getFile", json.writeValueAsString(Map.of("file_id", fileId)));
        JsonNode arquivo = exigirSucesso(resposta).path("result");
        String caminho = arquivo.path("file_path").asString("");
        long tamanho = arquivo.path("file_size").asLong(-1);
        if (caminho.isBlank() || caminho.contains("..")
                || !caminho.matches("[A-Za-z0-9_./-]+")) {
            throw EnvioDeMensagemException.temporaria(
                    "Resposta de arquivo malformada do provedor.");
        }
        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(BASE_ARQUIVO + token + '/' + caminho))
                .timeout(TIMEOUT)
                .GET().build();
        return new ArquivoRemoto(requisicao, tamanho);
    }

    TelegramTransport.Download abrirDownload(ArquivoRemoto arquivo) {
        try {
            return transport.baixar(arquivo.requisicao());
        } catch (IOException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Falha de rede ao baixar arquivo do provedor.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw EnvioDeMensagemException.temporaria("Download interrompido.");
        }
    }

    void registrarWebhook(UUID channelConnectionId, String urlPublica, String segredo) {
        registrarWebhook(channelConnectionId, urlPublica, segredo, true);
    }

    void registrarWebhook(UUID channelConnectionId, String urlPublica, String segredo,
                           boolean descartarPendentes) {
        if (urlPublica == null || !urlPublica.startsWith("https://")) {
            throw new IllegalArgumentException("Webhook do Telegram exige URL HTTPS.");
        }
        if (segredo == null || !SEGREDO_WEBHOOK.matcher(segredo).matches()) {
            throw new IllegalArgumentException(
                    "Segredo do webhook deve usar 1-256 caracteres A-Z, a-z, 0-9, _ ou -.");
        }

        TelegramTransport.Resposta resposta = chamar(
                token(channelConnectionId), "setWebhook", json.writeValueAsString(Map.of(
                        "url", urlPublica,
                        "secret_token", segredo,
                        "drop_pending_updates", descartarPendentes,
                        "allowed_updates", List.of("message", "edited_message"))));
        exigirSucesso(resposta);
        log.info("Webhook do Telegram registrado. connectionId={} tenantId={} apiVersion={}",
                channelConnectionId, TenantContext.atual().orElse(null), versaoDoContrato);
    }

    private String token(UUID channelConnectionId) {
        return credenciais
                .recuperar(channelConnectionId, TipoCredencial.TELEGRAM_BOT_TOKEN)
                .orElseThrow(() -> EnvioDeMensagemException.permanente(
                        "Canal do Telegram sem token configurado."));
    }

    private TelegramTransport.Resposta chamar(String token, String metodo, String corpo) {
        HttpRequest requisicao = HttpRequest.newBuilder()
                // O token faz parte da URL oficial; a URI nunca e registrada em log.
                .uri(URI.create(BASE_API + token + '/' + metodo))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();
        try {
            return transport.enviar(requisicao);
        } catch (IOException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Falha de rede ao contatar o provedor.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw EnvioDeMensagemException.temporaria("Envio interrompido.");
        }
    }

    private String extrairMessageId(TelegramTransport.Resposta resposta) {
        JsonNode corpo = exigirSucesso(resposta);
        JsonNode messageId = corpo.path("result").path("message_id");
        if (!messageId.canConvertToLong()) {
            throw EnvioDeMensagemException.temporaria(
                    "Resposta do provedor sem identificador valido.");
        }
        return messageId.asString();
    }

    private JsonNode exigirSucesso(TelegramTransport.Resposta resposta) {
        JsonNode corpo = lerCorpo(resposta.corpo());
        int status = resposta.status();
        if (status == 429) {
            throw EnvioDeMensagemException.temporaria(
                    "Provedor limitou temporariamente o envio.", retryAfter(resposta, corpo));
        }
        if (status >= 500) {
            throw EnvioDeMensagemException.temporaria(
                    "Provedor indisponivel no momento.");
        }
        if (status >= 400 || !corpo.path("ok").asBoolean(false)) {
            log.warn("Telegram recusou operacao. status={}", status);
            throw EnvioDeMensagemException.permanente(
                    "Provedor recusou a operacao.");
        }
        return corpo;
    }

    private JsonNode lerCorpo(String corpo) {
        try {
            return json.readTree(corpo == null ? "" : corpo);
        } catch (RuntimeException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Resposta malformada do provedor.");
        }
    }

    private Duration retryAfter(TelegramTransport.Resposta resposta, JsonNode corpo) {
        long segundos = corpo.path("parameters").path("retry_after").asLong(0);
        if (segundos <= 0) {
            try {
                segundos = Long.parseLong(String.valueOf(resposta.primeiroHeader("Retry-After")));
            } catch (NumberFormatException ignored) {
                segundos = 1;
            }
        }
        return Duration.ofSeconds(Math.clamp(segundos, 1, 86_400));
    }

    private Instant instanteOpcional(JsonNode valor) {
        return valor.canConvertToLong() && valor.asLong() > 0
                ? Instant.ofEpochSecond(valor.asLong()) : null;
    }

    record WebhookState(String url, int pendentes, Instant ultimaFalha,
                        List<String> atualizacoesPermitidas) {
        WebhookState {
            atualizacoesPermitidas = List.copyOf(atualizacoesPermitidas);
        }
    }

    record ArquivoRemoto(HttpRequest requisicao, long tamanhoDeclarado) {
    }
}
