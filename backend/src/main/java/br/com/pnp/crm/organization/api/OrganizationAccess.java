package br.com.pnp.crm.organization.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contrato mínimo usado por identidade/autorização, sem expor entidades JPA. */
public interface OrganizationAccess {

    AccessSummary summarize(UUID tenantId, UUID userId);

    AuthorizedContext selectUnit(UUID tenantId, UUID userId, UUID unitId);

    /**
     * Alcance efetivo de cada permissão concedida ao usuário: código da
     * permissão para o escopo mais amplo que a concede.
     *
     * <p>Existe porque {@link AuthorizedContext} achata as concessões — ele
     * responde "o que esta pessoa pode fazer" e perde "sob qual alcance cada
     * coisa foi concedida". Quem decide autorização precisa das duas respostas:
     * um papel de tenant que concede leitura de contato e um papel de alcance
     * próprio que concede escrita de oportunidade não podem virar um conjunto
     * único, sob pena de a escrita herdar o alcance da leitura.
     *
     * <p>Permissão ausente do mapa é permissão não concedida. Lança
     * {@code AcessoNegadoException} quando não há membership vigente.
     */
    Map<String, ScopeType> permissionScopes(UUID tenantId, UUID userId);

    /** Consulta de política sem exceção para identidades ainda sem membership. */
    boolean hasAnyRole(UUID tenantId, UUID userId, Set<String> roleCodes);

    record AccessSummary(UUID tenantId, UUID membershipId,
                         List<AuthorizedContext> contexts) {
        public AccessSummary {
            contexts = List.copyOf(contexts);
        }
    }

    record AuthorizedContext(UUID id, ScopeType type, String code, String name,
                             Set<String> roles, Set<String> permissions,
                             Set<ScopeType> scopes) {
        public AuthorizedContext {
            roles = Set.copyOf(roles);
            permissions = Set.copyOf(permissions);
            scopes = Set.copyOf(scopes);
        }
    }

    /** Hierarquia completa; NETWORK e TEAM ainda não recebem persistência. */
    enum ScopeType {
        NETWORK,
        TENANT,
        UNIT,
        TEAM,
        OWN
    }
}
