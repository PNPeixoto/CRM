package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.channel.api.ChannelMetrics;
import br.com.pnp.crm.contact.api.ContactMetrics;
import br.com.pnp.crm.conversation.api.ConversationMetrics;
import br.com.pnp.crm.deal.api.DealMetrics;
import br.com.pnp.crm.task.api.TaskMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uma composicao canonica para tela e exportacao usarem a mesma formula. */
@Service
class ReportSnapshotService {

    private final ConversationMetrics conversas;
    private final ContactMetrics contatos;
    private final DealMetrics oportunidades;
    private final TaskMetrics tarefas;
    private final ChannelMetrics canais;

    ReportSnapshotService(ConversationMetrics conversas, ContactMetrics contatos,
                          DealMetrics oportunidades, TaskMetrics tarefas,
                          ChannelMetrics canais) {
        this.conversas = conversas;
        this.contatos = contatos;
        this.oportunidades = oportunidades;
        this.tarefas = tarefas;
        this.canais = canais;
    }

    Snapshot capturar() {
        ConversationMetrics.ResumoDeConversas c = conversas.resumo();
        DealMetrics.ResumoDoFunil d = oportunidades.resumo();
        TaskMetrics.ResumoDeTarefas t = tarefas.resumo();
        Instant capturadoEm = Instant.now();
        return new Snapshot(
                capturadoEm, c.abertas(), c.aguardando(), c.mensagensRecebidasHoje(),
                contatos.totalDeContatos(), d.oportunidadesAbertas(),
                d.valorAbertoCentavos(), d.oportunidadesGanhas(),
                d.valorGanhoCentavos(), d.oportunidadesPerdidas(),
                t.abertas(), t.atrasadas(), canais.canaisAtivos(), conversao(d));
    }

    private static BigDecimal conversao(DealMetrics.ResumoDoFunil resumo) {
        long fechadas = resumo.oportunidadesGanhas() + resumo.oportunidadesPerdidas();
        if (fechadas == 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(resumo.oportunidadesGanhas())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(fechadas), 1, RoundingMode.HALF_UP);
    }

    record Snapshot(
            Instant capturadoEm,
            long conversasAbertas,
            long conversasAguardando,
            long mensagensRecebidasHoje,
            long totalDeContatos,
            long oportunidadesAbertas,
            long valorAbertoCentavos,
            long oportunidadesGanhas,
            long valorGanhoCentavos,
            long oportunidadesPerdidas,
            long tarefasAbertas,
            long tarefasAtrasadas,
            long canaisAtivos,
            BigDecimal taxaDeConversaoPercentual) {

        Map<String, String> valoresCanonicos() {
            Map<String, String> valores = new LinkedHashMap<>();
            valores.put("conversations.open.count", Long.toString(conversasAbertas));
            valores.put("conversations.waiting.count", Long.toString(conversasAguardando));
            valores.put("messages.inbound.today.count", Long.toString(mensagensRecebidasHoje));
            valores.put("contacts.total.count", Long.toString(totalDeContatos));
            valores.put("deals.open.count", Long.toString(oportunidadesAbertas));
            valores.put("deals.open.value_minor", Long.toString(valorAbertoCentavos));
            valores.put("deals.won.count", Long.toString(oportunidadesGanhas));
            valores.put("deals.won.value_minor", Long.toString(valorGanhoCentavos));
            valores.put("deals.lost.count", Long.toString(oportunidadesPerdidas));
            valores.put("tasks.open.count", Long.toString(tarefasAbertas));
            valores.put("tasks.overdue.count", Long.toString(tarefasAtrasadas));
            valores.put("channels.active.count", Long.toString(canaisAtivos));
            valores.put("deals.conversion.percent", taxaDeConversaoPercentual.toPlainString());
            return Map.copyOf(valores);
        }
    }
}
