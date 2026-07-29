package br.com.pnp.crm.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Busca pelo hash, sem filtrar por tenant: no refresh, o cookie é a única
     * coisa que temos, e é ele quem revela de qual tenant a sessão é. O RLS
     * não atrapalha porque o contexto ainda está vazio nesse ponto — ver
     * {@link RefreshTokenService} para o motivo de a consulta rodar sem tenant.
     */
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByFamilyId(UUID familyId);

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
               AND t.revokedAt IS NULL
            """)
    int revogarTodosDoUsuario(@Param("userId") UUID userId,
                              @Param("momento") Instant momento,
                              @Param("motivo") String motivo);
}
