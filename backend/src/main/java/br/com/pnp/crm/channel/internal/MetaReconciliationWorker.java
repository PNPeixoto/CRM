package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;

/** Confere e repara a assinatura de mensagens da conta profissional. */
@Component
@ConditionalOnProperty(
        name = {"app.providers.meta.enabled", "app.providers.meta.reconciliation-enabled"},
        havingValue = "true")
class MetaReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(MetaReconciliationWorker.class);

    private final ReservaDeReconciliacaoInstagram reserva;
    private final ReconciliacaoIndividual reconciliacao;

    MetaReconciliationWorker(ReservaDeReconciliacaoInstagram reserva,
                             ReconciliacaoIndividual reconciliacao) {
        this.reserva = reserva;
        this.reconciliacao = reconciliacao;
    }

    @Scheduled(fixedDelayString = "${app.providers.meta.reconciliation-schedule-ms:60000}")
    void reconciliar() {
        for (var canal : reserva.proximoLote()) {
            TenantContext.executarComo(canal.tenantId(), () -> {
                try {
                    reconciliacao.executar(canal.connectionId());
                } catch (RuntimeException e) {
                    reconciliacao.marcarErro(canal.connectionId());
                    log.warn("Falha sanitizada na reconciliacao Instagram. connectionId={}",
                            canal.connectionId());
                }
                return null;
            });
        }
    }

    @Component
    @ConditionalOnProperty(
            name = {"app.providers.meta.enabled", "app.providers.meta.reconciliation-enabled"},
            havingValue = "true")
    static class ReconciliacaoIndividual {

        private final InstagramAdapter adapter;
        private final EntityManager entityManager;
        private final TransactionTemplate transacoes;

        ReconciliacaoIndividual(InstagramAdapter adapter, EntityManager entityManager,
                                PlatformTransactionManager transactionManager) {
            this.adapter = adapter;
            this.entityManager = entityManager;
            this.transacoes = new TransactionTemplate(transactionManager);
        }

        void executar(UUID connectionId) {
            Set<String> assinaturas = adapter.obterAssinaturas(connectionId);
            boolean reparado = !assinaturas.contains("messages");
            if (reparado) adapter.registrarAssinaturas(connectionId);
            transacoes.executeWithoutResult(ignored -> atualizarEstado(
                    connectionId, reparado ? "REPAIRED" : "HEALTHY"));
        }

        void marcarErro(UUID connectionId) {
            transacoes.executeWithoutResult(ignored -> entityManager.createNativeQuery("""
                    UPDATE channel_connection
                       SET remote_status = 'ERROR', last_remote_error_at = now()
                     WHERE id = :id AND tenant_id = :tenant
                    """)
                    .setParameter("id", connectionId)
                    .setParameter("tenant", TenantContext.obrigatorio())
                    .executeUpdate());
        }

        private void atualizarEstado(UUID connectionId, String status) {
            entityManager.createNativeQuery("""
                    UPDATE channel_connection
                       SET remote_status = :status,
                           remote_pending_count = 0,
                           last_remote_error_at = NULL
                     WHERE id = :id AND tenant_id = :tenant
                    """)
                    .setParameter("status", status)
                    .setParameter("id", connectionId)
                    .setParameter("tenant", TenantContext.obrigatorio())
                    .executeUpdate();
        }
    }
}
