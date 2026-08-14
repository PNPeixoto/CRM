package br.com.pnp.crm.task.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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
    /**
     * @param irrestrito   verdadeiro quando o alcance é todo o tenant
     * @param responsaveis ids permitidos sob alcance de equipe ou próprio,
     *                     filtrando na consulta e não sobre o resultado
     */
    @Query("""
            SELECT t FROM TaskEntity t
             WHERE t.tenantId = :tenantId
               AND t.deletedAt IS NULL
               AND (:irrestrito = TRUE OR t.assignedUserId IN :responsaveis)
               AND (:apenasAbertas = false OR t.doneAt IS NULL)
               AND (:contatoId IS NULL OR t.contactId = :contatoId)
             ORDER BY CASE WHEN t.doneAt IS NULL THEN 0 ELSE 1 END,
                      t.dueAt ASC NULLS LAST,
                      t.createdAt DESC
            """)
    List<TaskEntity> listar(@Param("tenantId") UUID tenantId,
                            @Param("irrestrito") boolean irrestrito,
                            @Param("responsaveis") Collection<UUID> responsaveis,
                            @Param("apenasAbertas") boolean apenasAbertas,
                            @Param("contatoId") UUID contatoId);

    long countByTenantIdAndDoneAtIsNullAndDeletedAtIsNull(UUID tenantId);

    long countByTenantIdAndDoneAtIsNullAndDueAtLessThanAndDeletedAtIsNull(
            UUID tenantId, Instant limite);
}
