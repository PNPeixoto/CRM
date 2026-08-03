package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import br.com.pnp.crm.tenant.api.TenantLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Recuperação uniforme, de uso único e com revogação total de sessões. */
@Service
class PasswordResetService {

    static final Duration VALIDITY = Duration.ofMinutes(15);

    private final TenantLookup tenantLookup;
    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordResetDelivery delivery;
    private final RefreshTokenService refreshTokens;

    PasswordResetService(TenantLookup tenantLookup, AppUserRepository users,
                         JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                         PasswordPolicy passwordPolicy, PasswordResetDelivery delivery,
                         RefreshTokenService refreshTokens) {
        this.tenantLookup = tenantLookup;
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.delivery = delivery;
        this.refreshTokens = refreshTokens;
    }

    void request(String tenantSlug, String identifier) {
        // O sorteio acontece inclusive para conta inexistente, mantendo o
        // caminho externo e a resposta idênticos.
        String clearToken = SegredosAleatorios.gerarUrlSeguro(32);
        Optional<UUID> tenantId = tenantLookup.resolverIdPorSlug(tenantSlug);
        if (tenantId.isEmpty()) {
            return;
        }
        TenantContext.executarComo(tenantId.get(), () -> {
            users.buscarPorLoginOuEmail(tenantId.get(), identifier.trim())
                    .filter(AppUserEntity::isActive)
                    .ifPresent(user -> createAndDeliver(tenantId.get(), user, clearToken));
            return null;
        });
    }

    @Transactional(readOnly = true)
    Optional<UUID> resolveTenant(String clearToken) {
        List<UUID> found = jdbc.queryForList(
                "SELECT resolve_tenant_id_por_password_reset_hash(?)",
                UUID.class, SegredosAleatorios.sha256(clearToken));
        return found.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Transactional
    void confirm(UUID tenantId, String clearToken, String newPassword) {
        Instant now = Instant.now();
        List<ResetRow> found = jdbc.query("""
                SELECT id, user_id, expires_at, used_at
                  FROM password_reset_token
                 WHERE tenant_id = ? AND token_hash = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new ResetRow(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()),
                tenantId, SegredosAleatorios.sha256(clearToken));
        ResetRow reset = found.stream()
                .filter(row -> row.usedAt() == null && row.expiresAt().isAfter(now))
                .findFirst().orElseThrow(InvalidPasswordResetException::new);

        AppUserEntity user = users.findByIdAndTenantIdAndDeletedAtIsNull(reset.userId(), tenantId)
                .filter(AppUserEntity::isActive)
                .orElseThrow(InvalidPasswordResetException::new);
        String normalized = passwordPolicy.validateAndNormalize(newPassword);
        user.changePasswordHash(passwordEncoder.encode(normalized), user.getId());
        jdbc.update("UPDATE password_reset_token SET used_at = ? WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), tenantId, reset.id());
        jdbc.update("""
                UPDATE password_reset_token SET used_at = ?
                 WHERE tenant_id = ? AND user_id = ? AND used_at IS NULL
                """, Timestamp.from(now), tenantId, user.getId());
        refreshTokens.encerrarSessoesDoUsuario(
                tenantId, user.getId(), "redefinicao de senha");
    }

    private void createAndDeliver(UUID tenantId, AppUserEntity user, String clearToken) {
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE password_reset_token SET used_at = ?
                 WHERE tenant_id = ? AND user_id = ? AND used_at IS NULL
                """, Timestamp.from(now), tenantId, user.getId());
        jdbc.update("""
                INSERT INTO password_reset_token
                    (id, tenant_id, user_id, token_hash, expires_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UuidV7.gerar(), tenantId, user.getId(),
                SegredosAleatorios.sha256(clearToken), Timestamp.from(now.plus(VALIDITY)), user.getId());
        try {
            delivery.deliver(user.getEmail(), clearToken);
        } catch (RuntimeException failure) {
            // A resposta pública continua uniforme; monitoramento operacional
            // identifica a falha sem registrar destinatário ou token.
            org.slf4j.LoggerFactory.getLogger(PasswordResetService.class)
                    .error("Falha na entrega de recuperação de senha. tenantId={} userId={}",
                            tenantId, user.getId());
        }
    }

    private record ResetRow(UUID id, UUID userId, Instant expiresAt, Instant usedAt) {
    }
}
