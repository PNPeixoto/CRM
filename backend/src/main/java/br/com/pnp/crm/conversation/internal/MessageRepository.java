package br.com.pnp.crm.conversation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Devolve só a coluna necessária, e não a conversa inteira. Carregar a
     * entidade traria a conversa para o contexto de persistência e qualquer
     * alteração acidental nela seria gravada no flush — efeito colateral
     * invisível numa operação que deveria ser de leitura.
     */
    @Query("""
            SELECT c.externalContactId
              FROM ConversationEntity c
             WHERE c.id = :conversationId
            """)
    Optional<String> buscarExternalContactIdDaConversa(@Param("conversationId") UUID conversationId);

    @Query("""
            SELECT COUNT(m) FROM MessageEntity m
             WHERE m.tenantId = :tenantId
               AND m.direction = br.com.pnp.crm.conversation.api.DirecaoMensagem.INBOUND
               AND m.createdAt >= :desde
               AND m.deletedAt IS NULL
            """)
    long contarRecebidasDesde(@Param("tenantId") UUID tenantId,
                              @Param("desde") java.time.Instant desde);
}
