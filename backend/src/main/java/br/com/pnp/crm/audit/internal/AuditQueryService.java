package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
class AuditQueryService {

    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    AuditQueryService(JdbcTemplate jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    Pagina listar(UUID actorId, String actionFilter, String outcomeFilter,
                  String encodedCursor, int requestedLimit) {
        UUID tenantId = TenantContext.obrigatorio();
        int limite = Math.min(Math.max(requestedLimit, 1), 100);
        AuditTrail.Acao acao = parseAction(actionFilter);
        AuditTrail.Resultado resultado = parseOutcome(outcomeFilter);
        Cursor cursor = parseCursor(encodedCursor);

        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.AUDIT_READ,
                AuditTrail.Ator.humano(actorId),
                AuditTrail.Escopo.tenant(tenantId),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.AUDIT_TRAIL, null),
                AuditTrail.Resultado.SUCCEEDED,
                AuditTrail.Motivo.PAGE_VIEW));

        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.tenant_id, e.occurred_at, e.actor_type, e.actor_id,
                       u.full_name AS actor_name, e.scope_type, e.scope_id,
                       e.action_code, e.target_type, e.target_id, e.outcome,
                       e.reason_code, e.correlation_id, e.retention_category,
                       e.integrity_version, e.integrity_hash
                  FROM audit_event e
             LEFT JOIN app_user u
                    ON u.tenant_id = e.tenant_id AND u.id = e.actor_id
                 WHERE e.tenant_id = ?
                """);
        List<Object> parametros = new ArrayList<>();
        parametros.add(tenantId);
        if (acao != null) {
            sql.append(" AND e.action_code = ?");
            parametros.add(acao.codigo());
        }
        if (resultado != null) {
            sql.append(" AND e.outcome = ?");
            parametros.add(resultado.name());
        }
        if (cursor != null) {
            sql.append(" AND (e.occurred_at, e.id) < (?, ?)");
            parametros.add(Timestamp.from(cursor.occurredAt()));
            parametros.add(cursor.id());
        }
        sql.append(" ORDER BY e.occurred_at DESC, e.id DESC LIMIT ?");
        parametros.add(limite + 1);

        List<Linha> linhas = jdbc.query(sql.toString(), this::mapear,
                parametros.toArray());
        for (Linha linha : linhas) {
            verificarIntegridade(linha);
        }

        boolean temMais = linhas.size() > limite;
        if (temMais) linhas = new ArrayList<>(linhas.subList(0, limite));
        String proximo = temMais ? encodeCursor(linhas.getLast()) : null;
        List<EventoResponse> eventos = linhas.stream().map(Linha::response).toList();
        return new Pagina(eventos, proximo, true);
    }

    private Linha mapear(ResultSet rs, int rowNum) throws SQLException {
        AuditTrail.Acao action = actionByCode(rs.getString("action_code"));
        AuditRecord record = new AuditRecord(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(),
                AuditTrail.TipoAtor.valueOf(rs.getString("actor_type")),
                rs.getObject("actor_id", UUID.class),
                AuditTrail.TipoEscopo.valueOf(rs.getString("scope_type")),
                rs.getObject("scope_id", UUID.class), action,
                AuditTrail.TipoAlvo.valueOf(rs.getString("target_type")),
                rs.getObject("target_id", UUID.class),
                AuditTrail.Resultado.valueOf(rs.getString("outcome")),
                AuditTrail.Motivo.valueOf(rs.getString("reason_code")),
                rs.getObject("correlation_id", UUID.class),
                rs.getInt("integrity_version"), rs.getString("integrity_hash"));
        return new Linha(record, rs.getString("actor_name"),
                rs.getString("retention_category"));
    }

    private static void verificarIntegridade(Linha linha) {
        boolean categoriaCorreta = linha.retentionCategory()
                .equals(linha.record().action().categoriaRetencao());
        boolean hashCorreto = AuditIntegrity.calcular(linha.record())
                .equals(linha.record().integrityHash());
        if (!categoriaCorreta || !hashCorreto) {
            throw new IllegalStateException("Integridade da auditoria invalida.");
        }
    }

    private static AuditTrail.Acao parseAction(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AuditTrail.Acao.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("Filtro de auditoria invalido.");
        }
    }

    private static AuditTrail.Resultado parseOutcome(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AuditTrail.Resultado.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("Filtro de auditoria invalido.");
        }
    }

    private static AuditTrail.Acao actionByCode(String code) {
        for (AuditTrail.Acao action : AuditTrail.Acao.values()) {
            if (action.codigo().equals(code)) return action;
        }
        throw new IllegalStateException("Acao de auditoria desconhecida.");
    }

    private static Cursor parseCursor(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new RequisicaoInvalidaException("Cursor de auditoria invalido.");
        }
    }

    private static String encodeCursor(Linha linha) {
        String value = linha.record().occurredAt() + "|" + linha.record().id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    record Pagina(List<EventoResponse> eventos, String proximoCursor,
                  boolean integridadeVerificada) {
    }

    record EventoResponse(
            UUID id, Instant ocorridoEm, String acao, String resultado,
            String motivo, String atorTipo, UUID atorId, String atorNome,
            String escopoTipo, UUID escopoId, String alvoTipo, UUID alvoId,
            UUID correlacaoId) {
    }

    private record Linha(AuditRecord record, String actorName, String retentionCategory) {
        EventoResponse response() {
            return new EventoResponse(
                    record.id(), record.occurredAt(), record.action().codigo(),
                    record.outcome().name(), record.reason().name(),
                    record.actorType().name(), record.actorId(), actorName,
                    record.scopeType().name(), record.scopeId(),
                    record.targetType().name(), record.targetId(), record.correlationId());
        }
    }

    private record Cursor(Instant occurredAt, UUID id) {
    }
}
