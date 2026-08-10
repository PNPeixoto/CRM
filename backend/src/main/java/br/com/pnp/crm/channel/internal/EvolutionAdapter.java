package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ChannelAdapter;
import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ponte experimental da Evolution API para desenvolvimento.
 *
 * <p>Nao substitui o adaptador oficial {@link TipoCanal#WHATSAPP_CLOUD} e so
 * nasce quando {@code app.providers.evolution.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.providers.evolution.enabled", havingValue = "true")
class EvolutionAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(EvolutionAdapter.class);
    private static final Pattern INSTANCIA = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper json;
    private final EvolutionTransport transport;
    private final ChannelConnectionRepository conexoes;
    private final String baseUrl;
    private final String apiKey;
    private final String origin;

    EvolutionAdapter(ObjectMapper json, EvolutionTransport transport,
                     ChannelConnectionRepository conexoes,
                     @Value("${app.providers.evolution.base-url:http://evolution-api:8080}")
                     String baseUrl,
                     @Value("${app.providers.evolution.api-key:}") String apiKey,
                     @Value("${app.providers.evolution.origin:http://localhost:8081}")
                     String origin) {
        this.json = json;
        this.transport = transport;
        this.conexoes = conexoes;
        this.baseUrl = removerBarraFinal(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.origin = removerBarraFinal(origin);
    }

    @Override
    public TipoCanal tipo() {
        return TipoCanal.WHATSAPP_EVOLUTION;
    }

    @Override
    public String enviar(OutboundMessage mensagem) {
        if (mensagem.tipoConteudo() != TipoConteudo.TEXT
                || mensagem.texto() == null || mensagem.texto().isBlank()) {
            throw EnvioDeMensagemException.permanente(
                    "O adaptador Evolution experimental envia somente texto.");
        }
        String instancia = instancia(mensagem.channelConnectionId());
        String corpo = json.writeValueAsString(Map.of(
                "number", destinatario(mensagem.externalContactId()),
                "text", mensagem.texto()));
        EvolutionTransport.Resposta resposta = chamar(
                "POST", "/message/sendText/" + instancia, corpo);
        JsonNode raiz = exigirSucesso(resposta);
        String externalId = raiz.path("key").path("id").asString("");
        if (externalId.isBlank()) {
            throw EnvioDeMensagemException.temporaria(
                    "Evolution respondeu sem identificador de mensagem.");
        }
        return externalId;
    }

    void registrarWebhook(UUID connectionId, String url, String segredo) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("URL do webhook Evolution invalida.");
        }
        // A 2.3.7 exige o envelope "webhook". Manter o contrato explicito
        // evita adotar silenciosamente o formato plano das versoes seguintes.
        String corpo = json.writeValueAsString(Map.of("webhook", Map.of(
                "enabled", true,
                "url", url,
                "events", java.util.List.of("MESSAGES_UPSERT"),
                "headers", Map.of(EvolutionWebhookController.HEADER_SEGREDO, segredo),
                "base64", false)));
        exigirSucesso(chamar("POST", "/webhook/set/" + instancia(connectionId), corpo));
    }

    /**
     * Cria a instancia se ela ainda nao existir, e nao falha quando ja existe.
     *
     * <p>Ate aqui o CRM assumia que alguem havia criado a instancia por fora.
     * Quem seguisse o runbook esperava uma instancia que nunca aparecia, e a
     * reconciliacao ficava marcando ERRO contra um nome inexistente.
     *
     * <p>A criacao vive no pareamento, e nao no worker de reconciliacao, de
     * proposito: criar sessao de WhatsApp e ato deliberado de uma pessoa. Um
     * worker que cria sozinho recriaria a instancia toda vez que alguem
     * apagasse, sem ninguem pedir.
     */
    void garantirInstancia(UUID connectionId) {
        String instancia = instancia(connectionId);
        EvolutionTransport.Resposta existente = chamar(
                "GET", "/instance/connectionState/" + instancia, null);
        if (existente.status() != 404) {
            exigirSucesso(existente);
            return;
        }
        // `qrcode:false` porque o QR vem do /instance/connect, em uma resposta
        // que o servidor nao guarda. Pedi-lo aqui o colocaria na resposta da
        // criacao sem que ninguem esteja pronto para ler.
        String corpo = json.writeValueAsString(Map.of(
                "instanceName", instancia,
                "integration", "WHATSAPP-BAILEYS",
                "qrcode", false));
        exigirSucesso(chamar("POST", "/instance/create", corpo));
    }

    /**
     * Devolve o material de pareamento da instancia.
     *
     * <p><b>Nunca registrar o retorno.</b> O QR e o codigo pareiam uma sessao
     * de WhatsApp: quem os obtiver passa a enviar mensagem como aquele numero.
     * Eles nao entram em log, em auditoria, em metrica nem em mensagem de erro.
     */
    Pareamento iniciarPareamento(UUID connectionId) {
        String instancia = instancia(connectionId);
        JsonNode raiz = exigirSucesso(chamar(
                "GET", "/instance/connect/" + instancia, null));
        return new Pareamento(
                raiz.path("base64").asString(""),
                raiz.path("pairingCode").asString(""),
                raiz.path("count").asInt(0));
    }

    EstadoRemoto obterEstado(UUID connectionId) {
        String instancia = instancia(connectionId);
        JsonNode conexao = exigirSucesso(chamar(
                "GET", "/instance/connectionState/" + instancia, null));
        JsonNode webhook = exigirSucesso(chamar(
                "GET", "/webhook/find/" + instancia, null));
        return new EstadoRemoto(
                conexao.path("instance").path("state").asString("unknown"),
                webhook.path("enabled").asBoolean(false),
                webhook.path("url").asString(""));
    }

    private String instancia(UUID connectionId) {
        ChannelConnectionEntity conexao = conexoes
                .findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(
                        connectionId, TenantContext.obrigatorio())
                .orElseThrow(() -> EnvioDeMensagemException.permanente(
                        "Canal Evolution inexistente ou inativo."));
        String instancia = conexao.getExternalAccountId();
        if (instancia == null || !INSTANCIA.matcher(instancia).matches()) {
            throw EnvioDeMensagemException.permanente(
                    "Canal Evolution sem nome de instancia valido.");
        }
        return instancia;
    }

    private String destinatario(String valor) {
        String normalizado = valor == null ? "" : valor.trim();
        if (normalizado.endsWith("@s.whatsapp.net")) {
            normalizado = normalizado.substring(0,
                    normalizado.length() - "@s.whatsapp.net".length());
        }
        if (normalizado.isBlank()) {
            throw EnvioDeMensagemException.permanente("Destinatario WhatsApp invalido.");
        }
        return normalizado;
    }

    private EvolutionTransport.Resposta chamar(String metodo, String caminho, String corpo) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw EnvioDeMensagemException.permanente(
                    "Evolution API nao esta configurada no servidor.");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + caminho))
                .timeout(TIMEOUT)
                .header("apikey", apiKey)
                .header("Content-Type", "application/json; charset=utf-8");
        if (!origin.isBlank()) {
            // A Evolution 2.3.7 aplica CORS tambem a chamadas servidor-servidor.
            builder.header("Origin", origin);
        }
        HttpRequest requisicao = corpo == null
                ? builder.GET().build()
                : builder.method(metodo, HttpRequest.BodyPublishers.ofString(
                        corpo, StandardCharsets.UTF_8)).build();
        try {
            return transport.enviar(requisicao);
        } catch (IOException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Falha de rede ao contatar a Evolution API.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw EnvioDeMensagemException.temporaria("Envio Evolution interrompido.");
        }
    }

    private JsonNode exigirSucesso(EvolutionTransport.Resposta resposta) {
        int status = resposta.status();
        if (status == 429) {
            throw EnvioDeMensagemException.temporaria(
                    "Evolution limitou temporariamente a operacao.", retryAfter(resposta));
        }
        if (status >= 500 || status == 408 || status == 409 || status == 423) {
            throw EnvioDeMensagemException.temporaria(
                    "Evolution esta temporariamente indisponivel.");
        }
        if (status >= 400) {
            log.warn("Evolution recusou operacao. status={}", status);
            throw EnvioDeMensagemException.permanente(
                    "Evolution recusou a operacao.");
        }
        try {
            return json.readTree(resposta.corpo() == null ? "" : resposta.corpo());
        } catch (RuntimeException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Evolution devolveu resposta malformada.");
        }
    }

    private Duration retryAfter(EvolutionTransport.Resposta resposta) {
        try {
            return Duration.ofSeconds(Math.clamp(
                    Long.parseLong(String.valueOf(resposta.primeiroHeader("Retry-After"))),
                    1, 86_400));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(1);
        }
    }

    private static String removerBarraFinal(String valor) {
        if (valor == null) return "";
        String limpo = valor.trim();
        return limpo.endsWith("/") ? limpo.substring(0, limpo.length() - 1) : limpo;
    }

    record EstadoRemoto(String estadoDaConexao, boolean webhookAtivo, String webhookUrl) {
    }

    /**
     * Material de pareamento. Nao implementa {@code toString} proprio de
     * propriedade: o record padrao imprimiria o QR inteiro em qualquer log de
     * diagnostico que interpolasse o objeto.
     */
    record Pareamento(String qrCodeBase64, String codigoDeParear, int tentativa) {
        @Override
        public String toString() {
            return "Pareamento[conteudo omitido, tentativa=" + tentativa + ']';
        }
    }
}
