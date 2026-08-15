package br.com.pnp.crm.report.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Reserva global devolvendo somente ids; dados sao lidos depois sob RLS. */
@Component
class ReportExportQueue {

    private final JdbcTemplate jdbc;
    private final int lote;
    private final int leaseSegundos;
    private final int maxPorTenant;
    private final int maxTentativas;
    private final String workerId = "report-export-" + UUID.randomUUID();

    ReportExportQueue(
            JdbcTemplate jdbc,
            @Value("${app.report-export.batch-size:10}") int lote,
            @Value("${app.report-export.lease-seconds:60}") int leaseSegundos,
            @Value("${app.report-export.max-concurrent-per-tenant:2}") int maxPorTenant,
            @Value("${app.report-export.max-attempts:3}") int maxTentativas) {
        this.jdbc = jdbc;
        this.lote = Math.clamp(lote, 1, 200);
        this.leaseSegundos = Math.clamp(leaseSegundos, 5, 3600);
        this.maxPorTenant = Math.clamp(maxPorTenant, 1, 50);
        this.maxTentativas = Math.clamp(maxTentativas, 1, 20);
    }

    @Transactional
    List<Reservada> reservar() {
        return jdbc.query("""
                SELECT export_id, export_tenant_id
                  FROM reservar_exportacoes_relatorio(?, ?, ?, ?, ?)
                """, (rs, row) -> new Reservada(
                rs.getObject("export_id", UUID.class),
                rs.getObject("export_tenant_id", UUID.class)),
                lote, leaseSegundos, maxPorTenant, maxTentativas, workerId);
    }

    @Transactional(readOnly = true)
    List<Reservada> paraExpurgo() {
        return jdbc.query("""
                SELECT export_id, export_tenant_id
                  FROM listar_exportacoes_para_expurgo(?)
                """, (rs, row) -> new Reservada(
                rs.getObject("export_id", UUID.class),
                rs.getObject("export_tenant_id", UUID.class)), lote);
    }

    record Reservada(UUID exportId, UUID tenantId) {
    }
}
