package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * O tenant nunca fica sem ninguem capaz de administrar.
 *
 * <p>Revogar a ultima atribuicao de papel de sistema produz bloqueio total: nao
 * sobra quem crie papel, conceda permissao ou reative um usuario, e a saida
 * passa a exigir acesso direto ao banco. E um estado irreversivel pela
 * aplicacao, alcancado por um clique que parece rotina.
 */
final class UltimoProprietarioException extends DomainException {

    UltimoProprietarioException() {
        super("ULTIMO_PROPRIETARIO",
                "Esta é a última atribuição de papel de sistema do tenant."
                        + " Conceda-o a outra pessoa antes de revogar.");
    }
}
