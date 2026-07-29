package br.com.pnp.crm.conversation.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reserva lotes da fila de saída.
 *
 * <p><b>Bean separado do worker por necessidade, não por organização.</b> O
 * {@code @Transactional} do Spring funciona por proxy: um método chamando
 * outro da <i>mesma</i> classe não passa pelo proxy, e a anotação é ignorada
 * em silêncio. Se a reserva vivesse dentro do worker, ela rodaria na transação
 * do chamador — ou em nenhuma — e os bloqueios das linhas ficariam abertos
 * durante todas as chamadas de rede do lote, que é exatamente o que a
 * transação curta existe para evitar.
 */
@Component
class ReservaDeMensagens {

    private final EntityManager entityManager;
    private final int tamanhoDoLote;
    private final int maxTentativas;
    private final int esperaBaseSegundos;

    ReservaDeMensagens(EntityManager entityManager,
                       @Value("${app.fila-de-saida.tamanho-do-lote:50}") int tamanhoDoLote,
                       @Value("${app.fila-de-saida.max-tentativas:5}") int maxTentativas,
                       @Value("${app.fila-de-saida.espera-base-segundos:30}") int esperaBaseSegundos) {
        this.entityManager = entityManager;
        this.tamanhoDoLote = tamanhoDoLote;
        this.maxTentativas = maxTentativas;
        this.esperaBaseSegundos = esperaBaseSegundos;
    }

    /**
     * Transação curta, encerrada antes de qualquer envio. Mantê-la aberta
     * durante as chamadas de rede seguraria os bloqueios do lote inteiro, e
     * outra instância ficaria esperando em vez de trabalhar em paralelo.
     */
    @Transactional
    List<MensagemReservada> proximoLote() {
        List<Tuple> linhas = entityManager
                .createNativeQuery("""
                        SELECT message_id, message_tenant_id
                          FROM reservar_mensagens_para_envio(:limite, :maxTentativas, :espera)
                        """, Tuple.class)
                .setParameter("limite", tamanhoDoLote)
                .setParameter("maxTentativas", maxTentativas)
                .setParameter("espera", esperaBaseSegundos)
                .getResultList();

        return linhas.stream()
                .map(linha -> new MensagemReservada(
                        linha.get(0, UUID.class), linha.get(1, UUID.class)))
                .toList();
    }

    record MensagemReservada(UUID messageId, UUID tenantId) {
    }
}
