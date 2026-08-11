package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.DomainException;

/**
 * Recusa concessao que exceda o privilegio de quem concede.
 *
 * <p>E a invariante central da administracao delegada de papeis: ninguem
 * concede o que nao tem, nem sob alcance mais amplo que o proprio. Sem ela,
 * {@code organization.manage} deixaria de ser "pode administrar" e viraria
 * "pode se tornar qualquer coisa".
 *
 * <p><b>A mensagem nao diz qual permissao faltou.</b> Enumerar o que o autor
 * nao possui devolveria, a cada tentativa, um mapa do proprio privilegio — util
 * exatamente para quem esta sondando ate onde consegue chegar. Quem administra
 * de boa-fe ja ve o que pode conceder na listagem de papeis, que marca cada
 * permissao como delegavel ou nao.
 */
final class ConcessaoAcimaDoPrivilegioException extends DomainException {

    ConcessaoAcimaDoPrivilegioException() {
        super("CONCESSAO_ACIMA_DO_PRIVILEGIO",
                "Não é possível conceder permissão ou alcance além do seu próprio.");
    }
}
