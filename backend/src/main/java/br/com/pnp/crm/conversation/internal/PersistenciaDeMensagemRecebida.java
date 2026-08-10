package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.conversation.api.MensagemRecebidaEvent;
import br.com.pnp.crm.conversation.api.StatusConversa;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Fronteira transacional da ingestão, chamada através do proxy do Spring. */
@Service
class PersistenciaDeMensagemRecebida {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ApplicationEventPublisher events;
    private final EventoTempoRealService eventosTempoReal;
    private final SlaDeConversaService sla;
    private final EntityManager entityManager;

    PersistenciaDeMensagemRecebida(ConversationRepository conversations,
                                   MessageRepository messages,
                                   ApplicationEventPublisher events,
                                   EventoTempoRealService eventosTempoReal,
                                   SlaDeConversaService sla,
                                   EntityManager entityManager) {
        this.conversations = conversations;
        this.messages = messages;
        this.events = events;
        this.eventosTempoReal = eventosTempoReal;
        this.sla = sla;
        this.entityManager = entityManager;
    }

    @Transactional
    Optional<UUID> persist(UUID tenantId, InboundMessage input) {
        serialize("message", tenantId, input.channelConnectionId(), input.externalId());
        if (messages.findByTenantIdAndChannelConnectionIdAndExternalId(
                tenantId, input.channelConnectionId(), input.externalId()).isPresent()) {
            return Optional.empty();
        }

        ConversationEntity conversation = findOrOpen(tenantId, input);
        conversation.atualizarNomeExibicao(input.contactDisplayName());
        conversation.registrarAtividade(input.ocorridoEm());
        conversation.reabrirParaAtendimento();
        sla.iniciarSeNecessario(conversation, input.ocorridoEm());

        MessageEntity message = MessageEntity.recebida(
                tenantId, conversation.getId(), input.channelConnectionId(), input.externalId(),
                input.tipoConteudo(), input.texto(), input.ocorridoEm(), input.payload());
        messages.saveAndFlush(message);

        eventosTempoReal.registrar("MESSAGE_CREATED", conversation.getId(), message.getId(),
                message.getVersion(), input.ocorridoEm());

        events.publishEvent(new MensagemRecebidaEvent(
                tenantId, conversation.getId(), message.getId(), input.channelConnectionId(),
                input.texto(), input.ocorridoEm()));
        return Optional.of(message.getId());
    }

    private ConversationEntity findOrOpen(UUID tenantId, InboundMessage input) {
        serialize("conversation", tenantId, input.channelConnectionId(), input.externalContactId());
        Optional<ConversationEntity> existing = findActive(tenantId, input);
        if (existing.isPresent()) {
            return existing.get();
        }

        ConversationEntity newConversation = ConversationEntity.abrir(
                tenantId, input.channelConnectionId(), input.externalContactId(),
                input.contactDisplayName());
        return conversations.save(newConversation);
    }

    private Optional<ConversationEntity> findActive(UUID tenantId, InboundMessage input) {
        return conversations
                .findByTenantIdAndChannelConnectionIdAndExternalContactIdAndStatusNotAndDeletedAtIsNull(
                        tenantId, input.channelConnectionId(), input.externalContactId(),
                        StatusConversa.CLOSED);
    }

    /**
     * Lock transacional compartilhado por todas as instâncias da aplicação.
     * Uma violação de constraint aborta a transação PostgreSQL mesmo que a
     * exceção Java seja capturada; serializar a chave antes da leitura evita
     * entrar nesse estado e deixa a constraint como barreira final.
     */
    private void serialize(String scope, UUID tenantId, UUID connectionId, String externalId) {
        String key = scope + ':' + tenantId + ':' + connectionId + ':' + externalId;
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:key AS text), 0))")
                .setParameter("key", key)
                .getSingleResult();
    }
}
