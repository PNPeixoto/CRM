package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
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

    private final ReportSnapshotService snapshots;
    private final Autorizacao autorizacao;

    DashboardController(ReportSnapshotService snapshots, Autorizacao autorizacao) {
        this.snapshots = snapshots;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/visao-geral")
    ResponseEntity<VisaoGeralResponse> visaoGeral() {
        // Agregado do tenant inteiro: exigir REPORTS_READ não basta, porque
        // quem só alcança os próprios registros não pode ver totais que
        // incluem os alheios. Um relatório por alcance próprio é trabalho
        // futuro; até lá, o acesso ao consolidado é de alcance de tenant.
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);

        ReportSnapshotService.Snapshot s = snapshots.capturar();

        return ResponseEntity.ok(new VisaoGeralResponse(
                s.conversasAbertas(), s.conversasAguardando(),
                s.mensagensRecebidasHoje(), s.totalDeContatos(),
                s.oportunidadesAbertas(), s.valorAbertoCentavos(),
                s.oportunidadesGanhas(), s.valorGanhoCentavos(),
                s.oportunidadesPerdidas(), s.tarefasAbertas(),
                s.tarefasAtrasadas(), s.canaisAtivos(),
                s.taxaDeConversaoPercentual().doubleValue()));
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
