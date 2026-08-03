package br.com.pnp.crm.tenant.api;

import java.util.List;

/** Apresentação efetiva do tenant, sem expor a entidade persistida. */
public record TenantPresentation(
        BusinessSegment businessSegment,
        int presetVersion,
        boolean onboardingCompleted,
        List<NavigationItem> navigation,
        DefaultPipelineTemplate defaultPipeline) {

    public TenantPresentation {
        navigation = List.copyOf(navigation);
    }
}
