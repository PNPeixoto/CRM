package br.com.pnp.crm.identity.api;

import java.util.UUID;

/** Consulta mínima para validar referências a usuários sem expor entidade. */
public interface UsuarioLookup {

    boolean existsActive(UUID tenantId, UUID userId);
}
