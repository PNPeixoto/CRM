package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.conversation.api.StatusConversa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByChannelConnectionIdAndExternalContactIdAndStatusNotAndDeletedAtIsNull(
            UUID channelConnectionId, String externalContactId, StatusConversa status);

    Optional<ConversationEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<ConversationEntity> findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(UUID tenantId);

    long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, StatusConversa status);
}
