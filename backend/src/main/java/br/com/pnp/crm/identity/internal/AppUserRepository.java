package br.com.pnp.crm.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    /**
     * O filtro por {@code tenantId} é redundante com o RLS — e é assim de
     * propósito. O RLS é a rede de segurança para quando o código erra; o
     * filtro explícito é o que permite ao planejador usar o índice
     * {@code app_user_login_unico_por_tenant}, que começa por tenant_id.
     * Confiar só no RLS deixaria a consulta correta e lenta.
     */
    Optional<AppUserEntity> findByTenantIdAndLoginAndDeletedAtIsNull(UUID tenantId, String login);

    Optional<AppUserEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
