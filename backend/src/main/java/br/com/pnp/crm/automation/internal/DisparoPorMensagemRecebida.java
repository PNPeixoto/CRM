package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationEngine;
import br.com.pnp.crm.automation.api.AutomationTrigger;
import br.com.pnp.crm.conversation.api.MensagemRecebidaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Conecta o primeiro gatilho real sem copiar texto da mensagem para o motor. */
@Component
class DisparoPorMensagemRecebida {

    private static final Logger log = LoggerFactory.getLogger(DisparoPorMensagemRecebida.class);

    private final AutomationEngine motor;

    DisparoPorMensagemRecebida(AutomationEngine motor) {
        this.motor = motor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoReceber(MensagemRecebidaEvent evento) {
        try {
            motor.disparar(new AutomationTrigger(
                    evento.tenantId(), "MESSAGE_RECEIVED", "message:" + evento.messageId(),
                    evento.messageId(), null));
        } catch (RuntimeException e) {
            // A mensagem ja foi confirmada no banco. Automacao indisponivel nao
            // pode desfazer a entrada; o log omite texto e excecao por desenho.
            log.error("Falha sanitizada ao disparar automacao de mensagem. messageId={}",
                    evento.messageId());
        }
    }
}
