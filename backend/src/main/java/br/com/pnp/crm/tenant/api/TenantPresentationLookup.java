package br.com.pnp.crm.tenant.api;

/** API pública usada por módulos que precisam do preset do tenant corrente. */
public interface TenantPresentationLookup {

    TenantPresentation atual();
}
