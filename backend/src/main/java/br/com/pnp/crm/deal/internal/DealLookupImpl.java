package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.deal.api.DealLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class DealLookupImpl implements DealLookup {

    private final DealRepository deals;

    DealLookupImpl(DealRepository deals) {
        this.deals = deals;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID tenantId, UUID dealId) {
        return dealId != null && deals.existsByIdAndTenantIdAndDeletedAtIsNull(dealId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DealReference> findReference(UUID tenantId, UUID dealId) {
        if (dealId == null) {
            return Optional.empty();
        }
        return deals.findByIdAndTenantIdAndDeletedAtIsNull(dealId, tenantId)
                .map(deal -> new DealReference(deal.getOwnerUserId()));
    }
}
