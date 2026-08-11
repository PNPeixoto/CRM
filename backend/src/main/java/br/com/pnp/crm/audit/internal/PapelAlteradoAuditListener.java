package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.organization.api.PapelAlterado;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registra mudanca de papel na trilha corporativa.
 *
 * <p>Papel e superficie de privilegio: alteracao sem rastro impede reconstruir
 * depois quem podia o que, e quando. Roda na transacao de quem publicou, entao
 * a linha de auditoria confirma junto com a mudanca — ou nenhuma das duas
 * confirma.
 */
@Component
class PapelAlteradoAuditListener {

    private final AuditTrail audit;

    PapelAlteradoAuditListener(AuditTrail audit) {
        this.audit = audit;
    }

    @EventListener
    void registrar(PapelAlterado evento) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.ROLE_CHANGED,
                AuditTrail.Ator.humano(evento.actorId()),
                AuditTrail.Escopo.tenant(evento.tenantId()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.ROLE, evento.roleId()),
                AuditTrail.Resultado.SUCCEEDED,
                motivo(evento.mudanca())));
    }

    private static AuditTrail.Motivo motivo(PapelAlterado.Mudanca mudanca) {
        return switch (mudanca) {
            case CRIADO -> AuditTrail.Motivo.ROLE_CREATED;
            case ATUALIZADO, PERMISSOES_ALTERADAS -> AuditTrail.Motivo.ROLE_UPDATED;
            case REMOVIDO -> AuditTrail.Motivo.ROLE_REMOVED;
            case ATRIBUIDO, REVOGADO -> AuditTrail.Motivo.ROLE_ASSIGNMENT_CHANGED;
        };
    }
}
