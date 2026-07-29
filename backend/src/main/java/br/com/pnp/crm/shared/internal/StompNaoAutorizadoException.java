package br.com.pnp.crm.shared.internal;

import org.springframework.messaging.MessagingException;

/**
 * Recusa de CONNECT ou SUBSCRIBE.
 *
 * <p>Estende {@link MessagingException} e não {@code DomainException} porque
 * quem a trata é o broker STOMP, não o {@code GlobalExceptionHandler} — não há
 * requisição HTTP em curso para virar resposta. Lançá-la no interceptor aborta
 * o frame e derruba a conexão, que é o comportamento desejado: uma sessão que
 * tentou alcançar tópico alheio não deve continuar aberta.
 */
class StompNaoAutorizadoException extends MessagingException {

    StompNaoAutorizadoException(String mensagem) {
        super(mensagem);
    }
}
