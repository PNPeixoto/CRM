package br.com.pnp.crm.conversation.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando uma mensagem entra no sistema pela primeira vez.
 *
 * <p><b>Não é publicado em reentrega.</b> Provedor reenvia webhook o tempo
 * todo; se o evento saísse a cada reentrega, o atendente veria a mensagem
 * piscar na tela repetidamente e qualquer automação futura disparia várias
 * vezes pelo mesmo fato.
 *
 * <p>É por aqui que o tempo real será alimentado na próxima etapa: o módulo
 * que empurra para o WebSocket escuta este evento em vez de ser chamado
 * direto pela ingestão. Entrega em tempo real é otimização, não fonte da
 * verdade — se o push falhar, o dado está no banco e o cliente recupera por
 * REST ao reconectar.
 */
public record MensagemRecebidaEvent(
        UUID tenantId,
        UUID conversationId,
        UUID messageId,
        UUID channelConnectionId,
        String texto,
        Instant ocorridoEm) {
}
