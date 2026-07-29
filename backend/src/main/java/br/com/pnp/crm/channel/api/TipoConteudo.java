package br.com.pnp.crm.channel.api;

/**
 * Natureza do conteúdo de uma mensagem, já normalizada.
 *
 * <p>Espelha o CHECK de {@code message.content_type}. O tratamento de mídia
 * em si — download, validação por magic bytes, storage e URL assinada — é da
 * Fase 4; aqui só existe a classificação, porque o Telegram já entrega esses
 * tipos e descartá-los na entrada perderia informação que não volta.
 */
public enum TipoConteudo {

    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    LOCATION,

    /** Recebido e reconhecido como mensagem, mas de tipo que ainda não tratamos. */
    OTHER
}
