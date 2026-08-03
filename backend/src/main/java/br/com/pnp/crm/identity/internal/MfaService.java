package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cadastro e verificação TOTP, incluindo códigos de recuperação de uso único. */
@Service
class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);
    private static final Duration ENROLLMENT_VALIDITY = Duration.ofMinutes(10);
    private static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN", "SUPERADMIN");
    private static final int RECOVERY_CODE_COUNT = 10;

    private final JdbcTemplate jdbc;
    private final MfaSecretCipher cipher;
    private final OrganizationAccess organizationAccess;

    MfaService(JdbcTemplate jdbc, MfaSecretCipher cipher,
               OrganizationAccess organizationAccess) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.organizationAccess = organizationAccess;
    }

    @Transactional(readOnly = true)
    Optional<UUID> resolveEnrollmentTenant(String challenge) {
        List<UUID> found = jdbc.queryForList(
                "SELECT resolve_tenant_id_por_mfa_enrollment_hash(?)",
                UUID.class, SegredosAleatorios.sha256(challenge));
        return found.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /**
     * Verifica o segundo fator no login. Para perfis administrativos a ausência
     * de cadastro bloqueia a abertura de sessão; para os demais o uso é opcional.
     */
    @Transactional
    boolean verifyForLogin(UUID tenantId, UUID userId, String suppliedCode) {
        boolean required = requiresMfa(tenantId, userId);
        Optional<Authenticator> authenticator = activeAuthenticator(tenantId, userId, true);

        if (authenticator.isEmpty()) {
            if (required) {
                throw new MfaEnrollmentRequiredException();
            }
            if (suppliedCode != null && !suppliedCode.isBlank()) {
                throw new MfaInvalidException();
            }
            return false;
        }
        if (suppliedCode == null || suppliedCode.isBlank()) {
            throw new MfaRequiredException();
        }
        verifyCode(tenantId, authenticator.get(), suppliedCode);
        return true;
    }

    @Transactional
    Enrollment startEnrollment(UUID tenantId, AppUserEntity user, String currentCode,
                               String tenantSlug) {
        Optional<Authenticator> current = activeAuthenticator(tenantId, user.getId(), true);
        if (current.isPresent()) {
            if (currentCode == null || currentCode.isBlank()) {
                throw new MfaRequiredException();
            }
            verifyCode(tenantId, current.get(), currentCode);
        }

        Instant now = Instant.now();
        jdbc.update("""
                UPDATE mfa_enrollment SET used_at = ?
                 WHERE tenant_id = ? AND user_id = ? AND used_at IS NULL
                """, Timestamp.from(now), tenantId, user.getId());

        String secret = Totp.newSecret();
        String challenge = SegredosAleatorios.gerarUrlSeguro(32);
        jdbc.update("""
                INSERT INTO mfa_enrollment
                    (id, tenant_id, user_id, challenge_hash, secret_ciphertext,
                     key_version, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UuidV7.gerar(), tenantId, user.getId(),
                SegredosAleatorios.sha256(challenge), cipher.encrypt(secret),
                MfaSecretCipher.KEY_VERSION, Timestamp.from(now.plus(ENROLLMENT_VALIDITY)));

        String label = tenantSlug + ":" + user.getLogin();
        String uri = "otpauth://totp/" + encode(label)
                + "?secret=" + secret + "&issuer=" + encode("CRM PNP")
                + "&algorithm=SHA1&digits=6&period=30";
        return new Enrollment(challenge, secret, uri, ENROLLMENT_VALIDITY.toSeconds());
    }

    @Transactional
    Activation activate(UUID tenantId, String challenge, String suppliedCode) {
        EnrollmentRow enrollment = findEnrollmentForUpdate(
                tenantId, SegredosAleatorios.sha256(challenge))
                .filter(row -> row.usedAt() == null && row.expiresAt().isAfter(Instant.now()))
                .orElseThrow(MfaEnrollmentInvalidException::new);

        String secret = cipher.decrypt(enrollment.secretCiphertext());
        Totp.Verification confirmation = Totp.verify(secret, suppliedCode, Instant.now(), null);
        if (!confirmation.valid()) {
            throw new MfaInvalidException();
        }

        Instant now = Instant.now();
        // Um novo cadastro cria outro autenticador e encerra o anterior. Assim
        // códigos já usados permanecem como evidência, mas nunca voltam a valer.
        activeAuthenticator(tenantId, enrollment.userId(), true).ifPresent(previous ->
                jdbc.update("UPDATE mfa_authenticator SET deleted_at = ? WHERE tenant_id = ? AND id = ?",
                        Timestamp.from(now), tenantId, previous.id()));
        UUID authenticatorId = UuidV7.gerar();
        jdbc.update("""
                INSERT INTO mfa_authenticator
                    (id, tenant_id, user_id, secret_ciphertext, key_version,
                     activated_at, last_used_at, last_used_step)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, authenticatorId, tenantId, enrollment.userId(), cipher.encrypt(secret),
                MfaSecretCipher.KEY_VERSION, Timestamp.from(now), Timestamp.from(now),
                confirmation.step());
        List<String> recoveryCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            String code = formatRecoveryCode(base32Recovery());
            recoveryCodes.add(code);
            jdbc.update("""
                    INSERT INTO mfa_recovery_code
                        (id, tenant_id, authenticator_id, code_hash)
                    VALUES (?, ?, ?, ?)
                    """, UuidV7.gerar(), tenantId, authenticatorId,
                    SegredosAleatorios.sha256(normalizeRecoveryCode(code)));
        }
        jdbc.update("UPDATE mfa_enrollment SET used_at = ? WHERE tenant_id = ? AND id = ?",
                Timestamp.from(now), tenantId, enrollment.id());
        log.info("MFA cadastrado. tenantId={} userId={} authenticatorId={}",
                tenantId, enrollment.userId(), authenticatorId);
        return new Activation(enrollment.userId(), recoveryCodes);
    }

    private void verifyCode(UUID tenantId, Authenticator authenticator, String suppliedCode) {
        String normalized = suppliedCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.matches("\\d{6}")) {
            Totp.Verification verification = Totp.verify(
                    cipher.decrypt(authenticator.secretCiphertext()), normalized,
                    Instant.now(), authenticator.lastUsedStep());
            if (!verification.valid()) {
                throw new MfaInvalidException();
            }
            jdbc.update("""
                    UPDATE mfa_authenticator SET last_used_at = ?, last_used_step = ?
                     WHERE tenant_id = ? AND id = ?
                    """, Timestamp.from(Instant.now()), verification.step(),
                    tenantId, authenticator.id());
            return;
        }

        int consumed = jdbc.update("""
                UPDATE mfa_recovery_code SET used_at = ?
                 WHERE tenant_id = ? AND authenticator_id = ?
                   AND code_hash = ? AND used_at IS NULL
                """, Timestamp.from(Instant.now()), tenantId, authenticator.id(),
                SegredosAleatorios.sha256(normalizeRecoveryCode(normalized)));
        if (consumed != 1) {
            throw new MfaInvalidException();
        }
        log.warn("Código de recuperação MFA utilizado. tenantId={} userId={} authenticatorId={}",
                tenantId, authenticator.userId(), authenticator.id());
    }

    private boolean requiresMfa(UUID tenantId, UUID userId) {
        return organizationAccess.hasAnyRole(tenantId, userId, ADMIN_ROLES);
    }

    private Optional<Authenticator> activeAuthenticator(UUID tenantId, UUID userId,
                                                        boolean forUpdate) {
        String sql = """
                SELECT id, user_id, secret_ciphertext, last_used_step
                  FROM mfa_authenticator
                 WHERE tenant_id = ? AND user_id = ? AND deleted_at IS NULL
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<Authenticator> rows = jdbc.query(sql, (rs, rowNum) -> new Authenticator(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("secret_ciphertext"), (Long) rs.getObject("last_used_step")),
                tenantId, userId);
        return rows.stream().findFirst();
    }

    private Optional<EnrollmentRow> findEnrollmentForUpdate(UUID tenantId, String challengeHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT id, user_id, secret_ciphertext, expires_at, used_at
                      FROM mfa_enrollment
                     WHERE tenant_id = ? AND challenge_hash = ?
                     FOR UPDATE
                    """, (rs, rowNum) -> new EnrollmentRow(
                    rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getString("secret_ciphertext"), rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()),
                    tenantId, challengeHash));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static String base32Recovery() {
        // 80 bits, acima dos 64 bits mínimos para look-up secrets do NIST.
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] random = SegredosAleatorios.gerarBytes(10);
        StringBuilder result = new StringBuilder(16);
        int buffer = 0;
        int bits = 0;
        for (byte item : random) {
            buffer = (buffer << 8) | (item & 0xff);
            bits += 8;
            while (bits >= 5) {
                result.append(alphabet.charAt((buffer >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        return result.toString();
    }

    private static String formatRecoveryCode(String raw) {
        return raw.replaceAll("(.{4})(?=.)", "$1-");
    }

    private static String normalizeRecoveryCode(String raw) {
        return raw == null ? "" : raw.replace("-", "").replace(" ", "")
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    record Enrollment(String challenge, String secret, String otpauthUri, long expiresInSeconds) {
    }

    record Activation(UUID userId, List<String> recoveryCodes) {
        Activation {
            recoveryCodes = List.copyOf(recoveryCodes);
        }
    }

    private record Authenticator(UUID id, UUID userId, String secretCiphertext,
                                 Long lastUsedStep) {
    }

    private record EnrollmentRow(UUID id, UUID userId, String secretCiphertext,
                                 Instant expiresAt, Instant usedAt) {
    }
}
