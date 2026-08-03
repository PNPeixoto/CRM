package br.com.pnp.crm.deal.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PipelineStageRepository extends JpaRepository<PipelineStageEntity, UUID> {

    List<PipelineStageEntity> findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderByPosition(
            UUID tenantId, UUID pipelineId);

    Optional<PipelineStageEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
