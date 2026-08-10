package br.com.pnp.crm.automation.api;

import java.util.UUID;

/**
 * Gatilho validado entregue ao motor.
 *
 * <p>{@code tenantId} deve vir do contexto confiavel do produtor do evento;
 * nunca de corpo ou cabecalho enviado pelo cliente. {@code triggerKey} e uma
 * chave tecnica idempotente e nao pode conter conteudo de cliente.
 */
public record AutomationTrigger(
        UUID tenantId,
        String triggerType,
        String triggerKey,
        UUID sourceReferenceId,
        UUID causedByExecutionId) {
}
