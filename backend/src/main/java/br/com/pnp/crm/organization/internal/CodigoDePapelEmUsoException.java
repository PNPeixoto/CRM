package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Codigo de papel duplicado, recusado com mensagem em vez de 500.
 *
 * <p>O indice unico parcial de {@code app_role} ja impede a duplicata, mas a
 * violacao chegaria ao handler global como falha nao tratada — 500 generico,
 * sem indicacao do que corrigir, para um erro de digitacao trivial.
 *
 * <p>Codigo de papel excluido volta a ficar livre: o indice e parcial em
 * {@code deleted_at IS NULL}, e reaproveitar um codigo aposentado e legitimo.
 */
final class CodigoDePapelEmUsoException extends DomainException {

    CodigoDePapelEmUsoException(String codigo) {
        super("CODIGO_DE_PAPEL_EM_USO", "Já existe um papel com o código " + codigo + ".");
    }
}
