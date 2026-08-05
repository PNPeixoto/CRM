package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.identity.api.UsuarioLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class UsuarioLookupImpl implements UsuarioLookup {

    private final AppUserRepository users;

    UsuarioLookupImpl(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActive(UUID tenantId, UUID userId) {
        return userId != null
                && users.existsByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(userId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UsuarioReference> findKnown(UUID tenantId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return users.buscarPorIds(tenantId, userIds).stream()
                .map(user -> new UsuarioReference(
                        user.getId(), user.getLogin(), user.getFullName()))
                .collect(Collectors.toUnmodifiableMap(
                        UsuarioReference::id, Function.identity()));
    }
}
