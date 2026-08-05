package br.com.pnp.crm.shared.api;

/** Falha segura para parâmetros HTTP semanticamente inválidos. */
public final class RequisicaoInvalidaException extends DomainException {

    public RequisicaoInvalidaException(String mensagem) {
        super("REQUISICAO_INVALIDA", mensagem);
    }
}
