package br.com.pnp.crm.conversation.api;

public interface ConversationMetrics {

    ResumoDeConversas resumo();

    record ResumoDeConversas(long abertas, long aguardando, long mensagensRecebidasHoje) {
    }
}
