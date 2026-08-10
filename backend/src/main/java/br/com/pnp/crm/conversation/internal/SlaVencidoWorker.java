package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Verificador idempotente; reserva globalmente e processa sob o RLS do tenant. */
@Component
class SlaVencidoWorker {

    private final ReservaSlaVencido reserva;
    private final SlaDeConversaService sla;
    private final TransactionTemplate transacoes;

    SlaVencidoWorker(ReservaSlaVencido reserva, SlaDeConversaService sla,
                     PlatformTransactionManager transactionManager) {
        this.reserva = reserva;
        this.sla = sla;
        this.transacoes = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.conversation.sla.check-interval-ms:30000}")
    void verificar() {
        for (var item : reserva.proximoLote()) {
            TenantContext.executarComo(item.tenantId(), () -> {
                transacoes.executeWithoutResult(ignored ->
                        sla.marcarVencido(item.conversationId(), item.dueAt()));
                return null;
            });
        }
    }

    @Component
    static class ReservaSlaVencido {
        private final EntityManager entityManager;
        private final int tamanhoDoLote;

        ReservaSlaVencido(EntityManager entityManager,
                          @Value("${app.conversation.sla.check-batch-size:100}")
                          int tamanhoDoLote) {
            this.entityManager = entityManager;
            this.tamanhoDoLote = Math.clamp(tamanhoDoLote, 1, 1000);
        }

        @Transactional
        List<Item> proximoLote() {
            List<Tuple> linhas = entityManager.createNativeQuery("""
                    SELECT conversation_id, conversation_tenant_id, conversation_due_at
                      FROM reservar_slas_vencidos(:limit)
                    """, Tuple.class)
                    .setParameter("limit", tamanhoDoLote)
                    .getResultList();
            return linhas.stream().map(linha -> new Item(
                    linha.get(0, UUID.class), linha.get(1, UUID.class),
                    linha.get(2, Instant.class))).toList();
        }
    }

    record Item(UUID conversationId, UUID tenantId, Instant dueAt) {
    }
}
