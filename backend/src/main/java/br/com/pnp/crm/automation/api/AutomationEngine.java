package br.com.pnp.crm.automation.api;

import java.util.List;
import java.util.UUID;

/** Porta publica para eventos internos dispararem automacoes ativas. */
public interface AutomationEngine {

    /**
     * Dispara todas as definicoes ativas do tipo informado.
     *
     * @return ids novos ou ja existentes quando o gatilho foi repetido
     */
    List<UUID> disparar(AutomationTrigger trigger);
}
