package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.ChannelConnectionLookup;
import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.conversation.api.MensagemRecebidaEvent;
import br.com.pnp.crm.conversation.api.StatusConversa;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Entrada de mensagem, comum a todos os canais.
 *
 * <p>Recebe {@link InboundMessage} já normalizado. Não sabe — e não pode
 * saber — se veio do Telegram, do WhatsApp ou do widget do site. Adaptador que
 * precise de tratamento especial aqui é sinal de que a normalização está
 * incompleta, não de que este serviço precisa de um {@code if}.
 */
@Service
class IngestaoDeMensagemService {

    private static final Logger log = LoggerFactory.getLogger(IngestaoDeMensagemService.class);

    private final ConversationRepository conversas;
    private final MessageRepository mensagens;
    private final ChannelConnectionLookup conexoes;
    private final ApplicationEventPublisher eventos;

    IngestaoDeMensagemService(ConversationRepository conversas,
                              MessageRepository mensagens,
                              ChannelConnectionLookup conexoes,
                              ApplicationEventPublisher eventos) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.conexoes = conexoes;
        this.eventos = eventos;
    }

    /**
     * Registra uma mensagem recebida, criando a conversa se necessário.
     *
     * <p>O tenant é resolvido a partir da conexão, porque neste ponto não há
     * sessão: quem chamou foi um webhook ou um widget. A resolução acontece
     * fora do contexto de tenant e o resto do trabalho acontece dentro dele.
     *
     * @return o id da mensagem, ou vazio quando ela já havia sido recebida
     */
    Optional<UUID> registrar(InboundMessage entrada) {
        UUID tenantId = conexoes.resolverTenantId(entrada.channelConnectionId())
                .orElse(null);

        if (tenantId == null) {
            // Conexão inexistente, inativa ou excluída. Não é erro a propagar:
            // o provedor não tem o que fazer com um 500, e devolver detalhe
            // confirmaria a existência ou não de uma conexão para quem sonda.
            log.warn("Mensagem recebida para conexão desconhecida ou inativa. connectionId={}",
                    entrada.channelConnectionId());
            return Optional.empty();
        }

        return TenantContext.executarComo(tenantId, () -> persistir(tenantId, entrada));
    }

    @Transactional
    Optional<UUID> persistir(UUID tenantId, InboundMessage entrada) {
        // Caminho comum da reentrega: já vimos esta mensagem, saímos cedo e
        // sem publicar evento.
        if (mensagens.findByChannelConnectionIdAndExternalId(
                entrada.channelConnectionId(), entrada.externalId()).isPresent()) {
            return Optional.empty();
        }

        ConversationEntity conversa = localizarOuAbrirConversa(tenantId, entrada);
        conversa.atualizarNomeExibicao(entrada.contactDisplayName());
        conversa.registrarAtividade(entrada.ocorridoEm());
        conversa.reabrirParaAtendimento();

        MessageEntity mensagem = MessageEntity.recebida(
                tenantId,
                conversa.getId(),
                entrada.channelConnectionId(),
                entrada.externalId(),
                entrada.tipoConteudo(),
                entrada.texto(),
                entrada.ocorridoEm(),
                entrada.payload());

        try {
            mensagens.saveAndFlush(mensagem);
        } catch (DataIntegrityViolationException e) {
            // Duas entregas simultâneas do mesmo webhook passaram juntas pela
            // consulta acima. A constraint UNIQUE resolveu a corrida; aqui só
            // reconhecemos que perdemos e tratamos como reentrega.
            log.debug("Mensagem duplicada barrada pela constraint. externalId={}", entrada.externalId());
            return Optional.empty();
        }

        // O evento sai DEPOIS da persistência e só na primeira vez. Quem
        // escuta — o push de tempo real — pode confiar que o dado já existe
        // no banco quando o receber.
        eventos.publishEvent(new MensagemRecebidaEvent(
                tenantId,
                conversa.getId(),
                mensagem.getId(),
                entrada.channelConnectionId(),
                entrada.texto(),
                entrada.ocorridoEm()));

        return Optional.of(mensagem.getId());
    }

    /**
     * O índice único parcial da V2 garante no máximo uma conversa não
     * encerrada por interlocutor. Aqui tratamos a corrida: se outra transação
     * criou a conversa entre a nossa consulta e a nossa inserção, o INSERT
     * falha e nós buscamos de novo em vez de propagar erro — o resultado
     * correto é "use a que existe", não "deu ruim".
     */
    private ConversationEntity localizarOuAbrirConversa(UUID tenantId, InboundMessage entrada) {
        Optional<ConversationEntity> existente = buscarAtiva(entrada);
        if (existente.isPresent()) {
            return existente.get();
        }

        ConversationEntity nova = ConversationEntity.abrir(
                tenantId,
                entrada.channelConnectionId(),
                entrada.externalContactId(),
                entrada.contactDisplayName());
        try {
            return conversas.saveAndFlush(nova);
        } catch (DataIntegrityViolationException e) {
            return buscarAtiva(entrada).orElseThrow(() -> e);
        }
    }

    private Optional<ConversationEntity> buscarAtiva(InboundMessage entrada) {
        return conversas
                .findByChannelConnectionIdAndExternalContactIdAndStatusNotAndDeletedAtIsNull(
                        entrada.channelConnectionId(),
                        entrada.externalContactId(),
                        StatusConversa.CLOSED);
    }
}
