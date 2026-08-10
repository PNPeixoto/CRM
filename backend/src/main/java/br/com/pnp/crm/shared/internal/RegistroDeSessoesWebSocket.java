package br.com.pnp.crm.shared.internal;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mantém somente as sessões vivas necessárias para aplicar revogação imediata. */
@Component
class RegistroDeSessoesWebSocket {

    private final Map<String, WebSocketSession> sessoes = new ConcurrentHashMap<>();

    WebSocketHandler decorar(WebSocketHandler delegado) {
        return new WebSocketHandlerDecorator(delegado) {
            @Override
            public void afterConnectionEstablished(WebSocketSession sessao) throws Exception {
                sessoes.put(sessao.getId(), sessao);
                super.afterConnectionEstablished(sessao);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession sessao, CloseStatus status)
                    throws Exception {
                sessoes.remove(sessao.getId());
                super.afterConnectionClosed(sessao, status);
            }
        };
    }

    void encerrar(Collection<String> ids) {
        for (String id : ids) {
            WebSocketSession sessao = sessoes.remove(id);
            if (sessao == null || !sessao.isOpen()) continue;
            try {
                sessao.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
                // A sessão já está materialmente encerrada; o listener limpa o limite.
            }
        }
    }
}
