package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationEngine;
import br.com.pnp.crm.automation.api.AutomationTrigger;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Resolve definicoes ativas sem aceitar tenant controlado por HTTP. */
@Service
class MotorAutomacaoService implements AutomationEngine {

    private final ConsultaDefinicaoAutomacao definicoes;
    private final CriadorExecucaoAutomacao criador;

    MotorAutomacaoService(ConsultaDefinicaoAutomacao definicoes,
                          CriadorExecucaoAutomacao criador) {
        this.definicoes = definicoes;
        this.criador = criador;
    }

    @Override
    public List<UUID> disparar(AutomationTrigger trigger) {
        validar(trigger);
        return TenantContext.executarComo(trigger.tenantId(), () -> {
            List<UUID> execucoes = new ArrayList<>();
            for (ConsultaDefinicaoAutomacao.DefinicaoVersionada definicao
                    : definicoes.ativasPorGatilho(trigger.triggerType())) {
                execucoes.add(criador.criar(definicao, trigger, false, null));
            }
            return List.copyOf(execucoes);
        });
    }

    UUID dispararManual(UUID definicaoId, String chave, UUID referencia,
                        boolean simulacao, Integer numeroVersao, UUID autorId) {
        ConsultaDefinicaoAutomacao.DefinicaoVersionada definicao = numeroVersao == null
                ? definicoes.ativa(definicaoId)
                : definicoes.versao(definicaoId, numeroVersao);
        AutomationTrigger trigger = new AutomationTrigger(
                TenantContext.obrigatorio(), definicao.gatilho(),
                (simulacao ? "dryrun:" : "manual:") + chave,
                referencia, null);
        return criador.criar(definicao, trigger, simulacao, autorId);
    }

    private static void validar(AutomationTrigger trigger) {
        if (trigger == null || trigger.tenantId() == null
                || trigger.triggerType() == null
                || !trigger.triggerType().matches("^[A-Z][A-Z0-9_]{2,79}$")
                || trigger.triggerKey() == null
                || trigger.triggerKey().length() > 200
                || !trigger.triggerKey().matches("^[A-Za-z0-9._:-]+$")) {
            throw new RequisicaoInvalidaException("Gatilho de automacao invalido.");
        }
    }
}
