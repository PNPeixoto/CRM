package br.com.pnp.crm.conversation.api;

/**
 * Espelha o CHECK de {@code conversation.status}.
 */
public enum StatusConversa {

    /** Em atendimento ou aguardando resposta do atendente. */
    OPEN,

    /** Aguardando o cliente. Separado de OPEN para a fila não contar como pendência da equipe. */
    PENDING,

    /**
     * Encerrada. É o único estado que libera a criação de uma nova conversa
     * com o mesmo interlocutor na mesma conexão — o índice único parcial da V2
     * depende disso.
     */
    CLOSED
}
