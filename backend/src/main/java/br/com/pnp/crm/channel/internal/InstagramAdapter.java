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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Adaptador oficial da Instagram Messaging API com Instagram Login. */
@Component
@ConditionalOnProperty(name = "app.providers.meta.enabled", havingValue = "true")
class InstagramAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(InstagramAdapter.class);
    private static final String BASE_API = "https://graph.instagram.com/";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern ID_CONTA = Pattern.compile("[0-9]{1,40}");
    private static final Set<String> CAMPOS_WEBHOOK = Set.of("messages");

    private final ObjectMapper json;
    private final CredenciaisDeCanal credenciais;
    private final ChannelConnectionRepository conexoes;
    private final MetaTransport transport;
    private final String versaoDaApi;

    InstagramAdapter(ObjectMapper json, CredenciaisDeCanal credenciais,
                     ChannelConnectionRepository conexoes, MetaTransport transport,
                     @Value("${app.providers.meta.graph-api-version}") String versaoDaApi) {
        this.json = json;
        this.credenciais = credenciais;
        this.conexoes = conexoes;
        this.transport = transport;
        this.versaoDaApi = versaoDaApi;
        if (!"v24.0".equals(versaoDaApi)) {
            throw new IllegalStateException(
                    "Contrato Instagram nao revisado. Esperada Graph API v24.0.");
        }
    }

    @Override
    public TipoCanal tipo() {
        return TipoCanal.INSTAGRAM;
    }

    @Override
    public String enviar(OutboundMessage mensagem) {
        if (mensagem.tipoConteudo() != TipoConteudo.TEXT
                || mensagem.texto() == null || mensagem.texto().isBlank()) {
            throw EnvioDeMensagemException.permanente(
                    "O canal Instagram envia somente mensagens de texto nesta versao.");
        }
        String corpo = json.writeValueAsString(Map.of(
                "recipient", Map.of("id", mensagem.externalContactId()),
                "message", Map.of("text", mensagem.texto())));
        JsonNode resposta = exigirSucesso(chamar(
                mensagem.channelConnectionId(), "POST",
                "/" + conta(mensagem.channelConnectionId()) + "/messages", corpo));
        String messageId = resposta.path("message_id").asString("");
        if (messageId.isBlank()) {
            throw EnvioDeMensagemException.temporaria(
                    "Instagram respondeu sem identificador de mensagem.");
        }
        return messageId;
    }

    Set<String> obterAssinaturas(UUID connectionId) {
        JsonNode resposta = exigirSucesso(chamar(
                connectionId, "GET", "/" + conta(connectionId) + "/subscribed_apps", null));
        JsonNode dados = resposta.path("data");
        if (!dados.isArray()) {
            throw EnvioDeMensagemException.temporaria(
                    "Resposta de reconciliacao Instagram malformada.");
        }
        Set<String> campos = new LinkedHashSet<>();
        dados.forEach(item -> item.path("subscribed_fields").forEach(campo -> {
            if (campo.isTextual()) campos.add(campo.asString());
        }));
        return Set.copyOf(campos);
    }

    void registrarAssinaturas(UUID connectionId) {
        JsonNode resposta = exigirSucesso(chamar(
                connectionId, "POST",
                "/" + conta(connectionId)
                        + "/subscribed_apps?subscribed_fields=" + String.join(",", CAMPOS_WEBHOOK),
                ""));
        if (!resposta.path("success").asBoolean(false)) {
            throw EnvioDeMensagemException.temporaria(
                    "Instagram nao confirmou a assinatura do webhook.");
        }
        log.info("Webhook Instagram assinado. connectionId={} tenantId={} apiVersion={}",
                connectionId, TenantContext.atual().orElse(null), versaoDaApi);
    }

    private MetaTransport.Resposta chamar(UUID connectionId, String metodo,
                                          String caminho, String corpo) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_API + versaoDaApi + caminho))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token(connectionId))
                .header("Content-Type", "application/json; charset=utf-8");
        HttpRequest requisicao = corpo == null
                ? builder.GET().build()
                : builder.method(metodo, HttpRequest.BodyPublishers.ofString(
                        corpo, StandardCharsets.UTF_8)).build();
        try {
            return transport.enviar(requisicao);
        } catch (IOException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Falha de rede ao contatar a Instagram API.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw EnvioDeMensagemException.temporaria("Envio Instagram interrompido.");
        }
    }

    private JsonNode exigirSucesso(MetaTransport.Resposta resposta) {
        int status = resposta.status();
        if (status == 429) {
            throw EnvioDeMensagemException.temporaria(
                    "Instagram limitou temporariamente a operacao.", retryAfter(resposta));
        }
        if (status >= 500 || status == 408 || status == 409 || status == 423) {
            throw EnvioDeMensagemException.temporaria(
                    "Instagram esta temporariamente indisponivel.");
        }
        if (status >= 400) {
            log.warn("Instagram recusou operacao. status={}", status);
            throw EnvioDeMensagemException.permanente(
                    "Instagram recusou a operacao.");
        }
        try {
            return json.readTree(resposta.corpo() == null ? "" : resposta.corpo());
        } catch (RuntimeException e) {
            throw EnvioDeMensagemException.temporaria(
                    "Instagram devolveu resposta malformada.");
        }
    }

    private String conta(UUID connectionId) {
        ChannelConnectionEntity conexao = conexoes
                .findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(
                        connectionId, TenantContext.obrigatorio())
                .filter(c -> c.getKind() == TipoCanal.INSTAGRAM)
                .orElseThrow(() -> EnvioDeMensagemException.permanente(
                        "Canal Instagram inexistente ou inativo."));
        String conta = conexao.getExternalAccountId();
        if (conta == null || !ID_CONTA.matcher(conta).matches()) {
            throw EnvioDeMensagemException.permanente(
                    "Canal Instagram sem ID profissional valido.");
        }
        return conta;
    }

    private String token(UUID connectionId) {
        return credenciais.recuperar(connectionId, TipoCredencial.META_ACCESS_TOKEN)
                .orElseThrow(() -> EnvioDeMensagemException.permanente(
                        "Canal Instagram sem access token configurado."));
    }

    private Duration retryAfter(MetaTransport.Resposta resposta) {
        try {
            return Duration.ofSeconds(Math.clamp(
                    Long.parseLong(String.valueOf(resposta.primeiroHeader("Retry-After"))),
                    1, 86_400));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(1);
        }
    }
}
