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
    private final EventoTempoRealService eventos;

    ConversaService(ConversationRepository conversas, MessageRepository mensagens,
                    EventoTempoRealService eventos) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    ConversationDtos.PaginaConversas listar(
            Instant cursorEm, UUID cursorId, int limite, String busca) {
        if (limite < 1 || limite > 100) {
            throw new RequisicaoInvalidaException("O limite deve estar entre 1 e 100.");
        }
        if ((cursorEm == null) != (cursorId == null)) {
            throw new RequisicaoInvalidaException(
                    "O cursor exige o instante e o identificador da conversa.");
        }
        String buscaNormalizada = normalizarBusca(busca);
        UUID tenantId = TenantContext.obrigatorio();
        PageRequest pagina = PageRequest.of(0, limite + 1);
        List<ConversationEntity> encontradas = new ArrayList<>(cursorEm == null
                ? conversas.buscarPrimeiraPagina(tenantId, buscaNormalizada, pagina)
                : conversas.buscarDepoisDoCursor(
                        tenantId, buscaNormalizada, cursorEm, cursorId, pagina));
        boolean temMais = encontradas.size() > limite;
        if (temMais) encontradas = new ArrayList<>(encontradas.subList(0, limite));
        ConversationEntity ultima = encontradas.isEmpty() ? null : encontradas.getLast();
        List<ConversationDtos.ConversaResumo> itens = encontradas.stream()
                .map(ConversaService::paraResumo)
                .toList();
        return new ConversationDtos.PaginaConversas(
                itens,
                ultima == null ? null : momentoDaOrdem(ultima),
                ultima == null ? null : ultima.getId(),
                temMais,
                eventos.sequenciaAtual());
    }

    /**
     * Histórico completo de uma conversa. É este endpoint que sustenta a regra
     * de que tempo real é otimização: ao reconectar, o cliente descarta o que
     * tinha e recarrega daqui, em vez de tentar remendar o que perdeu enquanto
     * esteve desconectado.
     */
    @Transactional(readOnly = true)
    ConversationDtos.PaginaMensagens mensagensDa(
            UUID conversationId, Instant cursorCriadoEm, UUID cursorId, int limite) {
        ConversationEntity conversa = carregar(conversationId);
        if (limite < 1 || limite > 100) {
            throw new RequisicaoInvalidaException("O limite deve estar entre 1 e 100.");
        }
        if ((cursorCriadoEm == null) != (cursorId == null)) {
            throw new RequisicaoInvalidaException(
                    "O cursor exige o instante e o identificador da mensagem.");
        }

        PageRequest tamanhoDaPagina = PageRequest.of(0, limite + 1);
        List<MessageEntity> pagina = new ArrayList<>(cursorCriadoEm == null
                ? mensagens
                        .findByTenantIdAndConversationIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                                TenantContext.obrigatorio(), conversa.getId(), tamanhoDaPagina)
                : mensagens.buscarPaginaAnteriorDoHistorico(
                        TenantContext.obrigatorio(), conversa.getId(), cursorCriadoEm, cursorId,
                        tamanhoDaPagina));
        boolean temMais = pagina.size() > limite;
        if (temMais) pagina = new ArrayList<>(pagina.subList(0, limite));
        MessageEntity maisAntiga = pagina.isEmpty() ? null : pagina.getLast();
        Collections.reverse(pagina);
        List<ConversationDtos.MensagemResposta> itens = pagina.stream()
                .map(ConversaService::paraResposta)
                .toList();
        return new ConversationDtos.PaginaMensagens(
                itens,
                maisAntiga == null ? null : maisAntiga.getCreatedAt(),
                maisAntiga == null ? null : maisAntiga.getId(),
                temMais,
                eventos.sequenciaAtual());
    }

    @Transactional(readOnly = true)
    ConversationDtos.PaginaEventos eventosDepoisDe(long apos, int limite) {
        return eventos.listarDepois(apos, limite);
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

        mensagens.saveAndFlush(mensagem);
        conversa.registrarAtividade(Instant.now());
        eventos.registrar("MESSAGE_CREATED", conversa.getId(), mensagem.getId(),
                mensagem.getVersion(), mensagem.getCreatedAt());

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
                entity.getLastMessageAt(),
                entity.getDueAt(),
                entity.getVersion());
    }

    private static ConversationDtos.MensagemResposta paraResposta(MessageEntity entity) {
        return new ConversationDtos.MensagemResposta(
                entity.getId(),
                entity.getDirection(),
                entity.getContentType(),
                entity.getTextContent(),
                entity.getStatus(),
                entity.getAuthorUserId(),
                entity.getCreatedAt(),
                entity.getVersion());
    }

    private static Instant momentoDaOrdem(ConversationEntity conversa) {
        return conversa.getLastMessageAt() == null
                ? conversa.getCreatedAt() : conversa.getLastMessageAt();
    }

    private static String normalizarBusca(String busca) {
        if (busca == null || busca.isBlank()) return null;
        String limpa = busca.trim().toLowerCase(java.util.Locale.ROOT);
        if (limpa.length() > 120) {
            throw new RequisicaoInvalidaException("A busca deve ter no maximo 120 caracteres.");
        }
        return '%' + limpa.replace("%", "\\%").replace("_", "\\_") + '%';
    }
}
