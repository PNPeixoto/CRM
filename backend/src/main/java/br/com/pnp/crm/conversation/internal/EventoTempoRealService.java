package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.conversation.api.AtualizacaoTempoRealEvent;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Livro de eventos usado para recuperar exatamente o que o socket pode perder. */
@Service
class EventoTempoRealService {

    private final EntityManager entityManager;
    private final ApplicationEventPublisher publisher;

    EventoTempoRealService(EntityManager entityManager, ApplicationEventPublisher publisher) {
        this.entityManager = entityManager;
        this.publisher = publisher;
    }

    AtualizacaoTempoRealEvent registrar(String tipo, UUID conversaId, UUID mensagemId,
                                        long versao, Instant ocorridoEm) {
        UUID tenantId = TenantContext.obrigatorio();
        long sequencia = ((Number) entityManager.createNativeQuery(
                        "SELECT next_realtime_sequence(:tenant)")
                .setParameter("tenant", tenantId)
                .getSingleResult()).longValue();
        UUID id = UuidV7.gerar();
        entityManager.createNativeQuery("""
                INSERT INTO realtime_event (
                    id, tenant_id, sequence, event_type, conversation_id,
                    message_id, resource_version, occurred_at
                ) VALUES (:id, :tenant, :sequence, :type, :conversation,
                          :message, :version, :occurredAt)
                """)
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("sequence", sequencia)
                .setParameter("type", tipo)
                .setParameter("conversation", conversaId)
                .setParameter("message", mensagemId)
                .setParameter("version", versao)
                .setParameter("occurredAt", ocorridoEm)
                .executeUpdate();

        var evento = new AtualizacaoTempoRealEvent(
                tenantId, id, sequencia, tipo, conversaId, mensagemId, versao, ocorridoEm);
        publisher.publishEvent(evento);
        return evento;
    }

    @Transactional(readOnly = true)
    long sequenciaAtual() {
        List<Long> valores = entityManager.createNativeQuery("""
                SELECT last_sequence
                  FROM realtime_stream_cursor
                 WHERE tenant_id = :tenant
                """, Long.class)
                .setParameter("tenant", TenantContext.obrigatorio())
                .getResultList();
        return valores.isEmpty() ? 0 : valores.getFirst();
    }

    @Transactional(readOnly = true)
    ConversationDtos.PaginaEventos listarDepois(long apos, int limite) {
        if (apos < 0) {
            throw new br.com.pnp.crm.shared.api.RequisicaoInvalidaException(
                    "A sequencia deve ser maior ou igual a zero.");
        }
        if (limite < 1 || limite > 200) {
            throw new br.com.pnp.crm.shared.api.RequisicaoInvalidaException(
                    "O limite de eventos deve estar entre 1 e 200.");
        }
        UUID tenantId = TenantContext.obrigatorio();
        long atual = sequenciaAtual();
        Number minima = (Number) entityManager.createNativeQuery("""
                SELECT min(sequence) FROM realtime_event WHERE tenant_id = :tenant
                """)
                .setParameter("tenant", tenantId)
                .getSingleResult();
        long primeiraDisponivel = minima == null ? atual + 1 : minima.longValue();
        boolean resetObrigatorio = apos > 0 && apos < primeiraDisponivel - 1;
        if (resetObrigatorio) {
            return new ConversationDtos.PaginaEventos(
                    List.of(), atual, false, true);
        }

        @SuppressWarnings("unchecked")
        List<Tuple> linhas = entityManager.createNativeQuery("""
                SELECT id, sequence, event_type, conversation_id, message_id,
                       resource_version, occurred_at
                  FROM realtime_event
                 WHERE tenant_id = :tenant AND sequence > :after
                 ORDER BY sequence
                 LIMIT :limit
                """, Tuple.class)
                .setParameter("tenant", tenantId)
                .setParameter("after", apos)
                .setParameter("limit", limite + 1)
                .getResultList();
        boolean temMais = linhas.size() > limite;
        if (temMais) linhas = linhas.subList(0, limite);
        List<ConversationDtos.EventoResposta> eventos = linhas.stream()
                .map(linha -> new ConversationDtos.EventoResposta(
                        linha.get(0, UUID.class),
                        linha.get(1, Long.class),
                        linha.get(2, String.class),
                        linha.get(3, UUID.class),
                        linha.get(4, UUID.class),
                        linha.get(5, Long.class),
                        linha.get(6, Instant.class)))
                .toList();
        long ultimo = eventos.isEmpty() ? apos : eventos.getLast().sequence();
        return new ConversationDtos.PaginaEventos(eventos, ultimo, temMais, false);
    }
}
