package br.com.pnp.crm.privacy.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Eliminacao recusada com motivo.
 *
 * <p>Existe porque o aceite do Prompt 18 e explicito: "exclusao impossivel
 * vira anonimizacao/recusa fundamentada, <b>nao falha silenciosa</b>".
 *
 * <p>Devolver 200 sem apagar seria pior que devolver erro: o titular sairia
 * acreditando que o dado foi removido, e a empresa perderia o registro de que
 * um pedido ficou pendente.
 */
final class EliminacaoRecusadaException extends DomainException {

    EliminacaoRecusadaException(String mensagem) {
        super("ELIMINACAO_RECUSADA", mensagem);
    }
}
