package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.SessaoTempoRealRevogada;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/** Fecha o transporte, em vez de apenas deixar uma assinatura revogada silenciosa. */
@Component
class EncerramentoDeSessaoWebSocket {

    private final StompAuthChannelInterceptor autenticacao;
    private final RegistroDeSessoesWebSocket sessoes;

    EncerramentoDeSessaoWebSocket(StompAuthChannelInterceptor autenticacao,
                                  RegistroDeSessoesWebSocket sessoes) {
        this.autenticacao = autenticacao;
        this.sessoes = sessoes;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoRevogar(SessaoTempoRealRevogada evento) {
        sessoes.encerrar(autenticacao.retirarSessoes(evento));
    }

    @Scheduled(fixedDelayString = "${app.websocket.session-expiration-check-ms:5000}")
    void encerrarTokensExpirados() {
        sessoes.encerrar(autenticacao.retirarSessoesExpiradas(Instant.now()));
    }
}
