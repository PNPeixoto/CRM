package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.shared.api.DomainException;

final class ChaveIdempotenciaContatoEmConflitoException extends DomainException {

    ChaveIdempotenciaContatoEmConflitoException() {
        super("CHAVE_IDEMPOTENCIA_EM_CONFLITO",
                "A chave de idempotência já foi usada com outro contato.");
    }
}
