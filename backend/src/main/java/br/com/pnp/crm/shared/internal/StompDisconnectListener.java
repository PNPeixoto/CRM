package br.com.pnp.crm.shared.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Libera limites mesmo quando a rede cai sem frame DISCONNECT. */
@Component
class StompDisconnectListener {

    private final StompAuthChannelInterceptor interceptor;

    StompDisconnectListener(StompAuthChannelInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @EventListener
    void aoDesconectar(SessionDisconnectEvent evento) {
        interceptor.liberar(evento.getSessionId());
    }
}
