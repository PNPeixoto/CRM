package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.AutorizacaoDeEscuta;
import br.com.pnp.crm.shared.api.TopicosTempoReal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Autorização de inscrição em tópico.
 *
 * <p>Sem Spring: o que se mede é a decisão do interceptador, e subir contexto
 * para isso trocaria um teste de um segundo por um de vinte sem responder nada
 * a mais.
 */
class EscutaDeTopicoTest {

    private static final UUID TENANT = UUID.fromString("019fa91c-0f63-75f7-b4a0-1494c1304c42");
    private static final UUID VIZINHO = UUID.fromString("019fa91c-0f66-7d34-9a39-2f79328d02c9");
    private static final UUID USUARIO = UUID.fromString("019fa91c-0f66-7a68-8384-20e85b6c8f5a");

    private final List<UUID> consultados = new ArrayList<>();

    @Test
    void inscricaoNoProprioTenantExigePermissaoDeLeituraDeConversa() {
        StompAuthChannelInterceptor interceptor = interceptor(true);

        assertThat(interceptor.preSend(inscricao(TopicosTempoReal.inbox(TENANT)), null))
                .isNotNull();
        assertThat(consultados).containsExactly(USUARIO);
    }

    @Test
    void inscricaoSemPermissaoEhRecusadaMesmoNoProprioTenant() {
        StompAuthChannelInterceptor interceptor = interceptor(false);

        // O tenant confere: o que falta é permissão. Antes desta checagem, esta
        // inscrição era aceita e entregava mensagem de cliente em tempo real a
        // quem o REST recusava.
        assertThatThrownBy(() ->
                interceptor.preSend(inscricao(TopicosTempoReal.inbox(TENANT)), null))
                .isInstanceOf(StompNaoAutorizadoException.class);
    }

    @Test
    void inscricaoNoTopicoDoVizinhoNemChegaAConsultarPermissao() {
        StompAuthChannelInterceptor interceptor = interceptor(true);

        assertThatThrownBy(() ->
                interceptor.preSend(inscricao(TopicosTempoReal.inbox(VIZINHO)), null))
                .isInstanceOf(StompNaoAutorizadoException.class);
        assertThat(consultados).isEmpty();
    }

    @Test
    void inscricaoSemSessaoAutenticadaEhRecusada() {
        StompAuthChannelInterceptor interceptor = interceptor(true);
        StompHeaderAccessor acessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acessor.setDestination(TopicosTempoReal.inbox(TENANT));

        assertThatThrownBy(() -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], acessor.getMessageHeaders()), null))
                .isInstanceOf(StompNaoAutorizadoException.class);
    }

    @Test
    void destinoDesconhecidoDoMesmoTenantEhRecusadoSemConsultarPermissao() {
        StompAuthChannelInterceptor interceptor = interceptor(true);

        assertThatThrownBy(() -> interceptor.preSend(
                inscricao("/topic/tenant/" + TENANT + "/administracao"), null))
                .isInstanceOf(StompNaoAutorizadoException.class);
        assertThat(consultados).isEmpty();
    }

    private StompAuthChannelInterceptor interceptor(boolean autorizado) {
        JwtDecoder decoder = token -> {
            throw new UnsupportedOperationException("CONNECT não é o objeto deste teste");
        };
        AutorizacaoDeEscuta autorizacao = (tenantId, usuarioId) -> {
            consultados.add(usuarioId);
            if (!autorizado) {
                throw new AcessoNegadoException();
            }
        };
        return new StompAuthChannelInterceptor(decoder, autorizacao);
    }

    private Message<?> inscricao(String destino) {
        StompHeaderAccessor acessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acessor.setDestination(destino);
        acessor.setUser(new JwtAuthenticationToken(jwt(), List.of()));
        return MessageBuilder.createMessage(new byte[0], acessor.getMessageHeaders());
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("irrelevante")
                .header("alg", "HS256")
                .subject(USUARIO.toString())
                .claim("tid", TENANT.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }
}
