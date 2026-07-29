package br.com.pnp.crm.channel.api;

import java.util.UUID;

/**
 * Mensagem a enviar, no formato normalizado.
 *
 * <p>Carrega o {@code messageId} interno de propósito: o envio é feito por um
 * worker lendo a fila de saída, e o resultado precisa voltar para a linha
 * certa em {@code message} — é lá que o {@code external_id} devolvido pelo
 * provedor é gravado, e sem ele os eventos de entrega e leitura que chegam
 * depois não têm com o que casar.
 *
 * @param messageId           id interno da mensagem já persistida
 * @param channelConnectionId por onde enviar
 * @param externalContactId   destinatário no provedor
 * @param tipoConteudo        classificação normalizada
 * @param texto               conteúdo textual
 */
public record OutboundMessage(
        UUID messageId,
        UUID channelConnectionId,
        String externalContactId,
        TipoConteudo tipoConteudo,
        String texto) {

    public OutboundMessage {
        if (messageId == null) {
            throw new IllegalArgumentException("messageId é obrigatório");
        }
        if (channelConnectionId == null) {
            throw new IllegalArgumentException("channelConnectionId é obrigatório");
        }
        if (externalContactId == null || externalContactId.isBlank()) {
            throw new IllegalArgumentException("externalContactId é obrigatório");
        }
        if (tipoConteudo == null) {
            throw new IllegalArgumentException("tipoConteudo é obrigatório");
        }
    }
}
