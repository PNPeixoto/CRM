package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.ChannelAdapter;
import br.com.pnp.crm.channel.api.ChannelConnectionLookup;
import br.com.pnp.crm.channel.api.ConexaoDeCanal;
import br.com.pnp.crm.channel.api.EnvioDeMensagemException;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Entrega uma mensagem ao provedor e registra o resultado.
 *
 * <p>Só é chamado pelo worker. Nenhum caminho de requisição HTTP chega aqui —
 * é isso que garante a regra de nunca chamar provedor externo dentro da
 * transação da conversa.
 */
@Service
class EnvioDeMensagemService {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeMensagemService.class);

    /**
     * Texto usado quando a exceção não traz motivo. Nunca a mensagem crua do
     * provedor sem passar pelo adaptador: resposta de API externa pode ecoar o
     * token enviado, e {@code failure_reason} é lido na tela.
     */
    private static final String MOTIVO_DESCONHECIDO = "Falha não detalhada pelo provedor.";

    private final MessageRepository mensagens;
    private final ChannelConnectionLookup conexoes;
    private final Map<TipoCanal, ChannelAdapter> adaptadores;
    private final EventoTempoRealService eventos;
    private final SlaDeConversaService sla;

    EnvioDeMensagemService(MessageRepository mensagens,
                           ChannelConnectionLookup conexoes,
                           List<ChannelAdapter> adaptadores,
                           EventoTempoRealService eventos,
                           SlaDeConversaService sla) {
        this.mensagens = mensagens;
        this.conexoes = conexoes;
        this.eventos = eventos;
        this.sla = sla;
        // Indexa por tipo na construção. Se dois adaptadores declararem o mesmo
        // canal, o Map.toMap lança na subida da aplicação — melhor do que
        // escolher um em silêncio e enviar pelo provedor errado.
        this.adaptadores = adaptadores.stream()
                .collect(java.util.stream.Collectors.toMap(ChannelAdapter::tipo, Function.identity()));
    }

    /**
     * Uma transação por mensagem, de propósito.
     *
     * <p>Um lote inteiro em uma transação faria a falha da última desfazer o
     * registro de sucesso das anteriores — que já saíram para o provedor e não
     * voltam. O resultado seria reenviar mensagens já entregues na próxima
     * rodada.
     */
    @Transactional
    void entregar(UUID messageId) {
        MessageEntity mensagem = mensagens
                .findByIdAndTenantIdAndDeletedAtIsNull(messageId, TenantContext.obrigatorio())
                .orElse(null);
        if (mensagem == null) {
            // Reservada e depois removida, ou o tenant do contexto não confere
            // com o da linha e o RLS a escondeu. Nos dois casos não há o que
            // fazer, e insistir seria consumir tentativas à toa.
            log.warn("Mensagem reservada não encontrada no contexto do tenant. messageId={}", messageId);
            return;
        }

        ConexaoDeCanal conexao = conexoes.buscarAtiva(mensagem.getChannelConnectionId()).orElse(null);
        if (conexao == null) {
            mensagem.marcarFalha("Canal desconectado ou inativo.");
            publicarMudanca(mensagem);
            return;
        }

        ChannelAdapter adaptador = adaptadores.get(conexao.tipo());
        if (adaptador == null) {
            mensagem.marcarFalha("Canal sem adaptador disponível.");
            publicarMudanca(mensagem);
            return;
        }

        try {
            String externalId = adaptador.enviar(new OutboundMessage(
                    mensagem.getId(),
                    conexao.id(),
                    destinatarioDe(mensagem),
                    mensagem.getContentType(),
                    mensagem.getTextContent()));

            mensagem.marcarEnviada(externalId);
            sla.satisfazer(mensagem.getConversationId(), Instant.now());

        } catch (EnvioDeMensagemException e) {
            // O log não inclui o texto da mensagem: conteúdo de cliente não
            // entra em log, por regra do projeto.
            log.warn("Envio recusado pelo provedor. messageId={} permanente={}",
                    messageId, e.isPermanente());
            if (e.isPermanente()) {
                mensagem.marcarFalhaPermanente(motivoDe(e));
            } else {
                mensagem.marcarFalha(motivoDe(e));
                e.tentarNovamenteEm().ifPresent(mensagem::adiar);
            }
        }
        publicarMudanca(mensagem);
    }

    /**
     * O destinatário é o interlocutor da conversa. Buscado aqui e não guardado
     * na mensagem para não duplicar o dado — se o contato for corrigido, o
     * envio seguinte já usa o valor certo.
     */
    private String destinatarioDe(MessageEntity mensagem) {
        return mensagens.buscarExternalContactIdDaConversa(
                        TenantContext.obrigatorio(), mensagem.getConversationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Mensagem sem conversa correspondente: " + mensagem.getId()));
    }

    private String motivoDe(EnvioDeMensagemException e) {
        String motivo = e.getMessage();
        return motivo == null || motivo.isBlank() ? MOTIVO_DESCONHECIDO : motivo;
    }

    private void publicarMudanca(MessageEntity mensagem) {
        mensagens.flush();
        eventos.registrar("MESSAGE_STATUS_CHANGED", mensagem.getConversationId(),
                mensagem.getId(), mensagem.getVersion(), Instant.now());
    }
}
