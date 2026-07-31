package br.com.pnp.crm.deal.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {

    List<PipelineEntity> findByTenantIdAndDeletedAtIsNullOrderByName(UUID tenantId);

    Optional<PipelineEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<PipelineEntity> findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(UUID tenantId);
}
