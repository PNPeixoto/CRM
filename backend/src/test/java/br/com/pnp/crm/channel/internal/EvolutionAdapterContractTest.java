package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvolutionAdapterContractTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CONEXAO = UUID.randomUUID();
    private static final String API_KEY = "api-key-de-teste";

    private ChannelConnectionRepository conexoes;

    @BeforeEach
    void preparar() {
        TenantContext.definir(TENANT);
        conexoes = mock(ChannelConnectionRepository.class);
        ChannelConnectionEntity conexao = ChannelConnectionEntity.nova(
                TENANT, TipoCanal.WHATSAPP_EVOLUTION, "WhatsApp", "pnp-teste",
                UUID.randomUUID());
        when(conexoes.findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(CONEXAO, TENANT))
                .thenReturn(Optional.of(conexao));
    }

    @AfterEach
    void limpar() {
        TenantContext.limpar();
    }

    @Test
    void envioUsaContratoFixadoDaVersao237ERetornaId() {
        AtomicReference<HttpRequest> capturada = new AtomicReference<>();
        EvolutionAdapter adapter = adapter(req -> {
            capturada.set(req);
            return resposta(200, "{\"key\":{\"id\":\"EVO-42\"},\"status\":\"PENDING\"}");
        });

        assertThat(adapter.enviar(mensagem())).isEqualTo("EVO-42");
        assertThat(capturada.get().uri().toString())
                .isEqualTo("http://evolution:8080/message/sendText/pnp-teste");
        assertThat(capturada.get().headers().firstValue("apikey")).contains(API_KEY);
        assertThat(capturada.get().headers().firstValue("Origin"))
                .contains("http://localhost:8081");
    }

    @Test
    void webhookUsaEnvelopeExigidoPelaVersao237() {
        AtomicReference<HttpRequest> capturada = new AtomicReference<>();
        EvolutionAdapter adapter = adapter(req -> {
            capturada.set(req);
            return resposta(200, "{}");
        });

        adapter.registrarWebhook(CONEXAO,
                "http://app:8080/api/webhooks/evolution/canal", "segredo");

        var corpo = new ObjectMapper().readTree(corpoDa(capturada.get()));
        assertThat(capturada.get().uri().toString())
                .isEqualTo("http://evolution:8080/webhook/set/pnp-teste");
        assertThat(corpo.path("webhook").path("enabled").asBoolean()).isTrue();
        assertThat(corpo.path("webhook").path("url").asString())
                .isEqualTo("http://app:8080/api/webhooks/evolution/canal");
        assertThat(corpo.path("webhook").path("events").get(0).asString())
                .isEqualTo("MESSAGES_UPSERT");
        assertThat(corpo.path("webhook").path("headers")
                .path(EvolutionWebhookController.HEADER_SEGREDO).asString())
                .isEqualTo("segredo");
    }

    @Test
    void instanciaExistenteNaoEhRecriada() {
        List<String> chamadas = new java.util.ArrayList<>();
        EvolutionAdapter adapter = adapter(req -> {
            chamadas.add(req.method() + ' ' + req.uri().getPath());
            return resposta(200, "{\"instance\":{\"state\":\"connecting\"}}");
        });

        adapter.garantirInstancia(CONEXAO);

        // Recriar apagaria a sessao pareada de quem ja estava atendendo.
        assertThat(chamadas).containsExactly("GET /instance/connectionState/pnp-teste");
    }

    @Test
    void instanciaAusenteEhCriadaComOContratoDaVersao237() {
        List<String> chamadas = new java.util.ArrayList<>();
        AtomicReference<HttpRequest> criacao = new AtomicReference<>();
        EvolutionAdapter adapter = adapter(req -> {
            chamadas.add(req.method() + ' ' + req.uri().getPath());
            if (req.uri().getPath().startsWith("/instance/connectionState")) {
                return resposta(404, "{\"status\":404}");
            }
            criacao.set(req);
            return resposta(201, "{\"instance\":{\"instanceName\":\"pnp-teste\"}}");
        });

        adapter.garantirInstancia(CONEXAO);

        assertThat(chamadas).containsExactly(
                "GET /instance/connectionState/pnp-teste", "POST /instance/create");
        var corpo = new ObjectMapper().readTree(corpoDa(criacao.get()));
        assertThat(corpo.path("instanceName").asString()).isEqualTo("pnp-teste");
        assertThat(corpo.path("integration").asString()).isEqualTo("WHATSAPP-BAILEYS");
        // O QR vem do /instance/connect; pedi-lo aqui o colocaria numa resposta
        // que ninguem esta pronto para tratar como credencial.
        assertThat(corpo.path("qrcode").asBoolean()).isFalse();
    }

    @Test
    void pareamentoNaoExpoeConteudoAoSerImpresso() {
        EvolutionAdapter adapter = adapter(req -> resposta(200, """
                {"base64":"data:image/png;base64,SEGREDO-DO-QR",
                 "pairingCode":"CODIGO-SECRETO","count":2}"""));

        EvolutionAdapter.Pareamento pareamento = adapter.iniciarPareamento(CONEXAO);

        assertThat(pareamento.qrCodeBase64()).contains("SEGREDO-DO-QR");
        assertThat(pareamento.codigoDeParear()).isEqualTo("CODIGO-SECRETO");
        assertThat(pareamento.tentativa()).isEqualTo(2);
        // O material pareia uma sessao de WhatsApp. Qualquer log que interpole
        // o objeto — e eles interpolam — nao pode revelar isso.
        assertThat(pareamento.toString())
                .doesNotContain("SEGREDO-DO-QR")
                .doesNotContain("CODIGO-SECRETO")
                .contains("tentativa=2");
    }

    @Test
    void limiteDoProvedorEhTemporarioEHonraRetryAfter() {
        EvolutionAdapter adapter = adapter(req -> new EvolutionTransport.Resposta(
                429, Map.of("Retry-After", List.of("9")), "{}"));

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOfSatisfying(EnvioDeMensagemException.class, erro -> {
                    assertThat(erro.isPermanente()).isFalse();
                    assertThat(erro.tentarNovamenteEm()).contains(Duration.ofSeconds(9));
                });
    }

    @Test
    void erroDeRedeNaoVazaApiKeyNemConteudo() {
        EvolutionAdapter adapter = adapter(req -> {
            throw new IOException("apikey=" + API_KEY + " conteudo sensivel");
        });

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOf(EnvioDeMensagemException.class)
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("conteudo sensivel");
    }

    private EvolutionAdapter adapter(Comportamento comportamento) {
        EvolutionTransport transport = requisicao -> comportamento.executar(requisicao);
        return new EvolutionAdapter(
                new ObjectMapper(), transport, conexoes,
                "http://evolution:8080", API_KEY, "http://localhost:8081");
    }

    private EvolutionTransport.Resposta resposta(int status, String corpo) {
        return new EvolutionTransport.Resposta(status, Map.of(), corpo);
    }

    private OutboundMessage mensagem() {
        return new OutboundMessage(
                UUID.randomUUID(), CONEXAO, "5511999999999",
                TipoConteudo.TEXT, "Ola pelo CRM");
    }

    private String corpoDa(HttpRequest requisicao) {
        var bytes = new ByteArrayOutputStream();
        var terminou = new CompletableFuture<Void>();
        requisicao.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] trecho = new byte[item.remaining()];
                item.get(trecho);
                bytes.writeBytes(trecho);
            }

            @Override
            public void onError(Throwable throwable) {
                terminou.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                terminou.complete(null);
            }
        });
        terminou.join();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface Comportamento {
        EvolutionTransport.Resposta executar(HttpRequest requisicao)
                throws IOException, InterruptedException;
    }
}
