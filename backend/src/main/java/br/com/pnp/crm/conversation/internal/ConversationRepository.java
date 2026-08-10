package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.conversation.api.StatusConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity>
    findByTenantIdAndChannelConnectionIdAndExternalContactIdAndStatusNotAndDeletedAtIsNull(
            UUID tenantId, UUID channelConnectionId, String externalContactId, StatusConversa status);

    Optional<ConversationEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("""
            SELECT c FROM ConversationEntity c
             WHERE c.tenantId = :tenantId
               AND c.deletedAt IS NULL
               AND (:busca IS NULL OR lower(c.contactDisplayName) LIKE :busca)
             ORDER BY coalesce(c.lastMessageAt, c.createdAt) DESC, c.id DESC
            """)
    List<ConversationEntity> buscarPrimeiraPagina(
            @Param("tenantId") UUID tenantId,
            @Param("busca") String busca,
            Pageable pageable);

    @Query("""
            SELECT c FROM ConversationEntity c
             WHERE c.tenantId = :tenantId
               AND c.deletedAt IS NULL
               AND (:busca IS NULL OR lower(c.contactDisplayName) LIKE :busca)
               AND (coalesce(c.lastMessageAt, c.createdAt) < :cursorEm
                    OR (coalesce(c.lastMessageAt, c.createdAt) = :cursorEm
                        AND c.id < :cursorId))
             ORDER BY coalesce(c.lastMessageAt, c.createdAt) DESC, c.id DESC
            """)
    List<ConversationEntity> buscarDepoisDoCursor(
            @Param("tenantId") UUID tenantId,
            @Param("busca") String busca,
            @Param("cursorEm") Instant cursorEm,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, StatusConversa status);
}
