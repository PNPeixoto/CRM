package br.com.pnp.crm.shared.api;

/**
 * Nenhum tenant no contexto onde um era obrigatório.
 *
 * <p>Isto é erro de programação, não entrada inválida do usuário: significa
 * que uma operação de negócio chegou ao banco sem passar pela resolução de
 * tenant. Vira 500 no handler global, e deve, porque a alternativa — seguir
 * sem tenant — é consulta sem isolamento.
 */
public class TenantNaoDefinidoException extends DomainException {

    public TenantNaoDefinidoException() {
        super("TENANT_NAO_DEFINIDO", "Operação exige tenant no contexto da requisição.");
    }
}
