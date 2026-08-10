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

/** Repara configuracao remota ausente e registra apenas estado sanitizado. */
@Component
@ConditionalOnProperty(
        name = "app.providers.telegram.reconciliation-enabled", havingValue = "true")
class TelegramReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(TelegramReconciliationWorker.class);

    private final ReservaDeReconciliacaoTelegram reserva;
    private final ReconciliacaoIndividual reconciliacao;

    TelegramReconciliationWorker(ReservaDeReconciliacaoTelegram reserva,
                                 ReconciliacaoIndividual reconciliacao) {
        this.reserva = reserva;
        this.reconciliacao = reconciliacao;
    }

    @Scheduled(fixedDelayString = "${app.providers.telegram.reconciliation-schedule-ms:60000}")
    void reconciliar() {
        for (var canal : reserva.proximoLote()) {
            TenantContext.executarComo(canal.tenantId(), () -> {
                try {
                    reconciliacao.executar(canal.connectionId());
                } catch (RuntimeException e) {
                    reconciliacao.marcarErro(canal.connectionId());
                    log.warn("Falha sanitizada na reconciliacao Telegram. connectionId={}",
                            canal.connectionId());
                }
                return null;
            });
        }
    }

    @Component
    static class ReconciliacaoIndividual {

        private final TelegramAdapter adapter;
        private final CredenciaisDeCanal credenciais;
        private final EntityManager entityManager;
        private final TransactionTemplate transacoes;
        private final String basePublica;

        ReconciliacaoIndividual(
                TelegramAdapter adapter, CredenciaisDeCanal credenciais,
                EntityManager entityManager, PlatformTransactionManager transactionManager,
                @Value("${app.providers.telegram.public-base-url:}") String basePublica) {
            this.adapter = adapter;
            this.credenciais = credenciais;
            this.entityManager = entityManager;
            this.transacoes = new TransactionTemplate(transactionManager);
            this.basePublica = removerBarraFinal(basePublica);
        }

        void executar(UUID connectionId) {
            if (basePublica.isBlank() || !basePublica.startsWith("https://")) {
                throw new IllegalStateException("URL publica HTTPS do Telegram nao configurada.");
            }
            String esperada = basePublica + "/api/webhooks/telegram/" + connectionId;
            TelegramAdapter.WebhookState remoto = adapter.obterEstadoWebhook(connectionId);
            boolean reparado = !esperada.equals(remoto.url());
            if (reparado) {
                String segredo = credenciais.recuperar(
                                connectionId, TipoCredencial.TELEGRAM_WEBHOOK_SECRET)
                        .orElseThrow(() -> new IllegalStateException(
                                "Canal sem segredo de webhook configurado."));
                // Reconciliacao nunca descarta updates pendentes.
                adapter.registrarWebhook(connectionId, esperada, segredo, false);
            }
            String status = reparado ? "REPAIRED"
                    : remoto.ultimaFalha() == null ? "HEALTHY" : "DEGRADED";
            transacoes.executeWithoutResult(ignored -> atualizarEstado(
                    connectionId, status, remoto.pendentes(), remoto.ultimaFalha()));
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

        private void atualizarEstado(UUID connectionId, String status, int pendentes,
                                     java.time.Instant ultimaFalha) {
            entityManager.createNativeQuery("""
                    UPDATE channel_connection
                       SET remote_status = :status,
                           remote_pending_count = :pendentes,
                           last_remote_error_at = :ultimaFalha
                     WHERE id = :id AND tenant_id = :tenant
                    """)
                    .setParameter("status", status)
                    .setParameter("pendentes", pendentes)
                    .setParameter("ultimaFalha", ultimaFalha)
                    .setParameter("id", connectionId)
                    .setParameter("tenant", TenantContext.obrigatorio())
                    .executeUpdate();
        }

        private static String removerBarraFinal(String valor) {
            if (valor == null) return "";
            return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
        }
    }
}
