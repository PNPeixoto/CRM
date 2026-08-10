package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import org.springframework.stereotype.Component;

/** Acao interna deterministica usada para composicao e verificacao do fluxo. */
@Component
class NoopAutomationAction implements AutomationAction {

    @Override
    public String tipo() {
        return "NOOP_V1";
    }

    @Override
    public NaturezaEfeito natureza() {
        return NaturezaEfeito.INTERNAL;
    }

    @Override
    public boolean repeticaoSegura() {
        return true;
    }

    @Override
    public Resultado executar(Contexto contexto) {
        return Resultado.SUCESSO;
    }
}
