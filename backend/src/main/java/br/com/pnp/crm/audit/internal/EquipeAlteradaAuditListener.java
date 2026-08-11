package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.organization.api.EquipeAlterada;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registra mudanca de composicao de equipe na trilha corporativa.
 *
 * <p>O alvo e o gestor, porque e o alcance dele que muda: quem ganha ou perde
 * visibilidade e quem lidera, nao quem foi incluido.
 */
@Component
class EquipeAlteradaAuditListener {

    private final AuditTrail audit;

    EquipeAlteradaAuditListener(AuditTrail audit) {
        this.audit = audit;
    }

    @EventListener
    void registrar(EquipeAlterada evento) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.ROLE_CHANGED,
                AuditTrail.Ator.humano(evento.actorId()),
                AuditTrail.Escopo.tenant(evento.tenantId()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.TEAM, evento.gestorId()),
                AuditTrail.Resultado.SUCCEEDED,
                AuditTrail.Motivo.TEAM_MEMBERSHIP_CHANGED));
    }
}
