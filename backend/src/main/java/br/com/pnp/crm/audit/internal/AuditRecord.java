package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.audit.api.AuditTrail;

import java.time.Instant;
import java.util.UUID;

record AuditRecord(
        UUID id,
        UUID tenantId,
        Instant occurredAt,
        AuditTrail.TipoAtor actorType,
        UUID actorId,
        AuditTrail.TipoEscopo scopeType,
        UUID scopeId,
        AuditTrail.Acao action,
        AuditTrail.TipoAlvo targetType,
        UUID targetId,
        AuditTrail.Resultado outcome,
        AuditTrail.Motivo reason,
        UUID correlationId,
        int integrityVersion,
        String integrityHash) {
}

