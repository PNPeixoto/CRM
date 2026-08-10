package br.com.pnp.crm.channel.internal;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Remove o corpo bruto expirado; hash e metadados permanecem. */
@Component
@ConditionalOnProperty(
        name = "app.retencao-de-webhook.habilitada", havingValue = "true", matchIfMissing = true)
class RetencaoDeEventosWorker {

    private final EntityManager entityManager;

    RetencaoDeEventosWorker(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Scheduled(fixedDelayString = "${app.retencao-de-webhook.intervalo-ms:3600000}")
    @Transactional
    void expurgar() {
        entityManager.createNativeQuery(
                        "SELECT expurgar_payloads_eventos_recebidos(:limite)")
                .setParameter("limite", 500)
                .getSingleResult();
    }
}
