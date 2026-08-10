package br.com.pnp.crm.automation.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Reserva cross-tenant e devolve apenas ids para reentrada posterior no RLS. */
@Component
class ReservaExecucoesAutomacao {

    private final JdbcTemplate jdbc;
    private final int lote;
    private final int leaseSegundos;
    private final int maxConcorrentesTenant;
    private final String workerId = "automation-" + UUID.randomUUID();

    ReservaExecucoesAutomacao(
            JdbcTemplate jdbc,
            @Value("${app.automation.batch-size:25}") int lote,
            @Value("${app.automation.lease-seconds:30}") int leaseSegundos,
            @Value("${app.automation.max-concurrent-per-tenant:10}")
            int maxConcorrentesTenant) {
        this.jdbc = jdbc;
        this.lote = Math.clamp(lote, 1, 500);
        this.leaseSegundos = Math.clamp(leaseSegundos, 5, 3600);
        this.maxConcorrentesTenant = Math.clamp(maxConcorrentesTenant, 1, 500);
    }

    @Transactional
    List<Reservada> proximoLote() {
        return jdbc.query("""
                SELECT execution_id, execution_tenant_id
                  FROM reservar_execucoes_automacao(?, ?, ?, ?)
                """, (rs, row) -> new Reservada(
                rs.getObject("execution_id", UUID.class),
                rs.getObject("execution_tenant_id", UUID.class)),
                lote, leaseSegundos, maxConcorrentesTenant, workerId);
    }

    int leaseSegundos() {
        return leaseSegundos;
    }

    record Reservada(UUID executionId, UUID tenantId) {
    }
}
