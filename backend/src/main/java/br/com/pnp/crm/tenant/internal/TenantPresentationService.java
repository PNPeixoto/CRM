package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.tenant.api.BusinessSegment;
import br.com.pnp.crm.tenant.api.PerfilInicialConcluido;
import br.com.pnp.crm.tenant.api.TenantPresentation;
import br.com.pnp.crm.tenant.api.TenantPresentationLookup;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class TenantPresentationService implements TenantPresentationLookup {

    private final TenantProfileRepository profiles;
    private final SegmentPresetCatalog presets;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final AuditTrail audit;

    TenantPresentationService(TenantProfileRepository profiles, SegmentPresetCatalog presets,
                              EntityManager entityManager, ApplicationEventPublisher events,
                              AuditTrail audit) {
        this.profiles = profiles;
        this.presets = presets;
        this.entityManager = entityManager;
        this.events = events;
        this.audit = audit;
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
        bloquearTenant(tenantId);

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
        } else {
            profile.concluir(segment, SegmentPresetCatalog.CURRENT_VERSION, authorId);
        }

        TenantPresentation presentation = presets.preview(segment, true);
        profiles.saveAndFlush(profile);
        // Listener síncrono: se qualquer default falhar, o perfil também faz
        // rollback. Depois do commit, a empresa nunca fica "concluída" pela metade.
        events.publishEvent(new PerfilInicialConcluido(
                tenantId, authorId, segment, presentation.presetVersion(),
                presentation.defaultPipeline()));
        auditar(tenantId, authorId, AuditTrail.Motivo.TENANT_PROFILE_COMPLETED);
        return presentation;
    }

    @Transactional
    TenantPresentation alterarApresentacao(BusinessSegment segment, UUID authorId) {
        UUID tenantId = TenantContext.obrigatorio();
        bloquearTenant(tenantId);
        TenantProfileEntity profile = profiles.findById(tenantId)
                .filter(TenantProfileEntity::isOnboardingCompleted)
                .orElseThrow(PerfilInicialPendenteException::new);

        profile.alterarApresentacao(segment, SegmentPresetCatalog.CURRENT_VERSION, authorId);
        auditar(tenantId, authorId, AuditTrail.Motivo.TENANT_PRESENTATION_CHANGED);
        return presets.preview(segment, true);
    }

    private void auditar(UUID tenantId, UUID authorId, AuditTrail.Motivo motivo) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.SENSITIVE_CONFIGURATION_CHANGED,
                AuditTrail.Ator.humano(authorId),
                AuditTrail.Escopo.tenant(tenantId),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.TENANT_PROFILE, tenantId),
                AuditTrail.Resultado.SUCCEEDED, motivo));
    }

    private void bloquearTenant(UUID tenantId) {
        // O perfil pode ainda não existir, portanto não há linha nele para
        // bloquear. A linha do tenant existe sempre e serializa dois PUTs.
        entityManager.createNativeQuery("SELECT id FROM tenant WHERE id = :id FOR UPDATE")
                .setParameter("id", tenantId)
                .getSingleResult();
    }
}
