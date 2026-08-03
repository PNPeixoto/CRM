package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de conversa para o atendente.
 *
 * <p>Todo método parte de {@link TenantContext#obrigatorio()}: se uma chamada
 * chegar aqui sem tenant no contexto, é bug de programação, e falhar alto é
 * melhor que consultar sem isolamento — mesmo com o RLS pronto para devolver
 * vazio.
 */
@Service
class ConversaService {

    private final ConversationRepository conversas;
    private final MessageRepository mensagens;

    ConversaService(ConversationRepository conversas, MessageRepository mensagens) {
        this.conversas = conversas;
        this.mensagens = mensagens;
    }

    @Transactional(readOnly = true)
    List<ConversationDtos.ConversaResumo> listar() {
        UUID tenantId = TenantContext.obrigatorio();
        return conversas.findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(tenantId).stream()
                .map(ConversaService::paraResumo)
                .toList();
    }

    /**
     * Histórico completo de uma conversa. É este endpoint que sustenta a regra
     * de que tempo real é otimização: ao reconectar, o cliente descarta o que
     * tinha e recarrega daqui, em vez de tentar remendar o que perdeu enquanto
     * esteve desconectado.
     */
    @Transactional(readOnly = true)
    List<ConversationDtos.MensagemResposta> mensagensDa(UUID conversationId) {
        ConversationEntity conversa = carregar(conversationId);
        return mensagens.findByTenantIdAndConversationIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        TenantContext.obrigatorio(), conversa.getId())
                .stream()
                .map(ConversaService::paraResposta)
                .toList();
    }

    /**
     * Enfileira uma mensagem do atendente.
     *
     * <p>Grava com status {@code PENDING} e <b>não</b> chama o provedor. A
     * entrega é do worker da fila de saída: chamada de rede dentro desta
     * transação seguraria a conexão do banco pelo tempo do timeout HTTP, e sob
     * carga isso esgota o pool e derruba a aplicação por causa de um provedor
     * lento.
     */
    @Transactional
    ConversationDtos.MensagemResposta enviar(UUID conversationId, String texto, UUID autorId) {
        UUID tenantId = TenantContext.obrigatorio();
        ConversationEntity conversa = carregar(conversationId);

        MessageEntity mensagem = MessageEntity.paraEnvio(
                tenantId,
                conversa.getId(),
                conversa.getChannelConnectionId(),
                TipoConteudo.TEXT,
                texto,
                autorId);

        mensagens.save(mensagem);
        conversa.registrarAtividade(Instant.now());

        return paraResposta(mensagem);
    }

    private ConversationEntity carregar(UUID conversationId) {
        return conversas
                .findByIdAndTenantIdAndDeletedAtIsNull(conversationId, TenantContext.obrigatorio())
                .orElseThrow(ConversaNaoEncontradaException::new);
    }

    private static ConversationDtos.ConversaResumo paraResumo(ConversationEntity entity) {
        return new ConversationDtos.ConversaResumo(
                entity.getId(),
                entity.getChannelConnectionId(),
                entity.getContactDisplayName(),
                entity.getStatus(),
                entity.getAssignedUserId(),
                entity.getLastMessageAt());
    }

    private static ConversationDtos.MensagemResposta paraResposta(MessageEntity entity) {
        return new ConversationDtos.MensagemResposta(
                entity.getId(),
                entity.getDirection(),
                entity.getContentType(),
                entity.getTextContent(),
                entity.getStatus(),
                entity.getAuthorUserId(),
                entity.getCreatedAt());
    }
}
