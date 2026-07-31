package br.com.pnp.crm.deal.api;

/**
 * Números do funil. Valores sempre em <b>centavos</b>, como inteiro — a
 * conversão para moeda acontece só na formatação de exibição.
 */
public interface DealMetrics {

    ResumoDoFunil resumo();

    record ResumoDoFunil(
            long oportunidadesAbertas,
            long oportunidadesGanhas,
            long oportunidadesPerdidas,
            long valorAbertoCentavos,
            long valorGanhoCentavos) {
    }
}
