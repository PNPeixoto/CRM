package br.com.pnp.crm.contact.internal;

import br.com.pnp.crm.contact.api.ContactMetrics;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ContactMetricsImpl implements ContactMetrics {

    private final ContactRepository repository;

    ContactMetricsImpl(ContactRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public long totalDeContatos() {
        return repository.countByTenantIdAndDeletedAtIsNull(TenantContext.obrigatorio());
    }
}
