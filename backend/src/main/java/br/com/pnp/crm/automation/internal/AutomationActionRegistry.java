package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Catalogo fail-closed das acoes versionadas disponiveis. */
@Component
class AutomationActionRegistry {

    private final Map<String, AutomationAction> porTipo;

    AutomationActionRegistry(List<AutomationAction> actions) {
        Map<String, AutomationAction> catalogo = new HashMap<>();
        for (AutomationAction action : actions) {
            if (action.tipo() == null
                    || !action.tipo().matches("^[A-Z][A-Z0-9_]{1,69}_V[0-9]+$")) {
                throw new IllegalStateException("Tipo de acao de automacao invalido.");
            }
            if (catalogo.putIfAbsent(action.tipo(), action) != null) {
                throw new IllegalStateException("Tipo de acao de automacao duplicado.");
            }
        }
        this.porTipo = Map.copyOf(catalogo);
    }

    AutomationAction exigir(String tipo) {
        AutomationAction action = porTipo.get(tipo);
        if (action == null) {
            throw new RequisicaoInvalidaException("A acao informada nao esta disponivel.");
        }
        return action;
    }

    AutomationAction buscarParaExecucao(String tipo) {
        return porTipo.get(tipo);
    }
}
