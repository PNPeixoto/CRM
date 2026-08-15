package br.com.pnp.crm.billing.internal;

import br.com.pnp.crm.billing.api.EntitlementService;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementacao SQL: o Postgres fecha atomicamente a janela de hard limit. */
@Service
class EntitlementServiceJdbc implements EntitlementService {

    private final JdbcTemplate jdbc;

    EntitlementServiceJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public AvaliacaoCapacidade avaliar(String capabilityCode, Instant instante) {
        exigirReferencia(capabilityCode, instante);
        UUID tenantId = TenantContext.obrigatorio();
        return jdbc.query("""
                        SELECT c.available, g.id AS grant_id, g.version_number,
                               g.valid_from, g.valid_until
                          FROM technical_capability c
                     LEFT JOIN LATERAL (
                                SELECT id, version_number, valid_from, valid_until
                                  FROM entitlement_grant
                                 WHERE tenant_id = ?
                                   AND capability_code = c.code
                                   AND valid_from <= ?
                                   AND (valid_until IS NULL OR valid_until > ?)
                                 ORDER BY version_number DESC
                                 LIMIT 1
                               ) g ON true
                         WHERE c.code = ?
                        """, (rs, row) -> new AvaliacaoCapacidade(
                        rs.getBoolean("available")
                                ? DisponibilidadeTecnica.DISPONIVEL
                                : DisponibilidadeTecnica.INDISPONIVEL,
                        rs.getObject("grant_id", UUID.class) == null
                                ? Contratacao.NAO_CONTRATADA
                                : Contratacao.CONTRATADA,
                        rs.getObject("grant_id", UUID.class),
                        rs.getObject("version_number", Integer.class),
                        instante(rs.getTimestamp("valid_from")),
                        instante(rs.getTimestamp("valid_until"))),
                        tenantId, Timestamp.from(instante), Timestamp.from(instante),
                        capabilityCode)
                .stream().findFirst()
                .orElseGet(() -> new AvaliacaoCapacidade(
                        DisponibilidadeTecnica.DESCONHECIDA,
                        Contratacao.NAO_CONTRATADA, null, null, null, null));
    }

