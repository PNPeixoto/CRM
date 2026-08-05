package br.com.pnp.crm.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Consulta mínima para validar referências a usuários sem expor entidade. */
public interface UsuarioLookup {

    boolean existsActive(UUID tenantId, UUID userId);

    /**
     * Resolve responsáveis ativos em lote sem expor entidade, e sem transformar
     * uma listagem de domínio em uma consulta de usuário por linha.
     */
    Map<UUID, UsuarioReference> findKnown(UUID tenantId, Collection<UUID> userIds);

    record UsuarioReference(UUID id, String login, String nomeCompleto) {
    }
}
