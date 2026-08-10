package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.shared.api.DomainException;

final class PerfilInicialPendenteException extends DomainException {

    PerfilInicialPendenteException() {
        super("PERFIL_INICIAL_PENDENTE",
                "Conclua o perfil inicial antes de alterar a apresentação da empresa.");
    }
}
