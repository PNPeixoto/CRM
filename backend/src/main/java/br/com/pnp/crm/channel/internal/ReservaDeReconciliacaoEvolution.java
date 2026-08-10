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
@ConditionalOnProperty(name = "app.providers.evolution.enabled", havingValue = "true")
class ReservaDeReconciliacaoEvolution {

    private final EntityManager entityManager;
    private final int tamanhoDoLote;
    private final int intervaloSegundos;

    ReservaDeReconciliacaoEvolution(
            EntityManager entityManager,
            @Value("${app.providers.evolution.reconciliation-batch-size:10}") int tamanhoDoLote,
            @Value("${app.providers.evolution.reconciliation-interval-seconds:30}")
            int intervaloSegundos) {
        this.entityManager = entityManager;
        this.tamanhoDoLote = tamanhoDoLote;
        this.intervaloSegundos = intervaloSegundos;
    }

    @Transactional
    List<CanalReservado> proximoLote() {
        List<Tuple> linhas = entityManager.createNativeQuery("""
                SELECT connection_id, connection_tenant_id
                  FROM reservar_canais_evolution_para_reconciliar(:limite, :intervalo)
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
