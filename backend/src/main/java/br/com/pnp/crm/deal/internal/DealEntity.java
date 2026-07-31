package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "deal")
class DealEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(nullable = false)
    private String title;

    /** Centavos. Ver o comentário da coluna na V5 — nunca ponto flutuante. */
    @Column(name = "value_cents", nullable = false)
    private long valueCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusOportunidade status;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

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

    protected DealEntity() {
    }

    static DealEntity nova(UUID tenantId, UUID pipelineId, UUID stageId, UUID autorId) {
        DealEntity entity = new DealEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.pipelineId = pipelineId;
        entity.stageId = stageId;
        entity.status = StatusOportunidade.OPEN;
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    void aplicar(String titulo, long valorCentavos, UUID contatoId,
                 LocalDate previsaoFechamento, UUID responsavelId, UUID autorId) {
        this.title = titulo;
        this.valueCents = valorCentavos;
        this.contactId = contatoId;
        this.expectedCloseDate = previsaoFechamento;
        this.ownerUserId = responsavelId;
        this.updatedBy = autorId;
    }

    /**
     * Mover de etapa é a operação central do kanban, e ela decide o status.
     *
     * <p>O status é derivado da etapa, e não informado pelo cliente: um funil
     * onde "ganho" é escolhido separadamente da coluna permite arrastar para
     * "Fechado" e o relatório continuar contando como aberto — divergência que
     * só aparece no fechamento do mês.
     */
    void moverPara(PipelineStageEntity etapa, UUID autorId) {
        this.stageId = etapa.getId();
        this.pipelineId = etapa.getPipelineId();
        this.updatedBy = autorId;

        if (etapa.isWon()) {
            this.status = StatusOportunidade.WON;
            this.closedAt = Instant.now();
            this.lostReason = null;
        } else if (etapa.isLost()) {
            this.status = StatusOportunidade.LOST;
            this.closedAt = Instant.now();
        } else {
            // Reabrir também é mover: arrastar de volta para uma coluna
            // intermediária limpa o fechamento.
            this.status = StatusOportunidade.OPEN;
            this.closedAt = null;
            this.lostReason = null;
        }
    }

    void registrarMotivoDePerda(String motivo) {
        if (status == StatusOportunidade.LOST) {
            this.lostReason = motivo;
        }
    }

    void excluir(UUID autorId) {
        this.deletedAt = Instant.now();
        this.updatedBy = autorId;
    }

    UUID getId() {
        return id;
    }

    UUID getPipelineId() {
        return pipelineId;
    }

    UUID getStageId() {
        return stageId;
    }

    UUID getContactId() {
        return contactId;
    }

    String getTitle() {
        return title;
    }

    long getValueCents() {
        return valueCents;
    }

    StatusOportunidade getStatus() {
        return status;
    }

    LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    String getLostReason() {
        return lostReason;
    }

    UUID getOwnerUserId() {
        return ownerUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
