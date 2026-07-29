package br.com.pnp.crm.channel.api;

/**
 * Provedores suportados.
 *
 * <p>Os nomes espelham exatamente os valores aceitos pelo CHECK da coluna
 * {@code channel_connection.kind}. Divergir aqui quebra na inserção, não na
 * compilação — por isso o valor é o próprio nome da constante, sem tradução.
 */
public enum TipoCanal {

    /** Widget de chat no site do cliente. Não tem provedor externo. */
    LIVE_CHAT,

    TELEGRAM,

    WHATSAPP_CLOUD,

    INSTAGRAM
}
