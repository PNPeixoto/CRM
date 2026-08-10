package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.channel.api.MensagemNormalizadaEvent;
import br.com.pnp.crm.channel.api.TipoCanal;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa os eventos de webhook fora do request.
 *
 * <p>Esta separação é o coração do padrão de recepção: o request só grava e
 * responde 200; toda interpretação acontece aqui. Provedor que não recebe 200
 * rápido reenvia, e depois de algumas falhas desativa o webhook — o canal cai
 * inteiro por causa de um payload que o tradutor não esperava.
 *
 * <p>Sobrevive a reinício porque o estado está no banco, e não em uma fila em
 * memória. Um {@code @Async} simples perderia todo evento em voo no momento de
 * um deploy.
 */
@Component
@ConditionalOnProperty(
        name = "app.entrada-de-webhook.habilitada", havingValue = "true", matchIfMissing = true)
class EntradaDeWebhookWorker {

    private static final Logger log = LoggerFactory.getLogger(EntradaDeWebhookWorker.class);

    private final ReservaDeEventos reserva;
    private final ProcessamentoDeEvento processamento;

    EntradaDeWebhookWorker(ReservaDeEventos reserva, ProcessamentoDeEvento processamento) {
        this.reserva = reserva;
        this.processamento = processamento;
    }

    @Scheduled(fixedDelayString = "${app.entrada-de-webhook.intervalo-ms:1000}")
    void processarLote() {
        List<ReservaDeEventos.EventoReservado> reservados = reserva.proximoLote();
        if (reservados.isEmpty()) {
            return;
        }

        for (ReservaDeEventos.EventoReservado reservado : reservados) {
            TenantContext.executarComo(reservado.tenantId(), () -> {
                try {
                    processamento.processar(reservado.eventId());
                } catch (RuntimeException e) {
                    // Um evento problemático não interrompe o lote. A tentativa
                    // já foi contada na reserva; depois do teto, ele para de
                    // voltar e fica em inbound_event para investigação.
                    log.error("Falha sanitizada ao processar webhook. eventId={} tipo={}",
                            reservado.eventId(), e.getClass().getSimpleName());
                }
                return null;
            });
        }
    }

    /**
     * Transação por evento. Um lote inteiro em uma transação faria a falha do
     * último desfazer o processamento dos anteriores — que já publicaram
     * mensagem na tela do atendente.
     */
    @Component
    static class ProcessamentoDeEvento {

        private static final Logger log = LoggerFactory.getLogger(ProcessamentoDeEvento.class);

        private final InboundEventRepository eventos;
        private final ChannelConnectionLookupImpl conexoes;
        private final TradutorDeUpdateTelegram tradutorTelegram;
        private final TradutorDeWebhookEvolution tradutorEvolution;
        private final CofreDeCredenciais cofre;
        private final TelegramMediaService midiasTelegram;
        private final ApplicationEventPublisher publicador;

        ProcessamentoDeEvento(InboundEventRepository eventos,
                              ChannelConnectionLookupImpl conexoes,
                              TradutorDeUpdateTelegram tradutorTelegram,
                              TradutorDeWebhookEvolution tradutorEvolution,
                              CofreDeCredenciais cofre,
                              TelegramMediaService midiasTelegram,
                              ApplicationEventPublisher publicador) {
            this.eventos = eventos;
            this.conexoes = conexoes;
            this.tradutorTelegram = tradutorTelegram;
            this.tradutorEvolution = tradutorEvolution;
            this.cofre = cofre;
            this.midiasTelegram = midiasTelegram;
            this.publicador = publicador;
        }

        @Transactional
        void processar(UUID eventId) {
            InboundEventEntity evento = eventos
                    .findByIdAndTenantId(eventId, TenantContext.obrigatorio())
                    .orElse(null);
            if (evento == null) {
                log.warn("Evento reservado não encontrado no contexto do tenant. eventId={}", eventId);
                return;
            }

            TipoCanal tipo = conexoes.buscarAtiva(evento.getChannelConnectionId())
                    .map(conexao -> conexao.tipo())
                    .orElse(null);

            if (tipo == null) {
                evento.marcarFalha("Conexão inativa ou removida.");
                // Marca processado: insistir num canal que não existe mais só
                // gastaria tentativas até o teto.
                evento.marcarProcessado();
                return;
            }

            String payloadCru = evento.payloadParaProcessamento(cofre);
            Optional<InboundMessage> normalizada = traduzir(tipo, evento, payloadCru);
            if (tipo == TipoCanal.TELEGRAM && normalizada.isPresent()) {
                Optional<UUID> mediaId = midiasTelegram.ingerir(
                        evento.getChannelConnectionId(), payloadCru);
                if (mediaId.isPresent()) {
                    normalizada = Optional.of(comMidia(normalizada.get(), mediaId.get()));
                }
            }

            // Evento que não vira mensagem — edição, entrada em grupo, enquete
            // — é sucesso, não falha. Tratar como erro encheria o log de ruído
            // e consumiria tentativas por algo que nunca vai mudar.
            normalizada.ifPresent(mensagem ->
                    publicador.publishEvent(new MensagemNormalizadaEvent(mensagem)));

            evento.marcarProcessado();
        }

        private Optional<InboundMessage> traduzir(TipoCanal tipo, InboundEventEntity evento,
                                                  String payloadCru) {
            return switch (tipo) {
                case TELEGRAM -> tradutorTelegram.traduzir(
                        evento.getChannelConnectionId(), payloadCru);
                case WHATSAPP_EVOLUTION -> tradutorEvolution.traduzir(
                        evento.getChannelConnectionId(), payloadCru);
                // Chat ao vivo não passa por webhook, e os canais da Meta ainda
                // não existem. Chegar aqui é sinal de configuração incoerente.
                case LIVE_CHAT, WHATSAPP_CLOUD, INSTAGRAM -> Optional.empty();
            };
        }

        private InboundMessage comMidia(InboundMessage original, UUID mediaId) {
            java.util.Map<String, Object> diagnostico =
                    new java.util.LinkedHashMap<>(original.payload());
            diagnostico.put("media_id", mediaId.toString());
            diagnostico.put("media_status", "QUARANTINED");
            return new InboundMessage(
                    original.channelConnectionId(), original.externalId(),
                    original.externalContactId(), original.contactDisplayName(),
                    original.tipoConteudo(), original.texto(), original.ocorridoEm(),
                    diagnostico);
        }
    }
}
