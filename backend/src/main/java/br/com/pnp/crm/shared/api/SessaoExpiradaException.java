package br.com.pnp.crm.shared.api;

/**
 * Refresh token ausente, expirado, já usado ou revogado.
 *
 * <p>Como {@link CredenciaisInvalidasException}, é única para todos os
 * motivos. O caso mais delicado é o reuso de token já rotacionado: ele indica
 * roubo, dispara a revogação da família inteira e não pode dizer isso na
 * resposta — informar "este token já foi usado" confirma ao atacante que ele
 * capturou um token real e que a vítima continua ativa.
 */
public class SessaoExpiradaException extends DomainException {

    public SessaoExpiradaException() {
        super("SESSAO_EXPIRADA", "Sessão expirada. Faça login novamente.");
    }
}
