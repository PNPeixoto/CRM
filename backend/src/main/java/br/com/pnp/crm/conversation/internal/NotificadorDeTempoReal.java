package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.conversation.api.MensagemRecebidaEvent;
import br.com.pnp.crm.shared.api.TopicosTempoReal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Empurra para o navegador o que acabou de entrar no banco.
 *
 * <p><b>Depois do commit, sempre.</b> {@code AFTER_COMMIT} é o que impede o
 * caso em que o atendente vê a mensagem na tela e a transação faz rollback em
 * seguida — o dado sumiria do banco e continuaria na tela até alguém recarregar.
 * Publicar antes do commit troca uma janela de milissegundos de latência por
 * uma tela que mente.
 *
 * <p><b>Falha aqui não derruba a ingestão.</b> Tempo real é otimização, não
 * fonte da verdade: se o push falhar, a mensagem continua gravada e o cliente
 * a recupera por REST na próxima carga ou reconexão. Deixar a exceção subir
 * marcaria como falha uma operação que deu certo.
 */
@Component
class NotificadorDeTempoReal {

    private static final Logger log = LoggerFactory.getLogger(NotificadorDeTempoReal.class);

    private final SimpMessagingTemplate mensageria;

    NotificadorDeTempoReal(SimpMessagingTemplate mensageria) {
        this.mensageria = mensageria;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoReceberMensagem(MensagemRecebidaEvent evento) {
        try {
            MensagemPush push = new MensagemPush(
                    evento.conversationId(),
                    evento.messageId(),
                    "INBOUND",
                    evento.texto(),
                    evento.ocorridoEm());

            // Dois destinos, porque são duas telas: quem está com a conversa
            // aberta e quem está olhando a lista.
            mensageria.convertAndSend(
                    TopicosTempoReal.conversa(evento.tenantId(), evento.conversationId()), push);
            mensageria.convertAndSend(
                    TopicosTempoReal.inbox(evento.tenantId()), push);

        } catch (RuntimeException e) {
            // O log não inclui o texto da mensagem: conteúdo de cliente não
            // entra em log, por regra do projeto.
            log.warn("Falha ao publicar mensagem em tempo real. conversationId={} messageId={}",
                    evento.conversationId(), evento.messageId(), e);
        }
    }

    /**
     * Formato do que trafega no WebSocket. Separado dos DTOs REST de
     * propósito: o cliente de tempo real recebe o mínimo para atualizar a tela,
     * e um campo acrescentado ao REST não passa a vazar pelo socket sem alguém
     * decidir isso.
     */
    record MensagemPush(
            UUID conversationId,
            UUID messageId,
            String direcao,
            String texto,
            Instant ocorridoEm) {
    }
}
