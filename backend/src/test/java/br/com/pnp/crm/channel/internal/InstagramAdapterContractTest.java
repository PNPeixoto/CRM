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

import java.io.IOException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstagramAdapterContractTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CONEXAO = UUID.randomUUID();
    private static final String CONTA = "178900000000001";
    private static final String TOKEN = "IGAA-token-secreto";

    private CredenciaisDeCanal credenciais;
    private ChannelConnectionRepository conexoes;

    @BeforeEach
    void preparar() {
        TenantContext.definir(TENANT);
        credenciais = mock(CredenciaisDeCanal.class);
        conexoes = mock(ChannelConnectionRepository.class);
        ChannelConnectionEntity conexao = mock(ChannelConnectionEntity.class);
        when(conexao.getKind()).thenReturn(TipoCanal.INSTAGRAM);
        when(conexao.getExternalAccountId()).thenReturn(CONTA);
        when(conexoes.findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(CONEXAO, TENANT))
                .thenReturn(Optional.of(conexao));
        when(credenciais.recuperar(CONEXAO, TipoCredencial.META_ACCESS_TOKEN))
                .thenReturn(Optional.of(TOKEN));
    }

    @AfterEach
    void limpar() {
        TenantContext.limpar();
    }

    @Test
    void enviaTextoNoContratoOficialSemTokenNaUrl() {
        AtomicReference<HttpRequest> capturada = new AtomicReference<>();
        InstagramAdapter adapter = adapter(req -> {
            capturada.set(req);
            return resposta(200, """
                    {"recipient_id":"IGSID-1","message_id":"MID-1"}
                    """);
        });

        assertThat(adapter.enviar(mensagem())).isEqualTo("MID-1");
        assertThat(capturada.get().uri().toString())
                .isEqualTo("https://graph.instagram.com/v24.0/" + CONTA + "/messages")
                .doesNotContain(TOKEN);
        assertThat(capturada.get().headers().firstValue("Authorization"))
                .contains("Bearer " + TOKEN);
    }

    @Test
    void limiteDaMetaEhTemporarioEHonraRetryAfter() {
        InstagramAdapter adapter = adapter(req -> new MetaTransport.Resposta(
                429, Map.of("Retry-After", List.of("9")), "{}"));

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOfSatisfying(EnvioDeMensagemException.class, erro -> {
                    assertThat(erro.isPermanente()).isFalse();
                    assertThat(erro.tentarNovamenteEm()).contains(Duration.ofSeconds(9));
                });
    }

    @Test
    void recusaQuatrocentosSemPersistirRespostaOuToken() {
        InstagramAdapter adapter = adapter(req -> resposta(
                400, "{\"error\":{\"message\":\"token=" + TOKEN + "\"}}"));

        assertThatThrownBy(() -> adapter.enviar(mensagem()))
                .isInstanceOfSatisfying(EnvioDeMensagemException.class, erro -> {
                    assertThat(erro.isPermanente()).isTrue();
                    assertThat(erro.getMessage()).doesNotContain(TOKEN);
                });
    }

    @Test
    void reconciliaSomenteAssinaturaDeMensagens() {
        AtomicReference<HttpRequest> capturada = new AtomicReference<>();
        InstagramAdapter leitura = adapter(req -> resposta(200, """
                {"data":[{"id":"app-1","subscribed_fields":["messages","message_reactions"]}]}
                """));
        assertThat(leitura.obterAssinaturas(CONEXAO))
                .containsExactlyInAnyOrder("messages", "message_reactions");

        InstagramAdapter escrita = adapter(req -> {
            capturada.set(req);
            return resposta(200, "{\"success\":true}");
        });
        escrita.registrarAssinaturas(CONEXAO);
        assertThat(capturada.get().uri().getQuery()).isEqualTo("subscribed_fields=messages");
    }

    @Test
    void conteudoNaoTextualFalhaAntesDaRede() {
        InstagramAdapter adapter = adapter(req -> {
            throw new AssertionError("nao deveria chamar a rede");
        });
        var imagem = new OutboundMessage(
                UUID.randomUUID(), CONEXAO, "IGSID-1", TipoConteudo.IMAGE, null);

        assertThatThrownBy(() -> adapter.enviar(imagem))
                .isInstanceOfSatisfying(EnvioDeMensagemException.class,
                        erro -> assertThat(erro.isPermanente()).isTrue());
    }

    private InstagramAdapter adapter(Comportamento comportamento) {
        MetaTransport transport = requisicao -> comportamento.executar(requisicao);
        return new InstagramAdapter(
                new ObjectMapper(), credenciais, conexoes, transport, "v24.0");
    }

    private MetaTransport.Resposta resposta(int status, String corpo) {
        return new MetaTransport.Resposta(status, Map.of(), corpo);
    }

    private OutboundMessage mensagem() {
        return new OutboundMessage(
                UUID.randomUUID(), CONEXAO, "IGSID-1", TipoConteudo.TEXT, "Ola");
    }

    @FunctionalInterface
    private interface Comportamento {
        MetaTransport.Resposta executar(HttpRequest requisicao)
                throws IOException, InterruptedException;
    }
}
