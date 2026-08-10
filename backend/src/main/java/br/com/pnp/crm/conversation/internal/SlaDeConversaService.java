package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Calcula e registra o prazo de primeira resposta no fuso configurado. */
@Service
class SlaDeConversaService {

    private final EntityManager entityManager;
    private final ConversationRepository conversas;
    private final EventoTempoRealService eventos;
    private final long minutosParaResposta;

    SlaDeConversaService(EntityManager entityManager, ConversationRepository conversas,
                         EventoTempoRealService eventos,
                         @Value("${app.conversation.sla.first-response-minutes:15}")
                         long minutosParaResposta) {
        this.entityManager = entityManager;
        this.conversas = conversas;
        this.eventos = eventos;
        this.minutosParaResposta = Math.clamp(minutosParaResposta, 1, 10_080);
    }

    void iniciarSeNecessario(ConversationEntity conversa, Instant recebidoEm) {
        ZoneId fuso = fusoDoTenant();
        Instant venceEm = recebidoEm.atZone(fuso).plusMinutes(minutosParaResposta).toInstant();
        if (!conversa.iniciarSla(recebidoEm, venceEm)) return;
        conversas.flush();
        registrarEventoSla(conversa.getId(), "STARTED", venceEm, recebidoEm);
        eventos.registrar("SLA_CHANGED", conversa.getId(), null,
                conversa.getVersion(), recebidoEm);
    }

    void satisfazer(UUID conversaId, Instant respondidoEm) {
        ConversationEntity conversa = conversas
                .findByIdAndTenantIdAndDeletedAtIsNull(
                        conversaId, TenantContext.obrigatorio())
                .orElse(null);
        if (conversa == null) return;
        Instant prazo = conversa.concluirSla();
        if (prazo == null) return;
        conversas.flush();
        registrarEventoSla(conversaId, "SATISFIED", prazo, respondidoEm);
        eventos.registrar("SLA_CHANGED", conversaId, null,
                conversa.getVersion(), respondidoEm);
    }

    void marcarVencido(UUID conversaId, Instant prazo) {
        ConversationEntity conversa = conversas
                .findByIdAndTenantIdAndDeletedAtIsNull(
                        conversaId, TenantContext.obrigatorio())
                .orElse(null);
        if (conversa == null || !prazo.equals(conversa.getDueAt())) return;
        int inseridos = registrarEventoSla(conversaId, "BREACHED", prazo, Instant.now());
        if (inseridos > 0) {
            eventos.registrar("SLA_CHANGED", conversaId, null,
                    conversa.getVersion(), Instant.now());
        }
    }

    private int registrarEventoSla(UUID conversaId, String tipo,
                                   Instant prazo, Instant ocorridoEm) {
        return entityManager.createNativeQuery("""
                INSERT INTO conversation_sla_event (
                    id, tenant_id, conversation_id, event_type, due_at, occurred_at
                ) VALUES (:id, :tenant, :conversation, :type, :dueAt, :occurredAt)
                ON CONFLICT (tenant_id, conversation_id, event_type, due_at) DO NOTHING
                """)
                .setParameter("id", UuidV7.gerar())
                .setParameter("tenant", TenantContext.obrigatorio())
                .setParameter("conversation", conversaId)
                .setParameter("type", tipo)
                .setParameter("dueAt", prazo)
                .setParameter("occurredAt", ocorridoEm)
                .executeUpdate();
    }

    private ZoneId fusoDoTenant() {
        List<String> fusos = entityManager.createNativeQuery("""
                SELECT time_zone FROM tenant_profile WHERE tenant_id = :tenant
                """, String.class)
                .setParameter("tenant", TenantContext.obrigatorio())
                .getResultList();
        String valor = fusos.isEmpty() ? "America/Sao_Paulo" : fusos.getFirst();
        try {
            return ZoneId.of(valor);
        } catch (DateTimeException e) {
            throw new IllegalStateException("Fuso do tenant invalido.");
        }
    }
}
