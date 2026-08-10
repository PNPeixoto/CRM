package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/** Repara o webhook e observa a conexao experimental da Evolution. */
@Component
@ConditionalOnProperty(name = "app.providers.evolution.enabled", havingValue = "true")
class EvolutionReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(EvolutionReconciliationWorker.class);

    private final ReservaDeReconciliacaoEvolution reserva;
    private final ReconciliacaoIndividual reconciliacao;

    EvolutionReconciliationWorker(ReservaDeReconciliacaoEvolution reserva,
                                  ReconciliacaoIndividual reconciliacao) {
        this.reserva = reserva;
        this.reconciliacao = reconciliacao;
    }

    @Scheduled(fixedDelayString = "${app.providers.evolution.reconciliation-schedule-ms:10000}")
    void reconciliar() {
        for (var canal : reserva.proximoLote()) {
            TenantContext.executarComo(canal.tenantId(), () -> {
                try {
                    reconciliacao.executar(canal.connectionId());
                } catch (RuntimeException e) {
                    reconciliacao.marcarErro(canal.connectionId());
                    log.warn("Falha sanitizada na reconciliacao Evolution. connectionId={}",
                            canal.connectionId());
                }
                return null;
            });
        }
    }

    @Component
    @ConditionalOnProperty(name = "app.providers.evolution.enabled", havingValue = "true")
    static class ReconciliacaoIndividual {

        private final EvolutionAdapter adapter;
        private final CredenciaisDeCanal credenciais;
        private final EntityManager entityManager;
        private final TransactionTemplate transacoes;
        private final String baseWebhook;

        ReconciliacaoIndividual(
                EvolutionAdapter adapter, CredenciaisDeCanal credenciais,
                EntityManager entityManager, PlatformTransactionManager transactionManager,
                @Value("${app.providers.evolution.webhook-base-url:http://app:8080/api/webhooks/evolution}")
                String baseWebhook) {
            this.adapter = adapter;
            this.credenciais = credenciais;
            this.entityManager = entityManager;
            this.transacoes = new TransactionTemplate(transactionManager);
            this.baseWebhook = removerBarraFinal(baseWebhook);
        }

        void executar(UUID connectionId) {
            String esperada = baseWebhook + '/' + connectionId;
            EvolutionAdapter.EstadoRemoto remoto = adapter.obterEstado(connectionId);
            boolean reparado = !remoto.webhookAtivo() || !esperada.equals(remoto.webhookUrl());
            if (reparado) {
                String segredo = credenciais.recuperar(
                                connectionId, TipoCredencial.EVOLUTION_WEBHOOK_SECRET)
                        .orElseThrow(() -> new IllegalStateException(
                                "Canal Evolution sem segredo de webhook."));
                adapter.registrarWebhook(connectionId, esperada, segredo);
            }
            boolean conectado = "open".equalsIgnoreCase(remoto.estadoDaConexao());
            String status = conectado ? (reparado ? "REPAIRED" : "HEALTHY") : "DEGRADED";
            transacoes.executeWithoutResult(ignored -> atualizarEstado(connectionId, status));
        }

        void marcarErro(UUID connectionId) {
            transacoes.executeWithoutResult(ignored -> entityManager.createNativeQuery("""
                    UPDATE channel_connection SET remote_status = 'ERROR'
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

        private static String removerBarraFinal(String valor) {
            if (valor == null) return "";
            String limpo = valor.trim();
            return limpo.endsWith("/") ? limpo.substring(0, limpo.length() - 1) : limpo;
        }
    }
}
