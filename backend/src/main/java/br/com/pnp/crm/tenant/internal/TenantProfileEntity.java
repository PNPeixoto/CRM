package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.tenant.api.BusinessSegment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_profile")
class TenantProfileEntity {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_segment", nullable = false)
    private BusinessSegment businessSegment;

    @Column(name = "preset_version", nullable = false)
    private int presetVersion;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected TenantProfileEntity() {
    }

    static TenantProfileEntity inicial(UUID tenantId, BusinessSegment segment,
                                       int presetVersion, UUID authorId) {
        TenantProfileEntity profile = new TenantProfileEntity();
        profile.tenantId = tenantId;
        profile.concluir(segment, presetVersion, authorId);
        profile.createdBy = authorId;
        return profile;
    }

    void concluir(BusinessSegment segment, int version, UUID authorId) {
        this.businessSegment = segment;
        this.presetVersion = version;
        this.onboardingCompleted = true;
        this.updatedBy = authorId;
    }

    /** Atualiza só a apresentação; funis e dados materializados não são reaplicados. */
    void alterarApresentacao(BusinessSegment segment, int version, UUID authorId) {
        this.businessSegment = segment;
        this.presetVersion = version;
        this.updatedBy = authorId;
    }

    BusinessSegment getBusinessSegment() {
        return businessSegment;
    }

    int getPresetVersion() {
        return presetVersion;
    }

    boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }
}
