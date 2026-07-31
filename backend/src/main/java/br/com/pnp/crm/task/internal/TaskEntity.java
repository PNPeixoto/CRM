package br.com.pnp.crm.task.internal;

import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task")
class TaskEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TaskEntity() {
    }

    static TaskEntity nova(UUID tenantId, UUID autorId) {
        TaskEntity entity = new TaskEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    void aplicar(String titulo, String descricao, Instant vencimento,
                 UUID responsavelId, UUID contatoId, UUID oportunidadeId, UUID autorId) {
        this.title = titulo;
        this.description = descricao == null || descricao.isBlank() ? null : descricao.trim();
        this.dueAt = vencimento;
        this.assignedUserId = responsavelId;
        this.contactId = contatoId;
        this.dealId = oportunidadeId;
        this.updatedBy = autorId;
    }

    /**
     * Alternar em vez de receber o estado do cliente. Enviar
     * {@code concluida: true} de duas abas ao mesmo tempo sobrescreveria a
     * data de conclusão da primeira; alternar preserva a original.
     */
    void alternarConclusao(UUID autorId) {
        this.doneAt = doneAt == null ? Instant.now() : null;
        this.updatedBy = autorId;
    }

    void excluir(UUID autorId) {
        this.deletedAt = Instant.now();
        this.updatedBy = autorId;
    }

    UUID getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    UUID getContactId() {
        return contactId;
    }

    UUID getDealId() {
        return dealId;
    }

    UUID getAssignedUserId() {
        return assignedUserId;
    }

    Instant getDueAt() {
        return dueAt;
    }

    Instant getDoneAt() {
        return doneAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
