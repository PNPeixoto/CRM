package br.com.pnp.crm.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    boolean existsByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(UUID id, UUID tenantId);

    /**
     * Busca o usuário pelo login, ignorando maiúsculas.
     *
     * <p>O {@code LOWER} de ambos os lados casa com o índice único
     * {@code app_user_login_unico_por_tenant}, que também é sobre
     * {@code lower(login)} — sem essa simetria o planejador ignoraria o
     * índice e a consulta viraria varredura de tabela a cada tentativa de
     * login, que é exatamente o caminho que um ataque de força bruta percorre.
     *
     * <p>O filtro por {@code tenantId} é redundante com o RLS, e é assim de
     * propósito: o RLS é a rede de segurança para quando o código erra; o
     * filtro explícito é o que permite usar o índice, que começa por
     * tenant_id. Confiar só no RLS deixaria a consulta correta e lenta.
     */
    @Query("""
            SELECT u FROM AppUserEntity u
             WHERE u.tenantId = :tenantId
               AND LOWER(u.login) = LOWER(:login)
               AND u.deletedAt IS NULL
            """)
    Optional<AppUserEntity> buscarPorLogin(@Param("tenantId") UUID tenantId,
                                           @Param("login") String login);

    @Query("""
            SELECT u FROM AppUserEntity u
             WHERE u.tenantId = :tenantId
               AND (LOWER(u.login) = LOWER(:identificador)
                    OR LOWER(u.email) = LOWER(:identificador))
               AND u.deletedAt IS NULL
            """)
    Optional<AppUserEntity> buscarPorLoginOuEmail(@Param("tenantId") UUID tenantId,
                                                   @Param("identificador") String identificador);

    Optional<AppUserEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    @Query("""
            SELECT u FROM AppUserEntity u
             WHERE u.tenantId = :tenantId
               AND u.id IN :ids
            """)
    List<AppUserEntity> buscarPorIds(@Param("tenantId") UUID tenantId,
                                     @Param("ids") Collection<UUID> ids);
}
