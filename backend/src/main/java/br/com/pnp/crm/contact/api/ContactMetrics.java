package br.com.pnp.crm.contact.api;

/**
 * Números do módulo, para o dashboard e os relatórios.
 *
 * <p>Existe porque o módulo {@code report} <b>não pode</b> injetar o
 * repositório daqui. Poderia contornar com SQL nativo sobre a tabela
 * {@code contact} — o verificador de fronteira não veria —, mas aí o formato
 * da tabela viraria contrato implícito de outro módulo, e a primeira mudança
 * de coluna quebraria um relatório sem nenhum aviso do compilador.
 */
public interface ContactMetrics {

    long totalDeContatos();
}
