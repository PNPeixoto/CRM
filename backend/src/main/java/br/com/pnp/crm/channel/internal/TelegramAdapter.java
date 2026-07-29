package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ChannelAdapter;
import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Primeiro provedor externo de verdade.
 *
 * <p>É ele quem valida se a porta {@link ChannelAdapter} foi desenhada certa —
 * o chat ao vivo não tem webhook, {@code external_id} de terceiro nem mídia,
 * então não exercitava nada disso.
 */
@Component
class TelegramAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAdapter.class);

    private static final String BASE_API = "https://api.telegram.org/bot";

    /**
     * Timeout curto e explícito. Sem timeout, o cliente HTTP do Java espera
     * indefinidamente, e um provedor que aceita a conexão e não responde
     * prende a thread do worker para sempre — a fila inteira para atrás dela.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final int HTTP_MUITAS_REQUISICOES = 429;
    private static final int HTTP_ERRO_SERVIDOR = 500;

    private final HttpClient http;
    private final ObjectMapper json;
    private final CredenciaisDeCanal credenciais;

    TelegramAdapter(ObjectMapper json, CredenciaisDeCanal credenciais) {
        this.json = json;
        this.credenciais = credenciais;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public TipoCanal tipo() {
        return TipoCanal.TELEGRAM;
    }

    @Override
    public String enviar(OutboundMessage mensagem) {
        String token = credenciais
                .recuperar(mensagem.channelConnectionId(), TipoCredencial.TELEGRAM_BOT_TOKEN)
                // Falha permanente: sem token, repetir não resolve. A conexão
                // precisa ser reconfigurada por uma pessoa.
                .orElseThrow(() -> EnvioDeMensagemException.permanente(
                        "Canal do Telegram sem token configurado."));

        HttpResponse<String> resposta = chamar(token, corpoDeEnvio(mensagem));
        return extrairMessageId(resposta);
    }

    private String corpoDeEnvio(OutboundMessage mensagem) {
        // Montado pelo Jackson e não por concatenação: texto de atendente
        // contém aspas, quebra de linha e emoji, e concatenar produziria JSON
        // inválido no primeiro deles.
        return json.writeValueAsString(java.util.Map.of(
                "chat_id", mensagem.externalContactId(),
                "text", mensagem.texto() == null ? "" : mensagem.texto()));
    }

    private HttpResponse<String> chamar(String token, String corpo) {
        HttpRequest requisicao = HttpRequest.newBuilder()
                // O token vai no CAMINHO porque a API do Telegram é assim. Por
                // isso a URL nunca pode ser registrada em log — ela É a
                // credencial. Nenhum log deste arquivo inclui a URI.
                .uri(URI.create(BASE_API + token + "/sendMessage"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();

        try {
            return http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw EnvioDeMensagemException.temporaria("Falha de rede ao contatar o provedor.");
        } catch (InterruptedException e) {
            // Restaurar o flag é obrigatório: engoli-lo faz o encerramento
            // da aplicação parar de funcionar, e o sintoma aparece longe daqui.
            Thread.currentThread().interrupt();
            throw EnvioDeMensagemException.temporaria("Envio interrompido.");
        }
    }

    private String extrairMessageId(HttpResponse<String> resposta) {
        int status = resposta.statusCode();

        if (status == HTTP_MUITAS_REQUISICOES || status >= HTTP_ERRO_SERVIDOR) {
            // Limite de taxa e erro de servidor passam — o worker retenta com
            // backoff em vez de queimar a mensagem.
            throw EnvioDeMensagemException.temporaria("Provedor indisponível no momento.");
        }

        if (status >= 400) {
            // 4xx é problema do que enviamos: chat inexistente, bot bloqueado,
            // token inválido. Repetir só gastaria cota.
            log.warn("Telegram recusou o envio. status={}", status);
            throw EnvioDeMensagemException.permanente("Provedor recusou a mensagem.");
        }

        JsonNode corpo = json.readTree(resposta.body());
        JsonNode messageId = corpo.path("result").path("message_id");

        if (messageId.isMissingNode()) {
            // A descrição do erro do Telegram NÃO entra na exceção: ela pode
            // ecoar o que enviamos, e failure_reason é exibido na tela.
            throw EnvioDeMensagemException.permanente("Resposta do provedor sem identificador.");
        }

        return messageId.asString();
    }

    /**
     * Registra o webhook no Telegram. Chamado na configuração da conexão, não
     * no caminho de mensagem.
     *
     * <p>O {@code secret_token} definido aqui é o que o Telegram devolverá no
     * header a cada chamada — é a única prova de que o webhook veio dele.
     */
    void registrarWebhook(java.util.UUID channelConnectionId, String urlPublica, String segredo) {
        String token = credenciais
                .recuperar(channelConnectionId, TipoCredencial.TELEGRAM_BOT_TOKEN)
                .orElseThrow(() -> new IllegalStateException(
                        "Conexão sem token: " + channelConnectionId));

        String corpo = json.writeValueAsString(java.util.Map.of(
                "url", urlPublica,
                "secret_token", segredo,
                // Sem isto, um webhook trocado herda updates antigos
                // acumulados na fila do Telegram.
                "drop_pending_updates", true));

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(BASE_API + token + "/setWebhook"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resposta =
                    http.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resposta.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Telegram recusou o registro do webhook. status=" + resposta.statusCode());
            }
            log.info("Webhook do Telegram registrado. connectionId={} tenantId={}",
                    channelConnectionId, TenantContext.atual().orElse(null));
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao registrar webhook.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Registro de webhook interrompido.", e);
        }
    }
}
