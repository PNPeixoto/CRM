package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.tenant.api.BusinessSegment;
import br.com.pnp.crm.tenant.api.TenantPresentation;
import br.com.pnp.crm.tenant.api.TenantPresentationLookup;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class TenantPresentationService implements TenantPresentationLookup {

    private final TenantProfileRepository profiles;
    private final SegmentPresetCatalog presets;
    private final EntityManager entityManager;

    TenantPresentationService(TenantProfileRepository profiles, SegmentPresetCatalog presets,
                              EntityManager entityManager) {
        this.profiles = profiles;
        this.presets = presets;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantPresentation atual() {
        UUID tenantId = TenantContext.obrigatorio();
        return profiles.findById(tenantId)
                .map(profile -> presets.resolve(profile.getBusinessSegment(),
                        profile.getPresetVersion(), profile.isOnboardingCompleted()))
                // Antes da escolha, o contrato continua completo e seguro. O
                // frontend usa onboardingCompleted para mostrar a seleção.
                .orElseGet(() -> presets.preview(BusinessSegment.GENERAL_SERVICES, false));
    }

    @Transactional
    TenantPresentation concluirPerfilInicial(BusinessSegment segment, UUID authorId) {
        UUID tenantId = TenantContext.obrigatorio();

        // O perfil pode ainda não existir, portanto não há linha nele para
        // bloquear. A linha do tenant existe sempre e serializa dois PUTs.
        entityManager.createNativeQuery("SELECT id FROM tenant WHERE id = :id FOR UPDATE")
                .setParameter("id", tenantId)
                .getSingleResult();

        TenantProfileEntity profile = profiles.findById(tenantId).orElse(null);
        if (profile != null && profile.isOnboardingCompleted()) {
            if (profile.getBusinessSegment() != segment) {
                throw new PerfilInicialJaConcluidoException();
            }
            return presets.resolve(segment, profile.getPresetVersion(), true);
        }

        if (profile == null) {
            profile = TenantProfileEntity.inicial(
                    tenantId, segment, SegmentPresetCatalog.CURRENT_VERSION, authorId);
            profiles.save(profile);
        } else {
            profile.concluir(segment, SegmentPresetCatalog.CURRENT_VERSION, authorId);
        }

        return presets.preview(segment, true);
    }
}
