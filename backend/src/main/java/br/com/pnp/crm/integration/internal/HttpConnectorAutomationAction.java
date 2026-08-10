package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.automation.api.AutomationAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A única ação HTTP exposta ao motor; nunca aceita URL, método ou header no passo. */
@Component
class HttpConnectorAutomationAction implements AutomationAction {

    static final String TIPO = "HTTP_CONNECTOR_V1";
    private final HttpConnectorService conectores;
    private final ExecutorConectorHttp executor;

    HttpConnectorAutomationAction(HttpConnectorService conectores,
                                  ExecutorConectorHttp executor) {
        this.conectores = conectores;
        this.executor = executor;
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public NaturezaEfeito natureza() {
        return NaturezaEfeito.EXTERNAL_IRREVERSIBLE;
    }

    @Override
    public boolean repeticaoSegura() {
        // Métodos mutáveis exigem Idempotency-Key na definição; GET é seguro.
        return true;
    }

    @Override
    public void validarConfiguracao(Map<String, String> configuracao) {
        if (configuracao == null || !configuracao.keySet().equals(Set.of("connectorId"))) {
            throw new IllegalArgumentException("Informe somente connectorId.");
        }
        try {
            conectores.exigirExistente(UUID.fromString(configuracao.get("connectorId")));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Conector HTTP invalido.");
        }
    }

    @Override
    public Resultado executar(Contexto contexto) {
        UUID connectorId;
        try {
            connectorId = UUID.fromString(contexto.configuracao().get("connectorId"));
        } catch (RuntimeException e) {
            return Resultado.FALHA_PERMANENTE;
        }
        return executor.executar(connectorId, contexto);
    }
}
