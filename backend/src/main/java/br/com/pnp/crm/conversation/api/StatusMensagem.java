package br.com.pnp.crm.conversation.api;

/**
 * Espelha o CHECK de {@code message.status}.
 *
 * <p>Mensagem recebida nasce e permanece em {@link #RECEIVED}. Mensagem
 * enviada percorre {@link #PENDING} → {@link #SENT} → {@link #DELIVERED} →
 * {@link #READ}, cada passo dependendo de um evento que o provedor manda
 * depois — e que pode nunca chegar, razão de nenhum deles ser obrigatório.
 */
public enum StatusMensagem {

    RECEIVED,

    /** Na fila de saída, ainda não entregue ao provedor. */
    PENDING,

    /** Aceita pelo provedor; foi neste momento que o external_id chegou. */
    SENT,

    DELIVERED,

    READ,

    /** Recusada pelo provedor ou esgotadas as tentativas. */
    FAILED
}
