package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.SessaoExpiradaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Refresh token rotativo com família, reuso e limites de sessão. */
@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    static final Duration VALIDADE = Duration.ofDays(14);
    static final Duration INATIVIDADE_MAXIMA = Duration.ofHours(1);
    static final Duration VALIDADE_ABSOLUTA = Duration.ofHours(24);
    private static final int TAMANHO_SEGREDO_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final EntityManager entityManager;

    RefreshTokenService(RefreshTokenRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    Optional<UUID> resolverTenant(String valorApresentado) {
        List<UUID> encontrados = entityManager
                .createNativeQuery("SELECT resolve_tenant_id_por_refresh_hash(:hash)", UUID.class)
                .setParameter("hash", SegredosAleatorios.sha256(valorApresentado))
                .getResultList();
        return encontrados.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Transactional
    TokenEmitido abrirSessao(UUID tenantId, UUID usuarioId, boolean mfaVerified) {
        Instant startedAt = Instant.now();
        return emitir(tenantId, usuarioId, UuidV7.gerar(), startedAt, mfaVerified);
    }

    @Transactional(noRollbackFor = SessaoExpiradaException.class)
    RotacaoResultado rotacionar(String valorApresentado) {
        UUID tenantId = TenantContext.obrigatorio();
        RefreshTokenEntity token = repository
                .findByTenantIdAndTokenHashForUpdate(
                        tenantId, SegredosAleatorios.sha256(valorApresentado))
                .orElseThrow(SessaoExpiradaException::new);

        if (token.jaFoiUsado()) {
            revogarFamilia(tenantId, token.getFamilyId(), "reuso de refresh token");
            log.warn("Reuso de refresh token detectado. Família revogada. familyId={} tenantId={}",
                    token.getFamilyId(), token.getTenantId());
            throw new SessaoExpiradaException();
        }

        Instant now = Instant.now();
        if (!token.estaValidoEm(now, INATIVIDADE_MAXIMA, VALIDADE_ABSOLUTA)) {
            revogarFamilia(tenantId, token.getFamilyId(), "sessao expirada");
            throw new SessaoExpiradaException();
        }

        token.marcarComoUsado();
        TokenEmitido novo = emitir(token.getTenantId(), token.getUserId(), token.getFamilyId(),
                token.getSessionStartedAt(), token.isMfaVerified());
        return new RotacaoResultado(token.getUserId(), novo, token.isMfaVerified());
    }

    @Transactional
    void revogarFamilia(UUID tenantId, UUID familyId, String motivo) {
        repository.findByTenantIdAndFamilyId(tenantId, familyId)
                .forEach(token -> token.revogar(motivo));
    }

    @Transactional
    void encerrarSessoesDoUsuario(UUID tenantId, UUID usuarioId, String motivo) {
        repository.revogarTodosDoUsuario(tenantId, usuarioId, Instant.now(), motivo);
    }

    private TokenEmitido emitir(UUID tenantId, UUID usuarioId, UUID familyId,
                                Instant sessionStartedAt, boolean mfaVerified) {
        String valor = SegredosAleatorios.gerarUrlSeguro(TAMANHO_SEGREDO_BYTES);
        Instant rollingLimit = Instant.now().plus(VALIDADE);
        Instant absoluteLimit = sessionStartedAt.plus(VALIDADE_ABSOLUTA);
        Instant expiresAt = rollingLimit.isBefore(absoluteLimit) ? rollingLimit : absoluteLimit;

        RefreshTokenEntity entity = RefreshTokenEntity.novo(
                tenantId, usuarioId, familyId, SegredosAleatorios.sha256(valor),
                expiresAt, sessionStartedAt, mfaVerified, usuarioId);
        repository.save(entity);
        // O valor claro existe somente até ser escrito no cookie HttpOnly.
        return new TokenEmitido(valor, expiresAt, familyId, mfaVerified);
    }

    record TokenEmitido(String valor, Instant expiraEm, UUID familyId, boolean mfaVerified) {
    }

    record RotacaoResultado(UUID usuarioId, TokenEmitido token, boolean mfaVerified) {
    }
}
