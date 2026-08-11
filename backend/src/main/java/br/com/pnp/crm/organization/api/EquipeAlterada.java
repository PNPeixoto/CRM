package br.com.pnp.crm.organization.api;

import java.util.UUID;

/**
 * Mudanca na composicao de uma equipe, publicada para quem registra auditoria.
 *
 * <p>Composicao de equipe decide quem enxerga o que: incluir alguem sob um
 * gestor amplia o que aquele gestor le, sem que nenhum papel tenha mudado. E
 * superficie de autorizacao, e por isso vai para a trilha.
 *
 * <p>Mesmo caminho de {@link PapelAlterado}: organization publica, audit
 * escuta. Chamar {@code AuditTrail} daqui fecharia ciclo entre os modulos.
 */
public record EquipeAlterada(UUID tenantId, UUID actorId, UUID gestorId, UUID lideradoId,
                             Mudanca mudanca) {

    public enum Mudanca {
        INCLUIDO,
        REMOVIDO
    }
}
