package br.com.pnp.crm.deal.api;

import java.util.Optional;
import java.util.UUID;

/** Consulta mínima para validar vínculo com oportunidade. */
public interface DealLookup {

    boolean exists(UUID tenantId, UUID dealId);

    /** Metadado mínimo para autorizar o vínculo de uma tarefa à oportunidade. */
    Optional<DealReference> findReference(UUID tenantId, UUID dealId);

    record DealReference(UUID ownerUserId) {
    }
}
