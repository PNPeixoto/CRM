package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.AutorizacaoDeEscuta;
import br.com.pnp.crm.shared.api.TopicosTempoReal;
import br.com.pnp.crm.shared.api.ValidadorSessaoTempoReal;
import br.com.pnp.crm.shared.api.SessaoTempoRealRevogada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Autentica CONNECT e revalida sessao, destino e permissao em cada SUBSCRIBE. */
@Component
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";
    private static final String CLAIM_TENANT = "tid";
    private static final String CLAIM_SESSION = "sid";

    private final JwtDecoder jwtDecoder;
    private final AutorizacaoDeEscuta autorizacao;
    private final ValidadorSessaoTempoReal validadorSessao;
    private final int maximoPorUsuario;
    private final int maximoPorTenant;
    private final int maximoDeFramesPorSegundo;
    private final Map<String, ControleSessao> sessoes = new ConcurrentHashMap<>();

    @Autowired
    StompAuthChannelInterceptor(
            JwtDecoder jwtDecoder,
            AutorizacaoDeEscuta autorizacao,
            ValidadorSessaoTempoReal validadorSessao,
            @Value("${app.websocket.max-connections-per-user:3}") int maximoPorUsuario,
            @Value("${app.websocket.max-connections-per-tenant:100}") int maximoPorTenant,
            @Value("${app.websocket.max-frames-per-second:20}") int maximoDeFramesPorSegundo) {
        this.jwtDecoder = jwtDecoder;
        this.autorizacao = autorizacao;
        this.validadorSessao = validadorSessao;
        this.maximoPorUsuario = Math.clamp(maximoPorUsuario, 1, 20);
        this.maximoPorTenant = Math.clamp(maximoPorTenant, 1, 10_000);
        this.maximoDeFramesPorSegundo = Math.clamp(maximoDeFramesPorSegundo, 5, 200);
    }

    /** Construtor estreito para testes unitarios da allowlist. */
    StompAuthChannelInterceptor(JwtDecoder jwtDecoder, AutorizacaoDeEscuta autorizacao) {
        this(jwtDecoder, autorizacao, (tenant, usuario, sessao) -> {}, 3, 100, 20);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acessor == null || acessor.getCommand() == null) return message;

        if (acessor.getCommand() != StompCommand.CONNECT) {
            controlarFrequencia(acessor.getSessionId());
        }
        return switch (acessor.getCommand()) {
            case CONNECT -> autenticar(message, acessor);
            case SUBSCRIBE -> autorizarInscricao(message, acessor);
            case UNSUBSCRIBE -> message;
            case DISCONNECT -> {
                liberar(acessor.getSessionId());
                yield message;
            }
            default -> throw new StompNaoAutorizadoException("Comando nao permitido.");
        };
    }

    private Message<?> autenticar(Message<?> message, StompHeaderAccessor acessor) {
        String tokenApresentado = extrairToken(acessor);
        if (tokenApresentado == null) {
            throw new StompNaoAutorizadoException("Conexao sem credencial.");
        }
        try {
            Jwt jwt = jwtDecoder.decode(tokenApresentado);
            UUID tenantId = tenantDe(jwt);
            UUID usuarioId = uuid(jwt.getSubject());
            UUID sessaoId = uuid(jwt.getClaimAsString(CLAIM_SESSION));
            if (tenantId == null || usuarioId == null || sessaoId == null) {
                throw new StompNaoAutorizadoException("Credencial invalida.");
            }
            validadorSessao.exigirAtiva(tenantId, usuarioId, sessaoId);
            registrar(acessor.getSessionId(), tenantId, usuarioId, sessaoId, jwt.getExpiresAt());
            AbstractAuthenticationToken autenticacao = new JwtAuthenticationToken(jwt, List.of());
            acessor.setUser(autenticacao);
            return message;
        } catch (JwtException | AcessoNegadoException e) {
            log.debug("CONNECT recusado: credencial ou sessao invalida.");
            throw new StompNaoAutorizadoException("Credencial invalida.");
        }
    }

    private Message<?> autorizarInscricao(Message<?> message, StompHeaderAccessor acessor) {
        Jwt token = tokenDoPrincipal(acessor);
        if (token == null) {
            throw new StompNaoAutorizadoException("Inscricao sem sessao autenticada.");
        }
        UUID tenantId = tenantDe(token);
        UUID usuarioId = uuid(token.getSubject());
        UUID sessaoId = uuid(token.getClaimAsString(CLAIM_SESSION));
        if (tenantId == null || usuarioId == null || sessaoId == null
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(Instant.now())) {
            liberar(acessor.getSessionId());
            throw new StompNaoAutorizadoException("Sessao expirada.");
        }
        try {
            validadorSessao.exigirAtiva(tenantId, usuarioId, sessaoId);
        } catch (AcessoNegadoException e) {
            liberar(acessor.getSessionId());
            throw new StompNaoAutorizadoException("Sessao expirada.");
        }

        TopicosTempoReal.Destino destino = TopicosTempoReal
                .analisarDestino(acessor.getDestination()).orElse(null);
        if (destino == null || !destino.tenantId().equals(tenantId)) {
            log.warn("SUBSCRIBE recusado. destino={} tenantDoUsuario={}",
                    acessor.getDestination(), tenantId);
            throw new StompNaoAutorizadoException("Destino nao autorizado.");
        }
        try {
            autorizacao.exigirEscutaDeConversas(tenantId, usuarioId);
        } catch (AcessoNegadoException e) {
            throw new StompNaoAutorizadoException("Destino nao autorizado.");
        }
        return message;
    }

    private Jwt tokenDoPrincipal(StompHeaderAccessor acessor) {
        return acessor.getUser() instanceof JwtAuthenticationToken autenticacao
                ? autenticacao.getToken() : null;
    }

    private UUID tenantDe(Jwt token) {
        return uuid(token.getClaimAsString(CLAIM_TENANT));
    }

    private UUID uuid(String valor) {
        try {
            return valor == null ? null : UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extrairToken(StompHeaderAccessor acessor) {
        String cabecalho = acessor.getFirstNativeHeader(AUTHORIZATION);
        if (cabecalho == null || !cabecalho.startsWith(BEARER)) return null;
        return cabecalho.substring(BEARER.length()).trim();
    }

    private synchronized void registrar(String sessionId, UUID tenantId,
                                         UUID usuarioId, UUID sessaoId, Instant expiraEm) {
        if (sessionId == null || sessoes.containsKey(sessionId)) return;
        long porTenant = sessoes.values().stream()
                .filter(s -> s.tenantId.equals(tenantId)).count();
        long porUsuario = sessoes.values().stream()
                .filter(s -> s.tenantId.equals(tenantId) && s.usuarioId.equals(usuarioId)).count();
        if (porTenant >= maximoPorTenant || porUsuario >= maximoPorUsuario) {
            throw new StompNaoAutorizadoException("Limite de conexoes atingido.");
        }
        sessoes.put(sessionId, new ControleSessao(tenantId, usuarioId, sessaoId, expiraEm));
    }

    synchronized List<String> retirarSessoes(SessaoTempoRealRevogada revogacao) {
        List<String> retiradas = new ArrayList<>();
        sessoes.forEach((id, sessao) -> {
            boolean mesmaSessao = revogacao.sessaoId() == null
                    || revogacao.sessaoId().equals(sessao.sessaoId);
            if (revogacao.tenantId().equals(sessao.tenantId)
                    && revogacao.usuarioId().equals(sessao.usuarioId) && mesmaSessao) {
                retiradas.add(id);
            }
        });
        retiradas.forEach(sessoes::remove);
        return List.copyOf(retiradas);
    }

    synchronized List<String> retirarSessoesExpiradas(Instant agora) {
        List<String> retiradas = sessoes.entrySet().stream()
                .filter(item -> item.getValue().expiraEm == null
                        || !item.getValue().expiraEm.isAfter(agora))
                .map(Map.Entry::getKey)
                .toList();
        retiradas.forEach(sessoes::remove);
        return retiradas;
    }

    void liberar(String sessionId) {
        if (sessionId != null) sessoes.remove(sessionId);
    }

    private void controlarFrequencia(String sessionId) {
        if (sessionId == null) return;
        ControleSessao sessao = sessoes.get(sessionId);
        if (sessao == null || !sessao.permitir(maximoDeFramesPorSegundo)) {
            throw new StompNaoAutorizadoException("Frequencia de frames excedida.");
        }
    }

    private static final class ControleSessao {
        private final UUID tenantId;
        private final UUID usuarioId;
        private final UUID sessaoId;
        private final Instant expiraEm;
        private long segundo;
        private int frames;

        private ControleSessao(UUID tenantId, UUID usuarioId, UUID sessaoId, Instant expiraEm) {
            this.tenantId = tenantId;
            this.usuarioId = usuarioId;
            this.sessaoId = sessaoId;
            this.expiraEm = expiraEm;
        }

        private synchronized boolean permitir(int limite) {
            long agora = System.currentTimeMillis() / 1000;
            if (agora != segundo) {
                segundo = agora;
                frames = 0;
            }
            return ++frames <= limite;
        }
    }
}
