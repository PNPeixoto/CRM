package br.com.pnp.crm.conversation.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    /**
     * Checagem de idempotência antes da inserção. Não substitui a constraint
     * do banco: entre esta consulta e o INSERT existe uma janela, e duas
     * entregas simultâneas do mesmo webhook passariam as duas por aqui. A
     * consulta evita o caso comum; a constraint resolve a corrida.
     */
    Optional<MessageEntity> findByChannelConnectionIdAndExternalId(
            UUID channelConnectionId, String externalId);

    List<MessageEntity> findByConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID conversationId);
}
