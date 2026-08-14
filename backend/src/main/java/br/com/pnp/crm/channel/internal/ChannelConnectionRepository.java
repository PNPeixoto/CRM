package br.com.pnp.crm.channel.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ChannelConnectionRepository extends JpaRepository<ChannelConnectionEntity, UUID> {

    Optional<ChannelConnectionEntity> findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(
            UUID id, UUID tenantId);

    Optional<ChannelConnectionEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<ChannelConnectionEntity> findByTenantIdAndDeletedAtIsNullOrderByName(UUID tenantId);

    List<ChannelConnectionEntity> findByTenantIdAndIdInAndDeletedAtIsNull(
            UUID tenantId, Collection<UUID> ids);

    long countByTenantIdAndActiveTrueAndDeletedAtIsNull(UUID tenantId);
}
