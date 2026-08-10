package br.com.pnp.crm.audit.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Component
class AuditWriter {

    private final JdbcTemplate jdbc;

    AuditWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void gravarNoContexto(AuditRecord r) {
        inserir(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void gravarIndependente(AuditRecord r) {
        inserir(r);
    }

    private void inserir(AuditRecord r) {
        jdbc.update("""
                INSERT INTO audit_event (
                    id, tenant_id, occurred_at, actor_type, actor_id,
                    scope_type, scope_id, action_code, target_type, target_id,
                    outcome, reason_code, correlation_id, retention_category,
                    integrity_version, integrity_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, r.id(), r.tenantId(), Timestamp.from(r.occurredAt()),
                r.actorType().name(), r.actorId(),
                r.scopeType().name(), r.scopeId(), r.action().codigo(),
                r.targetType().name(), r.targetId(), r.outcome().name(), r.reason().name(),
                r.correlationId(), r.action().categoriaRetencao(),
                r.integrityVersion(), r.integrityHash());
    }
}
