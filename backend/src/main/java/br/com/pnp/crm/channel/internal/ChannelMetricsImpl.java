package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ChannelMetrics;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ChannelMetricsImpl implements ChannelMetrics {

    private final ChannelConnectionRepository repository;

    ChannelMetricsImpl(ChannelConnectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public long canaisAtivos() {
        return repository.countByTenantIdAndActiveTrueAndDeletedAtIsNull(TenantContext.obrigatorio());
    }
}
