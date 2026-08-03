package br.com.pnp.crm.tenant.api;

/** Ajuste de apresentação ligado a um id de rota estável do frontend. */
public record NavigationItem(
        String routeId,
        String label,
        NavigationGroup group,
        int order,
        boolean visible) {
}
