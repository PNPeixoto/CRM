package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.MensagemNormalizadaEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ponte entre o módulo {@code channel} e a ingestão.
 *
 * <p>Ouvir um evento em vez de ser chamado diretamente é o que mantém a
 * dependência em uma direção só: {@code conversation} conhece
 * {@code channel.api}, e {@code channel} não conhece ninguém. O contrário
 * criaria um ciclo entre os dois módulos.
 *
 * <p>O listener é <b>síncrono</b> de propósito. O evento nasce dentro do
 * worker de entrada, que precisa saber se a ingestão funcionou para decidir
 * entre marcar o evento como processado ou deixá-lo voltar. Com listener
 * assíncrono, o worker marcaria sucesso sem ter ideia do resultado.
 */
@Component
class RecepcaoDeMensagemNormalizada {

    private final IngestaoDeMensagemService ingestao;

    RecepcaoDeMensagemNormalizada(IngestaoDeMensagemService ingestao) {
        this.ingestao = ingestao;
    }

    @EventListener
    void aoNormalizar(MensagemNormalizadaEvent evento) {
        ingestao.registrar(evento.mensagem());
    }
}
