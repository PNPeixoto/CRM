package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.report-export.worker-enabled", havingValue = "true", matchIfMissing = true)
class ReportExportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportExportWorker.class);
    private final ReportExportQueue fila;
    private final ReportExportService exportacoes;

    ReportExportWorker(ReportExportQueue fila, ReportExportService exportacoes) {
        this.fila = fila;
        this.exportacoes = exportacoes;
    }

    @Scheduled(fixedDelayString = "${app.report-export.worker-interval-ms:2000}")
    void processar() {
        for (ReportExportQueue.Reservada reservada : fila.reservar()) {
            TenantContext.executarComo(reservada.tenantId(), () -> {
                try {
                    exportacoes.processar(reservada.exportId());
                } catch (RuntimeException e) {
                    log.error("Falha sanitizada em exportacao. exportId={} tenant={}",
                            reservada.exportId(), reservada.tenantId());
                    exportacoes.falhar(reservada.exportId());
                }
                return null;
            });
        }
        expurgar();
    }

    private void expurgar() {
        for (ReportExportQueue.Reservada reservada : fila.paraExpurgo()) {
            TenantContext.executarComo(reservada.tenantId(), () -> {
                try {
                    exportacoes.expurgar(reservada.exportId());
                } catch (RuntimeException e) {
                    log.warn("Falha sanitizada no expurgo de exportacao. exportId={} tenant={}",
                            reservada.exportId(), reservada.tenantId());
                }
                return null;
            });
        }
    }
}
