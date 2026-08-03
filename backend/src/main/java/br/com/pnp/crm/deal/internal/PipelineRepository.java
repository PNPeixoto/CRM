package br.com.pnp.crm.deal.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO pipeline
                (id, tenant_id, name, is_default, created_by, updated_by)
            VALUES
                (:id, :tenantId, :name, true, :authorId, :authorId)
            ON CONFLICT (tenant_id) WHERE is_default AND deleted_at IS NULL
            DO NOTHING
            """, nativeQuery = true)
    int insertDefaultIfAbsent(@Param("id") UUID id,
                              @Param("tenantId") UUID tenantId,
                              @Param("name") String name,
                              @Param("authorId") UUID authorId);

    List<PipelineEntity> findByTenantIdAndDeletedAtIsNullOrderByName(UUID tenantId);

    Optional<PipelineEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<PipelineEntity> findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(UUID tenantId);
}
