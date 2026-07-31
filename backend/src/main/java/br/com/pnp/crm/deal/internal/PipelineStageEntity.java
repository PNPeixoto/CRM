package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_stage")
class PipelineStageEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_won", nullable = false)
    private boolean won;

    @Column(name = "is_lost", nullable = false)
    private boolean lost;

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

    protected PipelineStageEntity() {
    }

    static PipelineStageEntity nova(UUID tenantId, UUID pipelineId, String nome,
                                    int posicao, boolean ganho, boolean perda, UUID autorId) {
        PipelineStageEntity entity = new PipelineStageEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.pipelineId = pipelineId;
        entity.name = nome;
        entity.position = posicao;
        entity.won = ganho;
        entity.lost = perda;
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getPipelineId() {
        return pipelineId;
    }

    String getName() {
        return name;
    }

    int getPosition() {
        return position;
    }

    boolean isWon() {
        return won;
    }

    boolean isLost() {
        return lost;
    }
}
