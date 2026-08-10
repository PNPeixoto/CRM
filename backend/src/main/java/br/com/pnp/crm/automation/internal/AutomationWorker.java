package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import br.com.pnp.crm.shared.api.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Executa no maximo um passo por lease, mantendo pausa e cancelamento responsivos. */
@Component
@ConditionalOnProperty(
        name = "app.automation.worker-enabled", havingValue = "true", matchIfMissing = true)
class AutomationWorker {

    private static final Logger log = LoggerFactory.getLogger(AutomationWorker.class);

    private final ReservaExecucoesAutomacao reserva;
    private final ProcessamentoExecucaoAutomacao processamento;
    private final AutomationActionRegistry actions;

    AutomationWorker(ReservaExecucoesAutomacao reserva,
                     ProcessamentoExecucaoAutomacao processamento,
                     AutomationActionRegistry actions) {
        this.reserva = reserva;
        this.processamento = processamento;
        this.actions = actions;
    }

    @Scheduled(fixedDelayString = "${app.automation.worker-interval-ms:1000}")
    void processar() {
        for (ReservaExecucoesAutomacao.Reservada reservada : reserva.proximoLote()) {
            TenantContext.executarComo(reservada.tenantId(), () -> {
                executarUma(reservada.executionId());
                return null;
            });
        }
    }

    private void executarUma(java.util.UUID executionId) {
        ProcessamentoExecucaoAutomacao.Trabalho trabalho =
                processamento.preparar(executionId, reserva.leaseSegundos());
        if (trabalho == null) return;

        ProcessamentoExecucaoAutomacao.Resultado resultado;
        if (trabalho.dryRun()) {
            resultado = ProcessamentoExecucaoAutomacao.Resultado.simulado();
        } else {
            AutomationAction action = actions.buscarParaExecucao(trabalho.passo().acao());
            if (action == null) {
                resultado = ProcessamentoExecucaoAutomacao.Resultado.permanente(
                        "ACTION_UNAVAILABLE");
            } else if (action.natureza() != trabalho.passo().efeito()
                    || action.repeticaoSegura() != trabalho.passo().repeticaoSegura()) {
                resultado = ProcessamentoExecucaoAutomacao.Resultado.permanente(
                        "ACTION_CONTRACT_CHANGED");
            } else {
                resultado = executarSanitizado(action, trabalho);
            }
        }
        processamento.concluir(trabalho, resultado);
    }

    private ProcessamentoExecucaoAutomacao.Resultado executarSanitizado(
            AutomationAction action, ProcessamentoExecucaoAutomacao.Trabalho trabalho) {
        try {
            AutomationAction.Resultado obtido = action.executar(new AutomationAction.Contexto(
                    TenantContext.obrigatorio(), trabalho.executionId(),
                    trabalho.passo().chave(), trabalho.triggerType(),
                    trabalho.sourceReferenceId(), trabalho.passo().configuracao()));
            if (obtido == null) {
                return ProcessamentoExecucaoAutomacao.Resultado.permanente(
                        "ACTION_INVALID_RESULT");
            }
            return switch (obtido) {
                case SUCESSO -> ProcessamentoExecucaoAutomacao.Resultado.sucesso();
                case FALHA_TRANSITORIA ->
                        ProcessamentoExecucaoAutomacao.Resultado.transitorio(
                                "ACTION_TRANSIENT_FAILURE");
                case FALHA_PERMANENTE ->
                        ProcessamentoExecucaoAutomacao.Resultado.permanente(
                                "ACTION_PERMANENT_FAILURE");
            };
        } catch (RuntimeException e) {
            // Nao registra mensagem nem stack da acao: uma integracao futura
            // pode carregar payload ou segredo na excecao. O codigo e suficiente
            // para a trilha; executionId permite correlacionar com metrica.
            log.error("Falha sanitizada em acao de automacao. executionId={} stepKey={}",
                    trabalho.executionId(), trabalho.passo().chave());
            return ProcessamentoExecucaoAutomacao.Resultado.permanente(
                    "ACTION_UNEXPECTED_FAILURE");
        }
    }
}
