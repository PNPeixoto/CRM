package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramAdapterContractTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CONEXAO = UUID.randomUUID();
    private static final String TOKEN = "123456:token-de-teste";

    private CredenciaisDeCanal credenciais;
    private TelegramRateLimiter rateLimiter;

    @BeforeEach
    void preparar() {
        TenantContext.definir(TENANT);
        credenciais = mock(CredenciaisDeCanal.class);
        rateLimiter = mock(TelegramRateLimiter.class);
        when(credenciais.recuperar(CONEXAO, TipoCredencial.TELEGRAM_BOT_TOKEN))
                .thenReturn(Optional.of(TOKEN));
        doNothing().when(rateLimiter).exigir(any());
    }

    @AfterEach
    void limpar() {
        TenantContext.limpar();
    }

    @Test
    void respostaOficialDeSendMessageRetornaIdentificador() {
        TelegramAdapter adapter = adapter(req -> resposta(200, """
                {"ok":true,"result":{"message_id":42,"date":1785000000,
                "chat":{"id":123,"type":"private"},"text":"ola"}}
                """));

        assertThat(adapter.enviar(mensagem())).isEqualTo("42");
    }

    @Test
    void floodControlHonraRetryAfterOficial() {
        TelegramAdapter adapter = adapter(req -> new TelegramTransport.Resposta(
                429, Map.of("Retry-After", List.of("3")),
                "{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":17}}"));

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOfSatisfying(EnvioDeMensagemException.class, erro -> {
                    assertThat(erro.isPermanente()).isFalse();
                    assertThat(erro.tentarNovamenteEm()).contains(Duration.ofSeconds(17));
                });
    }

    @Test
    void timeoutEhTemporarioESemConteudoExterno() {
        TelegramAdapter adapter = adapter(req -> {
            throw new IOException("token=" + TOKEN + " payload sensivel");
        });

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOf(EnvioDeMensagemException.class)
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining("payload sensivel");
    }

    @Test
    void respostaMalformadaEhTemporaria() {
        TelegramAdapter adapter = adapter(req -> resposta(200, "nao-json"));

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOfSatisfying(EnvioDeMensagemException.class,
                        erro -> assertThat(erro.isPermanente()).isFalse());
    }

    @Test
    void getWebhookInfoEhNormalizadoSemPersistirMensagemDeErroRemota() {
        TelegramAdapter adapter = adapter(req -> resposta(200, """
                {"ok":true,"result":{"url":"https://crm.example/webhook",
                "pending_update_count":4,"last_error_date":1785000000,
                "last_error_message":"conteudo externo nao confiavel",
                "allowed_updates":["message","edited_message"]}}
                """));

        TelegramAdapter.WebhookState estado = adapter.obterEstadoWebhook(CONEXAO);
        assertThat(estado.url()).isEqualTo("https://crm.example/webhook");
        assertThat(estado.pendentes()).isEqualTo(4);
        assertThat(estado.ultimaFalha()).isEqualTo(Instant.ofEpochSecond(1785000000));
        assertThat(estado.toString()).doesNotContain("conteudo externo nao confiavel");
    }

    @Test
    void segredoForaDoAlfabetoOficialEhRecusadoAntesDaRede() {
        TelegramAdapter adapter = adapter(req -> {
            throw new AssertionError("nao deveria chamar a rede");
        });

        assertThatThrownBy(() -> adapter.registrarWebhook(
                CONEXAO, "https://crm.example/webhook", "segredo com espaco"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TelegramAdapter adapter(Comportamento comportamento) {
        TelegramTransport transport = requisicao -> comportamento.executar(requisicao);
        return new TelegramAdapter(
                new ObjectMapper(), credenciais, transport, rateLimiter, "10.2");
    }

    private TelegramTransport.Resposta resposta(int status, String corpo) {
        return new TelegramTransport.Resposta(status, Map.of(), corpo);
    }

    private OutboundMessage mensagem() {
        return new OutboundMessage(
                UUID.randomUUID(), CONEXAO, "123", TipoConteudo.TEXT, "ola");
    }

    @FunctionalInterface
    private interface Comportamento {
        TelegramTransport.Resposta executar(HttpRequest requisicao)
                throws IOException, InterruptedException;
    }
}
