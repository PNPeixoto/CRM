package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.shared.api.DomainException;

class PerfilInicialJaConcluidoException extends DomainException {

    PerfilInicialJaConcluidoException() {
        super("PERFIL_INICIAL_JA_CONCLUIDO", "O perfil inicial da empresa já foi definido.");
    }
}
