package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.AutorizacaoDeEscuta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Transporte de tempo real.
 *
 * <p><b>Broker simples, e a limitação é conhecida:</b> o broker embutido do
 * Spring mantém as inscrições em memória. Com duas instâncias atrás de um
 * balanceador, uma mensagem publicada na instância A não chega a quem está
 * conectado na B, e o sintoma é "às vezes a mensagem não aparece" — difícil de
 * reproduzir e fácil de culpar a rede. Trocar por um broker STOMP externo
 * (RabbitMQ) não é ajuste de configuração, muda o desenho, e precisa ser
 * decidido <b>antes</b> de subir a segunda instância.
 *
 * <p><b>Sem SockJS.</b> Ele existe para navegador sem WebSocket, o que hoje
 * não existe, e em troca acrescenta rotas HTTP de fallback que precisariam
 * entrar na configuração de segurança e no CSP.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final AutorizacaoDeEscuta autorizacao;
    private final String origensPermitidas;

    WebSocketConfig(JwtDecoder jwtDecoder, AutorizacaoDeEscuta autorizacao,
                    @Value("${app.cors.allowed-origins:}") String origensPermitidas) {
        this.jwtDecoder = jwtDecoder;
        this.autorizacao = autorizacao;
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Mesma lista branca do CORS. O handshake de WebSocket NÃO é
                // protegido pela política de mesma origem do navegador — sem
                // esta checagem, qualquer site consegue abrir uma conexão
                // contra a nossa API a partir do navegador de um usuário
                // logado. É o equivalente a CSRF no WebSocket.
                .setAllowedOrigins(listaBrancaDeOrigens().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        // Prefixo das mensagens que o cliente ENVIA para métodos @MessageMapping.
        // Hoje não há nenhum: o envio de mensagem passa por REST, para que a
        // validação e a autorização sigam um caminho só.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthChannelInterceptor(jwtDecoder, autorizacao));
    }

    /**
     * Vazio significa nenhuma origem — a mesma falha fechada do CORS HTTP.
     */
    private List<String> listaBrancaDeOrigens() {
        if (origensPermitidas == null || origensPermitidas.isBlank()) {
            return List.of();
        }
        return Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isBlank())
                .toList();
    }
}
