package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.AutorizacaoDeEscuta;
import br.com.pnp.crm.shared.api.TopicosTempoReal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

/**
 * Autentica no CONNECT e autoriza cada SUBSCRIBE.
 *
 * <p><b>Por que o token vem no frame STOMP e não na URL:</b> a API
 * {@code WebSocket} do navegador não permite definir cabeçalhos HTTP no
 * handshake, então a saída comum é pendurar o token na query string. Isso o
 * despeja no log de acesso do proxy, no histórico e no cabeçalho
 * {@code Referer} — e a regra do projeto é não colocar dado sensível em URL. O
 * frame CONNECT do STOMP tem cabeçalhos próprios, que viajam no corpo da
 * conexão já estabelecida e não aparecem em lugar nenhum disso.
 *
 * <p><b>Por que autorizar o SUBSCRIBE e não só o CONNECT:</b> autenticar diz
 * quem é; não diz o que a pessoa pode ouvir. Sem a checagem por inscrição, um
 * usuário legitimamente autenticado do tenant A envia um frame SUBSCRIBE
 * apontando para o tópico do tenant B e passa a receber as mensagens dele em
 * tempo real. O RLS não protege aqui: quem publica é a aplicação, e o broker
 * não conhece banco.
 */
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private static final String CABECALHO_AUTORIZACAO = "Authorization";
    private static final String PREFIXO_BEARER = "Bearer ";
    private static final String CLAIM_TENANT = "tid";

    private final JwtDecoder jwtDecoder;
    private final AutorizacaoDeEscuta autorizacao;

    StompAuthChannelInterceptor(JwtDecoder jwtDecoder, AutorizacaoDeEscuta autorizacao) {
        this.jwtDecoder = jwtDecoder;
        this.autorizacao = autorizacao;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (acessor == null || acessor.getCommand() == null) {
            return message;
        }

        return switch (acessor.getCommand()) {
            case StompCommand.CONNECT -> autenticar(message, acessor);
            case StompCommand.SUBSCRIBE -> autorizarInscricao(message, acessor);
            default -> message;
        };
    }

    private Message<?> autenticar(Message<?> message, StompHeaderAccessor acessor) {
        String token = extrairToken(acessor);
        if (token == null) {
            throw new StompNaoAutorizadoException("Conexão sem credencial.");
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            // O JwtAuthenticationToken vira o Principal da SESSÃO WebSocket
            // inteira. Todo frame seguinte é atribuído a ele — inclusive os
            // SUBSCRIBE, que é o que torna a autorização abaixo possível.
            AbstractAuthenticationToken autenticacao = new JwtAuthenticationToken(jwt, List.of());
            acessor.setUser(autenticacao);
            return message;
        } catch (JwtException e) {
            // Sem detalhe na exceção: assinatura inválida, token expirado e
            // token forjado são a mesma resposta para quem está do lado de fora.
            log.debug("CONNECT recusado: token inválido.");
            throw new StompNaoAutorizadoException("Credencial inválida.");
        }
    }

    private Message<?> autorizarInscricao(Message<?> message, StompHeaderAccessor acessor) {
        Jwt token = tokenDoPrincipal(acessor);
        if (token == null) {
            throw new StompNaoAutorizadoException("Inscrição sem sessão autenticada.");
        }
        UUID tenantDoUsuario = tenantDe(token);
        if (tenantDoUsuario == null) {
            throw new StompNaoAutorizadoException("Inscrição sem sessão autenticada.");
        }

        TopicosTempoReal.Destino destino = TopicosTempoReal
                .analisarDestino(acessor.getDestination())
                .orElse(null);

        // Destino fora do padrão é recusado, não liberado. Um destino
        // desconhecido pode ser tanto erro de cliente quanto tentativa de
        // alcançar um broker interno, e a diferença não é observável aqui.
        if (destino == null || !destino.tenantId().equals(tenantDoUsuario)) {
            log.warn("SUBSCRIBE recusado. destino={} tenantDoUsuario={}",
                    acessor.getDestination(), tenantDoUsuario);
            throw new StompNaoAutorizadoException("Destino não autorizado.");
        }

        // Tenant certo não é permissão. Sem esta segunda checagem, qualquer
        // usuário autenticado do tenant — inclusive um cujo papel não dá
        // acesso à caixa de entrada — recebe toda mensagem de cliente em tempo
        // real, que é justamente o dado que o REST lhe nega.
        try {
            autorizacao.exigirEscutaDeConversas(tenantDoUsuario, UUID.fromString(token.getSubject()));
        } catch (AcessoNegadoException e) {
            throw new StompNaoAutorizadoException("Destino não autorizado.");
        }

        return message;
    }

    private Jwt tokenDoPrincipal(StompHeaderAccessor acessor) {
        return acessor.getUser() instanceof JwtAuthenticationToken autenticacao
                ? autenticacao.getToken()
                : null;
    }

    private UUID tenantDe(Jwt token) {
        String tenantId = token.getClaimAsString(CLAIM_TENANT);
        return tenantId == null ? null : UUID.fromString(tenantId);
    }

    private String extrairToken(StompHeaderAccessor acessor) {
        String cabecalho = acessor.getFirstNativeHeader(CABECALHO_AUTORIZACAO);
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO_BEARER)) {
            return null;
        }
        return cabecalho.substring(PREFIXO_BEARER.length()).trim();
    }
}
