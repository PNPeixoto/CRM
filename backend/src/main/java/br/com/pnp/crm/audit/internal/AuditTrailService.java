package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
class AuditTrailService implements AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);
    private final AuditWriter writer;

    AuditTrailService(AuditWriter writer) {
        this.writer = writer;
    }

    @Override
    public void registrar(Evento evento) {
        AuditRecord base = new AuditRecord(
                UuidV7.gerar(), TenantContext.obrigatorio(),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                evento.ator().tipo(), evento.ator().id(),
                evento.escopo().tipo(), evento.escopo().id(), evento.acao(),
                evento.alvo().tipo(), evento.alvo().id(), evento.resultado(),
                evento.motivo(), correlationId(), 1, null);
        AuditRecord completo = new AuditRecord(
                base.id(), base.tenantId(), base.occurredAt(), base.actorType(), base.actorId(),
                base.scopeType(), base.scopeId(), base.action(), base.targetType(),
                base.targetId(), base.outcome(), base.reason(), base.correlationId(),
                base.integrityVersion(), AuditIntegrity.calcular(base));

        if (evento.acao().melhorEsforco()) {
            try {
                writer.gravarIndependente(completo);
            } catch (RuntimeException e) {
                // Nunca inclui mensagem da excecao: drivers podem incorporar SQL
                // ou valores. Os metadados abaixo sao todos canonicos.
                log.warn("Auditoria best-effort indisponivel. acao={} tenant={} correlacao={}",
                        evento.acao().codigo(), completo.tenantId(), completo.correlationId());
            }
            return;
        }

        try {
            writer.gravarNoContexto(completo);
        } catch (RuntimeException e) {
            throw new AuditUnavailableException(e);
        }
    }

    private static UUID correlationId() {
        try {
            return UUID.fromString(MDC.get("correlationId"));
        } catch (RuntimeException ignored) {
            return UUID.randomUUID();
        }
    }
}
