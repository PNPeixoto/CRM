package br.com.pnp.crm.channel.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ChannelConnectionRepository extends JpaRepository<ChannelConnectionEntity, UUID> {

    Optional<ChannelConnectionEntity> findByIdAndActiveTrueAndDeletedAtIsNull(UUID id);

    Optional<ChannelConnectionEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<ChannelConnectionEntity> findByTenantIdAndDeletedAtIsNullOrderByName(UUID tenantId);

    long countByTenantIdAndActiveTrueAndDeletedAtIsNull(UUID tenantId);
}
