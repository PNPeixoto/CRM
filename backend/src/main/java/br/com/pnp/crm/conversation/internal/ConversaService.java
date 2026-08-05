package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

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
    List<ConversationDtos.MensagemResposta> mensagensDa(
            UUID conversationId, Instant cursorCriadoEm, UUID cursorId, int limite) {
        ConversationEntity conversa = carregar(conversationId);
        if (limite < 1 || limite > 100) {
            throw new RequisicaoInvalidaException("O limite deve estar entre 1 e 100.");
        }
        if ((cursorCriadoEm == null) != (cursorId == null)) {
            throw new RequisicaoInvalidaException(
                    "O cursor exige o instante e o identificador da mensagem.");
        }

        List<MessageEntity> pagina = new ArrayList<>(mensagens.buscarPaginaDoHistorico(
                TenantContext.obrigatorio(), conversa.getId(), cursorCriadoEm, cursorId,
                PageRequest.of(0, limite)));
        Collections.reverse(pagina);
        return pagina.stream()
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
    ConversationDtos.MensagemResposta enviar(
            UUID conversationId, String texto, UUID autorId, String idempotencyKey) {
        UUID tenantId = TenantContext.obrigatorio();
        ConversationEntity conversa = carregar(conversationId);

        if (idempotencyKey != null) {
            validarIdempotencyKey(idempotencyKey);
            mensagens.bloquearIdempotencia(lockId(tenantId, conversa.getId(), idempotencyKey));
            var existente = mensagens.findByTenantIdAndConversationIdAndIdempotencyKey(
                    tenantId, conversa.getId(), idempotencyKey);
            if (existente.isPresent()) {
                MessageEntity mensagem = existente.orElseThrow();
                if (!Objects.equals(mensagem.getTextContent(), texto)
                        || !Objects.equals(mensagem.getAuthorUserId(), autorId)) {
                    throw new ChaveIdempotenciaEmConflitoException();
                }
                return paraResposta(mensagem);
            }
        }

        MessageEntity mensagem = MessageEntity.paraEnvio(
                tenantId,
                conversa.getId(),
                conversa.getChannelConnectionId(),
                TipoConteudo.TEXT,
                texto,
                autorId,
                idempotencyKey);

        mensagens.save(mensagem);
        conversa.registrarAtividade(Instant.now());

        return paraResposta(mensagem);
    }

    private static void validarIdempotencyKey(String key) {
        if (!key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new RequisicaoInvalidaException(
                    "A chave de idempotência deve ter de 8 a 128 caracteres seguros.");
        }
    }

    private static long lockId(UUID tenantId, UUID conversationId, String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(conversationId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
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
