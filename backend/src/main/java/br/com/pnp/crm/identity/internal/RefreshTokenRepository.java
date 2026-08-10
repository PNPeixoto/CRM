package br.com.pnp.crm.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /** O tenant já foi resolvido pelo hash e definido antes desta consulta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM RefreshTokenEntity t
             WHERE t.tenantId = :tenantId
               AND t.tokenHash = :tokenHash
            """)
    Optional<RefreshTokenEntity> findByTenantIdAndTokenHashForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findByTenantIdAndFamilyId(UUID tenantId, UUID familyId);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
              FROM RefreshTokenEntity t
             WHERE t.tenantId = :tenantId
               AND t.userId = :userId
               AND t.familyId = :familyId
               AND t.usedAt IS NULL
               AND t.revokedAt IS NULL
               AND t.deletedAt IS NULL
               AND t.expiresAt > :agora
               AND t.issuedAt > :limiteInatividade
               AND t.sessionStartedAt > :limiteAbsoluto
            """)
    boolean existeSessaoAtiva(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("familyId") UUID familyId,
            @Param("agora") Instant agora,
            @Param("limiteInatividade") Instant limiteInatividade,
            @Param("limiteAbsoluto") Instant limiteAbsoluto);

    /**
     * Revogação em massa no logout. UPDATE direto em vez de carregar as
     * entidades: o número de sessões abertas de um usuário é imprevisível e
     * não há regra de domínio a aplicar linha a linha.
     */
    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
               SET t.revokedAt = :momento, t.revokedReason = :motivo
             WHERE t.userId = :userId
               AND t.tenantId = :tenantId
               AND t.revokedAt IS NULL
            """)
    int revogarTodosDoUsuario(@Param("tenantId") UUID tenantId,
                              @Param("userId") UUID userId,
                              @Param("momento") Instant momento,
                              @Param("motivo") String motivo);
}
