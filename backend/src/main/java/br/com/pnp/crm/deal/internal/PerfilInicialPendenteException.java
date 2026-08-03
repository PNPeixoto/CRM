package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.shared.api.DomainException;

class PerfilInicialPendenteException extends DomainException {

    PerfilInicialPendenteException() {
        super("PERFIL_INICIAL_PENDENTE", "Escolha o segmento da empresa antes de criar o funil.");
    }
}
