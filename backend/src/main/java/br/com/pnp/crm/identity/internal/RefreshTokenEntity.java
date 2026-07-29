package br.com.pnp.crm.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, insertable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

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

    protected RefreshTokenEntity() {
        // exigido pelo JPA
    }

    static RefreshTokenEntity novo(UUID tenantId, UUID userId, UUID familyId,
                                   String tokenHash, Instant expiresAt, UUID criadoPor) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.id = br.com.pnp.crm.shared.api.UuidV7.gerar();
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.familyId = familyId;
        entity.tokenHash = tokenHash;
        entity.expiresAt = expiresAt;
        entity.createdBy = criadoPor;
        entity.updatedBy = criadoPor;
        return entity;
    }

    UUID getId() {
        return id;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getFamilyId() {
        return familyId;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    void marcarComoUsado() {
        this.usedAt = Instant.now();
    }

    void revogar(String motivo) {
        // Idempotente: revogar a família inteira passa por tokens já revogados,
        // e sobrescrever a data perderia o instante da primeira revogação, que
        // é o dado relevante numa investigação.
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedReason = motivo;
        }
    }

    /**
     * Só o token mais recente e não usado da família é apresentável. As três
     * condições cobrem situações distintas: {@code usedAt} pega o reuso após
     * rotação, {@code revokedAt} pega logout e revogação de família, e a
     * expiração pega o abandono.
     */
    boolean estaValidoEm(Instant momento) {
        return usedAt == null
                && revokedAt == null
                && expiresAt.isAfter(momento);
    }

    boolean jaFoiUsado() {
        return usedAt != null;
    }
}
