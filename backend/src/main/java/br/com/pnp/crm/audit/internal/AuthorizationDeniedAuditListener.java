package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.organization.api.AcessoOrganizacionalNegado;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class AuthorizationDeniedAuditListener {

    private final AuditTrail audit;

    AuthorizationDeniedAuditListener(AuditTrail audit) {
        this.audit = audit;
    }

    @EventListener
    void registrar(AcessoOrganizacionalNegado evento) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.AUTHORIZATION_DENIED,
                AuditTrail.Ator.humano(evento.actorId()),
                AuditTrail.Escopo.tenant(evento.tenantId()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.AUTHORIZATION, null),
                AuditTrail.Resultado.DENIED,
                AuditTrail.Motivo.valueOf(evento.motivo().name())));
    }
}
