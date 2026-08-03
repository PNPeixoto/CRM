package br.com.pnp.crm.contact.api;

import java.util.Optional;
import java.util.UUID;

/** Consulta mínima para validar vínculo com contato no tenant corrente. */
public interface ContactLookup {

    boolean exists(UUID tenantId, UUID contactId);

    /**
     * Metadado mínimo para autorizar um vínculo sem expor a entidade do módulo.
     * O record existe mesmo quando o contato ainda não tem responsável.
     */
    Optional<ContactReference> findReference(UUID tenantId, UUID contactId);

    record ContactReference(UUID ownerUserId) {
    }
}
