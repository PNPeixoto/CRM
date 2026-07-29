package br.com.pnp.crm.channel.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reserva lotes de eventos de webhook para processar.
 *
 * <p>Bean separado do worker pelo mesmo motivo de {@code ReservaDeMensagens}:
 * {@code @Transactional} funciona por proxy, e um método chamado de dentro da
 * própria classe não passa por ele — a anotação seria ignorada em silêncio.
 */
@Component
class ReservaDeEventos {

    private final EntityManager entityManager;
    private final int tamanhoDoLote;
    private final int maxTentativas;
    private final int esperaBaseSegundos;

    ReservaDeEventos(EntityManager entityManager,
                     @Value("${app.entrada-de-webhook.tamanho-do-lote:50}") int tamanhoDoLote,
                     @Value("${app.entrada-de-webhook.max-tentativas:5}") int maxTentativas,
                     @Value("${app.entrada-de-webhook.espera-base-segundos:15}") int esperaBaseSegundos) {
        this.entityManager = entityManager;
        this.tamanhoDoLote = tamanhoDoLote;
        this.maxTentativas = maxTentativas;
        this.esperaBaseSegundos = esperaBaseSegundos;
    }

    @Transactional
    List<EventoReservado> proximoLote() {
        List<Tuple> linhas = entityManager
                .createNativeQuery("""
                        SELECT event_id, event_tenant_id
                          FROM reservar_eventos_para_processar(:limite, :maxTentativas, :espera)
                        """, Tuple.class)
                .setParameter("limite", tamanhoDoLote)
                .setParameter("maxTentativas", maxTentativas)
                .setParameter("espera", esperaBaseSegundos)
                .getResultList();

        return linhas.stream()
                .map(linha -> new EventoReservado(
                        linha.get(0, UUID.class), linha.get(1, UUID.class)))
                .toList();
    }

    record EventoReservado(UUID eventId, UUID tenantId) {
    }
}
