package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Composicao de equipe recusada.
 *
 * <p>Cobre os dois arranjos que a resolucao de alcance nao sabe interpretar:
 * alguem respondendo a si mesmo, e duas pessoas respondendo uma a outra. O
 * segundo nao trava a consulta — a resolucao e de um nivel so, sem recursao —
 * mas produz duas pessoas que se enxergam mutuamente sem que nenhuma seja
 * gestora de fato, o que ninguem consegue explicar ao olhar o organograma.
 */
final class ComposicaoDeEquipeInvalidaException extends DomainException {

    ComposicaoDeEquipeInvalidaException(String mensagem) {
        super("COMPOSICAO_DE_EQUIPE_INVALIDA", mensagem);
    }
}
