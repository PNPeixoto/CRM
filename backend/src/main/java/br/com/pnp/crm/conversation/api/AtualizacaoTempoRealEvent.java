package br.com.pnp.crm.conversation.api;

import java.time.Instant;
import java.util.UUID;

/** Evento persistido, ordenado e sem conteudo sensivel para o stream da inbox. */
public record AtualizacaoTempoRealEvent(
        UUID tenantId,
        UUID eventId,
        long sequence,
        String eventType,
        UUID conversationId,
        UUID messageId,
        long resourceVersion,
        Instant occurredAt) {
}
