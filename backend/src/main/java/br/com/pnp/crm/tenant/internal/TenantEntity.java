package br.com.pnp.crm.tenant.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A conta contratante — uma franqueadora, no cenário-alvo.
 *
 * <p>Vive no módulo {@code tenant} e não em {@code identity} embora a
 * autenticação precise dela. O módulo {@code tenant} é quem vai crescer com
 * regiões, unidades e equipes; se a entidade nascesse dentro de
 * {@code identity}, esse crescimento inteiro aconteceria no módulo errado ou
 * exigiria uma migração de pacotes depois. O que {@code identity} precisa é
 * apenas traduzir slug em id, e isso é exposto por
 * {@link br.com.pnp.crm.tenant.api.TenantLookup}.
 */
@Entity
@Table(name = "tenant")
class TenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

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

    protected TenantEntity() {
        // exigido pelo JPA
    }

    UUID getId() {
        return id;
    }

    String getSlug() {
        return slug;
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }

    Instant getDeletedAt() {
        return deletedAt;
    }
}
