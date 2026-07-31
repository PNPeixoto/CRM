package br.com.pnp.crm.deal.internal;

import br.com.pnp.crm.deal.api.DealMetrics;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class DealMetricsImpl implements DealMetrics {

    private final DealRepository repository;

    DealMetricsImpl(DealRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumoDoFunil resumo() {
        UUID tenantId = TenantContext.obrigatorio();
        return new ResumoDoFunil(
                repository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, StatusOportunidade.OPEN),
                repository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, StatusOportunidade.WON),
                repository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, StatusOportunidade.LOST),
                repository.somarValorPorStatus(tenantId, StatusOportunidade.OPEN),
                repository.somarValorPorStatus(tenantId, StatusOportunidade.WON));
    }
}
