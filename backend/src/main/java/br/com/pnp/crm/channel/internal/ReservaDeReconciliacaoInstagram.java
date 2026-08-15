package br.com.pnp.crm.channel.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = {"app.providers.meta.enabled", "app.providers.meta.reconciliation-enabled"},
        havingValue = "true")
class ReservaDeReconciliacaoInstagram {

    private final EntityManager entityManager;
    private final int tamanhoDoLote;
    private final int intervaloSegundos;

    ReservaDeReconciliacaoInstagram(
            EntityManager entityManager,
            @Value("${app.providers.meta.reconciliation-batch-size:25}") int tamanhoDoLote,
            @Value("${app.providers.meta.reconciliation-interval-seconds:300}")
            int intervaloSegundos) {
        this.entityManager = entityManager;
        this.tamanhoDoLote = tamanhoDoLote;
        this.intervaloSegundos = intervaloSegundos;
    }

    @Transactional
    List<CanalReservado> proximoLote() {
        List<Tuple> linhas = entityManager.createNativeQuery("""
                SELECT connection_id, connection_tenant_id
                  FROM reservar_canais_instagram_para_reconciliar(:limite, :intervalo)
                """, Tuple.class)
                .setParameter("limite", tamanhoDoLote)
                .setParameter("intervalo", intervaloSegundos)
                .getResultList();
        return linhas.stream().map(l -> new CanalReservado(
                l.get(0, UUID.class), l.get(1, UUID.class))).toList();
    }

    record CanalReservado(UUID connectionId, UUID tenantId) {
    }
}
