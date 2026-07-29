package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Worker da fila de saída.
 *
 * <p>É o único lugar do sistema que fala com provedor externo. Nenhuma
 * requisição HTTP de usuário chega aqui, e é isso que cumpre a regra de nunca
 * chamar provedor dentro da transação da conversa: uma chamada de rede ali
 * seguraria a conexão do banco pelo tempo do timeout, e sob carga isso esgota
 * o pool e derruba a aplicação por causa de um provedor lento.
 *
 * <p>A reserva e a entrega estão em beans distintos de propósito — ver
 * {@link ReservaDeMensagens} para o motivo, que é a semântica de proxy do
 * {@code @Transactional}.
 */
@Component
// Ligado por padrão; desligado explicitamente nos testes de integração. Um
// worker rodando durante o teste consumiria a fila enquanto o cenário ainda
// está sendo montado, e a falha resultante seria intermitente — o pior tipo.
@ConditionalOnProperty(
        name = "app.fila-de-saida.habilitada", havingValue = "true", matchIfMissing = true)
class FilaDeSaidaWorker {

    private static final Logger log = LoggerFactory.getLogger(FilaDeSaidaWorker.class);

    private final ReservaDeMensagens reserva;
    private final EnvioDeMensagemService envio;

    FilaDeSaidaWorker(ReservaDeMensagens reserva, EnvioDeMensagemService envio) {
        this.reserva = reserva;
        this.envio = envio;
    }

    /**
     * {@code fixedDelay} e não {@code fixedRate}: o intervalo conta a partir do
     * FIM da execução anterior. Com {@code fixedRate}, um lote que demore mais
     * que o intervalo faz as execuções se sobreporem, e a fila passa a ser
     * disputada pelo próprio worker.
     */
    @Scheduled(fixedDelayString = "${app.fila-de-saida.intervalo-ms:2000}")
    void processarLote() {
        List<ReservaDeMensagens.MensagemReservada> reservadas = reserva.proximoLote();
        if (reservadas.isEmpty()) {
            return;
        }

        log.debug("Lote reservado para envio. quantidade={}", reservadas.size());

        for (ReservaDeMensagens.MensagemReservada reservada : reservadas) {
            entregarComIsolamento(reservada);
        }
    }

    private void entregarComIsolamento(ReservaDeMensagens.MensagemReservada reservada) {
        // Cada mensagem no contexto do PRÓPRIO tenant. Sem isto, o RLS
        // esconderia a linha de quem tentasse lê-la, e o envio falharia
        // silenciosamente para todos menos o primeiro tenant do lote.
        TenantContext.executarComo(reservada.tenantId(), () -> {
            try {
                envio.entregar(reservada.messageId());
            } catch (RuntimeException e) {
                // Uma mensagem problemática não pode interromper o lote. A
                // tentativa já foi contabilizada na reserva, então ela volta no
                // próximo ciclo — e depois do teto, para de voltar.
                log.error("Falha inesperada ao entregar mensagem. messageId={}",
                        reservada.messageId(), e);
            }
            return null;
        });
    }
}
