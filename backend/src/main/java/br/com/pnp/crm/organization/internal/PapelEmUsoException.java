package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Papel com atribuicao viva nao e removido em cascata.
 *
 * <p>Apagar o papel revogaria, em silencio, o acesso de todo mundo que o tem —
 * e a pessoa afetada descobre no meio do expediente, sem qualquer indicacao de
 * que alguem "organizou os acessos". A recusa traz a contagem para que quem
 * administra decida conscientemente.
 */
final class PapelEmUsoException extends DomainException {

    PapelEmUsoException(long atribuicoes) {
        super("PAPEL_EM_USO", atribuicoes + " pessoa(s) ainda usam este papel."
                + " Revogue as atribuições antes de removê-lo.");
    }
}
