package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.identity.api.UsuarioLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
}
