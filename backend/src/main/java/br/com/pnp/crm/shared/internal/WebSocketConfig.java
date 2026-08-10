package br.com.pnp.crm.shared.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;
import java.util.List;

/** Transporte STOMP limitado; REST e o stream persistido continuam sendo a verdade. */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor interceptor;
    private final TaskScheduler heartbeatScheduler;
    private final RegistroDeSessoesWebSocket sessoes;
    private final String origensPermitidas;

    WebSocketConfig(
            StompAuthChannelInterceptor interceptor,
            RegistroDeSessoesWebSocket sessoes,
            @Qualifier("stompHeartbeatScheduler") TaskScheduler heartbeatScheduler,
            @Value("${app.cors.allowed-origins:}") String origensPermitidas) {
        this.interceptor = interceptor;
        this.sessoes = sessoes;
        this.heartbeatScheduler = heartbeatScheduler;
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(listaBrancaDeOrigens().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
        registration.taskExecutor()
                .corePoolSize(2)
                .maxPoolSize(8)
                .queueCapacity(500);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .addDecoratorFactory(sessoes::decorar)
                .setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(256 * 1024)
                .setSendTimeLimit(10_000)
                .setTimeToFirstMessage(15_000);
    }

    private List<String> listaBrancaDeOrigens() {
        if (origensPermitidas == null || origensPermitidas.isBlank()) return List.of();
        return Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isBlank())
                .toList();
    }

    @Bean({"stompHeartbeatScheduler", "taskScheduler"})
    static TaskScheduler stompHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
