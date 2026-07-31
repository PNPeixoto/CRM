package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline")
class PipelineEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

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

    protected PipelineEntity() {
    }

    static PipelineEntity novo(UUID tenantId, String nome, boolean padrao, UUID autorId) {
        PipelineEntity entity = new PipelineEntity();
        entity.id = UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.name = nome;
        entity.isDefault = padrao;
        entity.createdBy = autorId;
        entity.updatedBy = autorId;
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    String getName() {
        return name;
    }

    boolean isDefault() {
        return isDefault;
    }
}
