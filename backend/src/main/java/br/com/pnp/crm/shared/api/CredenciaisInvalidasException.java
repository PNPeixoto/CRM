package br.com.pnp.crm.shared.api;

/**
 * Falha de autenticação.
 *
 * <p>Deliberadamente única para todos os motivos: usuário inexistente, senha
 * errada, tenant inexistente, conta inativa. Uma exceção por motivo produziria
 * mensagens diferentes e transformaria a tela de login num oráculo capaz de
 * responder "este login existe nesta empresa?" para qualquer um.
 *
 * <p>O motivo real é registrado no log com id de correlação, onde tem valor
 * operacional e nenhum valor para o atacante.
 */
public class CredenciaisInvalidasException extends DomainException {

    private static final String MENSAGEM_UNICA = "Login ou senha inválidos.";

    public CredenciaisInvalidasException() {
        super("CREDENCIAIS_INVALIDAS", MENSAGEM_UNICA);
    }
}
