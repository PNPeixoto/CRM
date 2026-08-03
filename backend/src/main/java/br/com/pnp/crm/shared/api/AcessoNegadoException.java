package br.com.pnp.crm.shared.api;

/** Operação autenticada cujo papel ou escopo não autoriza o acesso. */
public final class AcessoNegadoException extends DomainException {

    public AcessoNegadoException() {
        super("ACESSO_NEGADO", "Você não possui acesso a este contexto.");
    }
}
