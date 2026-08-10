package br.com.pnp.crm.shared.api;

import java.util.UUID;

/** Revalida a familia de sessao usada por uma conexao WebSocket duradoura. */
public interface ValidadorSessaoTempoReal {

    void exigirAtiva(UUID tenantId, UUID usuarioId, UUID sessaoId);
}
