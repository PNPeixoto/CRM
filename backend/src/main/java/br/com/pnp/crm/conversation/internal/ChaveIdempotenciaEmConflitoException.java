package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.shared.api.DomainException;

final class ChaveIdempotenciaEmConflitoException extends DomainException {

    ChaveIdempotenciaEmConflitoException() {
        super("CHAVE_IDEMPOTENCIA_EM_CONFLITO",
                "A chave de idempotência já foi usada com outro conteúdo.");
    }
}
