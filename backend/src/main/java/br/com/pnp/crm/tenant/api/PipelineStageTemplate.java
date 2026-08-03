package br.com.pnp.crm.tenant.api;

/** Etapa imutável do funil que nasce no primeiro uso do CRM. */
public record PipelineStageTemplate(
        String name,
        int position,
        boolean won,
        boolean lost) {
}
