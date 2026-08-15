package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.ChannelAdapter;
import br.com.pnp.crm.channel.api.ChannelConnectionLookup;
import br.com.pnp.crm.channel.api.ConexaoDeCanal;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.conversation.api.StatusMensagem;
import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvioDeMensagemServiceTest {

    private static final UUID TENANT = UUID.fromString("019fbace-0000-7000-8000-000000000001");
    private static final UUID CONVERSA = UUID.fromString("019fbace-0000-7000-8000-000000000002");
    private static final UUID CANAL = UUID.fromString("019fbace-0000-7000-8000-000000000003");
    private static final UUID AUTOR = UUID.fromString("019fbace-0000-7000-8000-000000000004");

    @Mock
    private MessageRepository mensagens;
    @Mock
    private ChannelConnectionLookup conexoes;
    @Mock
    private ChannelAdapter instagram;
    @Mock
    private EventoTempoRealService eventos;
    @Mock
    private SlaDeConversaService sla;

    private EnvioDeMensagemService service;

    @BeforeEach
    void preparar() {
        when(instagram.tipo()).thenReturn(TipoCanal.INSTAGRAM);
        service = new EnvioDeMensagemService(
                mensagens, conexoes, List.of(instagram), eventos, sla);
    }

    @Test
    void encerraSemChamarAMetaQuandoAJanelaDe24HorasExpirou() {
        MessageEntity mensagem = mensagemPendente();
        prepararConexao(mensagem);
        when(mensagens.buscarUltimoRecebimentoDaConversa(TENANT, CONVERSA))
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(25))));

        executarComoTenant(() -> service.entregar(mensagem.getId()));

        assertThat(mensagem.getStatus()).isEqualTo(StatusMensagem.FAILED);
        assertThat(mensagem.getFailureReason()).contains("janela de 24 horas");
        verify(instagram, never()).enviar(any());
        verify(mensagens).flush();
        verify(eventos).registrar(
                eq("MESSAGE_STATUS_CHANGED"), eq(CONVERSA), eq(mensagem.getId()),
                eq(0L), any(Instant.class));
    }

    @Test
    void enviaQuandoOContatoFalouNasUltimas24Horas() {
        MessageEntity mensagem = mensagemPendente();
        prepararConexao(mensagem);
        when(mensagens.buscarUltimoRecebimentoDaConversa(TENANT, CONVERSA))
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofHours(23))));
        when(mensagens.buscarExternalContactIdDaConversa(TENANT, CONVERSA))
                .thenReturn(Optional.of("ig-contact-1"));
        when(instagram.enviar(any())).thenReturn("mid.123");

        executarComoTenant(() -> service.entregar(mensagem.getId()));

        assertThat(mensagem.getStatus()).isEqualTo(StatusMensagem.SENT);
        ArgumentCaptor<OutboundMessage> enviada = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(instagram).enviar(enviada.capture());
        assertThat(enviada.getValue().externalContactId()).isEqualTo("ig-contact-1");
        verify(sla).satisfazer(eq(CONVERSA), any(Instant.class));
    }

    private MessageEntity mensagemPendente() {
        return MessageEntity.paraEnvio(
                TENANT, CONVERSA, CANAL, TipoConteudo.TEXT,
                "Olá", AUTOR, "idempotency-key");
    }

    private void prepararConexao(MessageEntity mensagem) {
        when(mensagens.findByIdAndTenantIdAndDeletedAtIsNull(mensagem.getId(), TENANT))
                .thenReturn(Optional.of(mensagem));
        when(conexoes.buscarAtiva(CANAL)).thenReturn(Optional.of(
                new ConexaoDeCanal(CANAL, TENANT, TipoCanal.INSTAGRAM,
                        "Instagram PNP", "17841400000000000")));
    }

    private void executarComoTenant(Runnable acao) {
        TenantContext.executarComo(TENANT, () -> {
            acao.run();
            return null;
        });
    }
}
