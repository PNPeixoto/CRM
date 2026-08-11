package br.com.pnp.crm.deal.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DealRepository extends JpaRepository<DealEntity, UUID> {

    boolean existsByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /** Recorta pelos responsáveis permitidos, salvo alcance de tenant inteiro. */
    @Query("""
            SELECT d FROM DealEntity d
             WHERE d.tenantId = :tenantId
               AND d.pipelineId = :funilId
               AND d.deletedAt IS NULL
               AND (:irrestrito = TRUE OR d.ownerUserId IN :responsaveis)
             ORDER BY d.createdAt DESC
            """)
    List<DealEntity> listarDoFunil(@Param("tenantId") UUID tenantId,
                                   @Param("funilId") UUID funilId,
                                   @Param("irrestrito") boolean irrestrito,
                                   @Param("responsaveis") Collection<UUID> responsaveis);

    List<DealEntity> findByTenantIdAndPipelineIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId, UUID pipelineId);

    Optional<DealEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Soma em centavos, como inteiro até a borda da API. Converter para
     * decimal aqui dentro faria o total do funil ganhar casas que ninguém
     * consegue reconciliar com a soma linha a linha.
     */
    @Query("""
            SELECT COALESCE(SUM(d.valueCents), 0) FROM DealEntity d
             WHERE d.tenantId = :tenantId AND d.status = :status AND d.deletedAt IS NULL
            """)
    long somarValorPorStatus(@Param("tenantId") UUID tenantId,
                             @Param("status") StatusOportunidade status);

    long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, StatusOportunidade status);
}
