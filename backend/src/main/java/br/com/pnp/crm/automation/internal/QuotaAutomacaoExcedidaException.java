package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.shared.api.DomainException;

/** Limite operacional do tenant atingido; a chamada pode ser repetida depois. */
final class QuotaAutomacaoExcedidaException extends DomainException {

    QuotaAutomacaoExcedidaException() {
        super("QUOTA_AUTOMACAO_EXCEDIDA",
                "O limite temporario de automacoes da empresa foi atingido.");
    }
}
