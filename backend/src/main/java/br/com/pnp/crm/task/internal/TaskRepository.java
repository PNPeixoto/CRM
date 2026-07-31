package br.com.pnp.crm.task.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Abertas primeiro, ordenadas por vencimento; sem data vão para o fim.
     * Concluídas entram depois, para que a lista funcione como fila de
     * trabalho e não como histórico.
     */
    @Query("""
            SELECT t FROM TaskEntity t
             WHERE t.tenantId = :tenantId
               AND t.deletedAt IS NULL
               AND (:apenasAbertas = false OR t.doneAt IS NULL)
             ORDER BY CASE WHEN t.doneAt IS NULL THEN 0 ELSE 1 END,
                      t.dueAt ASC NULLS LAST,
                      t.createdAt DESC
            """)
    List<TaskEntity> listar(@Param("tenantId") UUID tenantId,
                            @Param("apenasAbertas") boolean apenasAbertas);

    long countByTenantIdAndDoneAtIsNullAndDeletedAtIsNull(UUID tenantId);

    long countByTenantIdAndDoneAtIsNullAndDueAtLessThanAndDeletedAtIsNull(
            UUID tenantId, Instant limite);
}