    @Override
    @Transactional
    public ResultadoMedicao medir(EventoDeUso evento) {
        var linhas = jdbc.query("""
                        SELECT * FROM registrar_evento_de_uso(?, ?, ?, ?, ?, ?, ?)
                        """, (rs, row) -> new ResultadoMedicao(
                        rs.getObject("usage_event_id", UUID.class),
                        ResultadoRegistro.valueOf(rs.getString("outcome")),
                        rs.getObject("entitlement_grant_id", UUID.class),
                        instante(rs.getTimestamp("window_started_at")),
                        instante(rs.getTimestamp("window_ended_at")),
                        rs.getObject("total_quantity", Long.class)),
                UuidV7.gerar(), evento.metricCode(), evento.quantity(),
                evento.sourceType(), evento.sourceId(), evento.idempotencyKey(),
                Timestamp.from(evento.occurredAt()));
        if (linhas.size() != 1) {
            throw new IllegalStateException("Registro de uso nao produziu resultado unico.");
        }
        return linhas.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgregadoDeUso> agregar(String metricCode, Instant referencia) {
        exigirReferencia(metricCode, referencia);
        UUID tenantId = TenantContext.obrigatorio();
        return jdbc.query("""
                        WITH concessao AS (
                            SELECT g.id, g.version_number, g.valid_from, g.valid_until,
                                   g.window_type, g.time_zone
                              FROM usage_metric m
                              JOIN entitlement_grant g
                                ON g.capability_code = m.capability_code
                             WHERE m.code = ?
                               AND g.tenant_id = ?
                               AND g.valid_from <= ?
                               AND (g.valid_until IS NULL OR g.valid_until > ?)
                             ORDER BY g.version_number DESC
                             LIMIT 1
                        ), janela AS (
                            SELECT c.*,
                                   CASE c.window_type
                                     WHEN 'CALENDAR_DAY' THEN
                                       date_trunc('day', ?::timestamptz AT TIME ZONE c.time_zone)
                                           AT TIME ZONE c.time_zone
                                     WHEN 'CALENDAR_MONTH' THEN
                                       date_trunc('month', ?::timestamptz AT TIME ZONE c.time_zone)
                                           AT TIME ZONE c.time_zone
                                     ELSE c.valid_from
                                   END AS inicio,
                                   CASE c.window_type
                                     WHEN 'CALENDAR_DAY' THEN
                                       (date_trunc('day', ?::timestamptz AT TIME ZONE c.time_zone)
                                           + interval '1 day') AT TIME ZONE c.time_zone
                                     WHEN 'CALENDAR_MONTH' THEN
                                       (date_trunc('month', ?::timestamptz AT TIME ZONE c.time_zone)
                                           + interval '1 month') AT TIME ZONE c.time_zone
                                     ELSE c.valid_until
                                   END AS fim
                              FROM concessao c
                        )
                        SELECT j.id, j.version_number, j.inicio, j.fim,
                               COALESCE(sum(u.quantity), 0) AS total,
                               count(u.id) AS eventos
                          FROM janela j
                     LEFT JOIN usage_event u
                            ON u.tenant_id = ?
                           AND u.entitlement_grant_id = j.id
                           AND u.event_type = ?
                           AND u.occurred_at >= j.inicio
                           AND (j.fim IS NULL OR u.occurred_at < j.fim)
                      GROUP BY j.id, j.version_number, j.inicio, j.fim
                        """, (rs, row) -> new AgregadoDeUso(
                        rs.getObject("id", UUID.class), rs.getInt("version_number"),
                        metricCode, instante(rs.getTimestamp("inicio")),
                        instante(rs.getTimestamp("fim")), rs.getLong("total"),
                        rs.getLong("eventos")),
                        metricCode, tenantId,
                        Timestamp.from(referencia), Timestamp.from(referencia),
                        Timestamp.from(referencia), Timestamp.from(referencia),
                        Timestamp.from(referencia), Timestamp.from(referencia),
                        tenantId, metricCode)
                .stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FonteDeUso> listarFontes(
            UUID entitlementGrantId,
            String metricCode,
            Instant inicioInclusivo,
            Instant fimExclusivo) {
        if (entitlementGrantId == null || inicioInclusivo == null) {
            throw new IllegalArgumentException("Concessao e inicio sao obrigatorios.");
        }
        if (fimExclusivo != null && !fimExclusivo.isAfter(inicioInclusivo)) {
            throw new IllegalArgumentException("Fim precisa ser posterior ao inicio.");
        }
        exigirReferencia(metricCode, inicioInclusivo);
        return jdbc.query("""
                        SELECT id, source_type, source_id, quantity, occurred_at,
                               idempotency_key
                          FROM usage_event
                         WHERE tenant_id = ?
                           AND entitlement_grant_id = ?
                           AND event_type = ?
                           AND occurred_at >= ?
                           AND (?::timestamptz IS NULL OR occurred_at < ?)
                         ORDER BY occurred_at, id
                        """, (rs, row) -> new FonteDeUso(
                        rs.getObject("id", UUID.class), rs.getString("source_type"),
                        rs.getString("source_id"), rs.getLong("quantity"),
                        instante(rs.getTimestamp("occurred_at")),
                        rs.getString("idempotency_key")),
                TenantContext.obrigatorio(), entitlementGrantId, metricCode,
                Timestamp.from(inicioInclusivo), timestamp(fimExclusivo),
                timestamp(fimExclusivo));
    }

    private static void exigirReferencia(String codigo, Instant instante) {
        if (codigo == null || !codigo.matches("^[A-Z][A-Z0-9_]{2,79}$")) {
            throw new IllegalArgumentException("Codigo tecnico invalido.");
        }
        if (instante == null) throw new IllegalArgumentException("Instante e obrigatorio.");
    }

    private static Instant instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
