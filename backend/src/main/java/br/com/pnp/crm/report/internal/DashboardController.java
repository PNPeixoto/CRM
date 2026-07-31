package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.channel.api.ChannelMetrics;
import br.com.pnp.crm.contact.api.ContactMetrics;
import br.com.pnp.crm.conversation.api.ConversationMetrics;
import br.com.pnp.crm.deal.api.DealMetrics;
import br.com.pnp.crm.task.api.TaskMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visão geral e relatórios.
 *
 * <p>Este módulo <b>não tem tabela própria</b>: ele só compõe números que cada
 * módulo dono expõe. É de propósito — relatório que consulta tabela alheia
 * transforma o formato daquela tabela em contrato implícito, e a primeira
 * mudança de coluna quebra o relatório sem nenhum aviso do compilador.
 *
 * <p>Valores monetários saem em <b>centavos</b>. A formatação para
 * "R$ 1.234,56" é responsabilidade da tela.
 */
@RestController
@RequestMapping("/api/relatorios")
class DashboardController {

    private final ConversationMetrics conversas;
    private final ContactMetrics contatos;
    private final DealMetrics oportunidades;
    private final TaskMetrics tarefas;
    private final ChannelMetrics canais;

    DashboardController(ConversationMetrics conversas, ContactMetrics contatos,
                        DealMetrics oportunidades, TaskMetrics tarefas, ChannelMetrics canais) {
        this.conversas = conversas;
        this.contatos = contatos;
        this.oportunidades = oportunidades;
        this.tarefas = tarefas;
        this.canais = canais;
    }

    @GetMapping("/visao-geral")
    ResponseEntity<VisaoGeralResponse> visaoGeral() {
        ConversationMetrics.ResumoDeConversas c = conversas.resumo();
        DealMetrics.ResumoDoFunil d = oportunidades.resumo();
        TaskMetrics.ResumoDeTarefas t = tarefas.resumo();

        return ResponseEntity.ok(new VisaoGeralResponse(
                c.abertas(), c.aguardando(), c.mensagensRecebidasHoje(),
                contatos.totalDeContatos(),
                d.oportunidadesAbertas(), d.valorAbertoCentavos(),
                d.oportunidadesGanhas(), d.valorGanhoCentavos(), d.oportunidadesPerdidas(),
                t.abertas(), t.atrasadas(),
                canais.canaisAtivos(),
                taxaDeConversao(d)));
    }

    /**
     * Ganhas sobre o total de fechadas, em pontos percentuais.
     *
     * <p>Denominador exclui as abertas de propósito: incluí-las faria a taxa
     * cair sempre que entrasse lead novo, o que inverteria a leitura — mais
     * oportunidades pareceria pior desempenho.
     *
     * <p>Divisão em ponto flutuante é aceitável aqui porque o resultado é um
     * indicador de leitura, não dinheiro. Os valores em centavos continuam
     * inteiros.
     */
    private double taxaDeConversao(DealMetrics.ResumoDoFunil d) {
        long fechadas = d.oportunidadesGanhas() + d.oportunidadesPerdidas();
        if (fechadas == 0) {
            return 0d;
        }
        return Math.round((d.oportunidadesGanhas() * 1000d) / fechadas) / 10d;
    }

    record VisaoGeralResponse(
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
            double taxaDeConversaoPercentual) {
    }
}
