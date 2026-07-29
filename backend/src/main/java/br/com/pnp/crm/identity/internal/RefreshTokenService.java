package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.SessaoExpiradaException;
import br.com.pnp.crm.shared.api.UuidV7;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token rotativo com detecção de reuso por família.
 *
 * <p><b>O problema que a família resolve:</b> um refresh token roubado é
 * indistinguível do legítimo — os dois são a mesma string. Rotacionar a cada
 * uso não impede o roubo, mas cria um sintoma observável: o token antigo passa
 * a ser inválido, e se alguém o apresentar, existem duas cópias em circulação.
 * Nesse momento não há como saber qual das duas é a vítima, e é por isso que a
 * resposta correta é derrubar a família inteira e obrigar os dois a fazer
 * login. Deixar passar significa manter o atacante dentro.
 */
@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    static final Duration VALIDADE = Duration.ofDays(14);

    // 256 bits. O valor não é adivinhável nem enumerável, e é isso que
    // sustenta a decisão de guardá-lo como SHA-256 simples em vez de Argon2:
    // não existe dicionário para 2^256.
    private static final int TAMANHO_SEGREDO_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository repository;
    private final EntityManager entityManager;

    RefreshTokenService(RefreshTokenRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    /**
     * Descobre o tenant de um refresh token antes de haver contexto.
     *
     * <p>Precisa rodar antes de qualquer coisa: sem tenant definido, o RLS
     * esconde a própria linha que precisamos ler. Ver o comentário da função
     * {@code resolve_tenant_id_por_refresh_hash} na migration V1.
     */
    @Transactional(readOnly = true)
    Optional<UUID> resolverTenant(String valorApresentado) {
        return entityManager
                .createNativeQuery("SELECT resolve_tenant_id_por_refresh_hash(:hash)", UUID.class)
                .setParameter("hash", hashDe(valorApresentado))
                .getResultStream()
                .findFirst()
                .map(UUID.class::cast);
    }

    /**
     * Cria a primeira sessão. A família nasce aqui e acompanha todas as
     * renovações seguintes.
     */
    @Transactional
    TokenEmitido abrirSessao(UUID tenantId, UUID usuarioId) {
        return emitir(tenantId, usuarioId, UuidV7.gerar());
    }

    /**
     * Troca um refresh token válido por um novo, na mesma família.
     *
     * @throws SessaoExpiradaException se o token não existe, expirou, foi
     *                                 revogado — ou já foi usado, caso em que
     *                                 a família inteira é revogada antes de
     *                                 lançar
     */
    @Transactional
    RotacaoResultado rotacionar(String valorApresentado) {
        RefreshTokenEntity token = repository.findByTokenHash(hashDe(valorApresentado))
                .orElseThrow(SessaoExpiradaException::new);

        if (token.jaFoiUsado()) {
            revogarFamilia(token.getFamilyId(), "reuso de refresh token");
            // O log registra o evento sem o valor do token e sem o conteúdo do
            // cookie. O id da família é suficiente para investigar.
            log.warn("Reuso de refresh token detectado. Família revogada. familyId={} tenantId={}",
                    token.getFamilyId(), token.getTenantId());
            throw new SessaoExpiradaException();
        }

        if (!token.estaValidoEm(Instant.now())) {
            throw new SessaoExpiradaException();
        }

        token.marcarComoUsado();
        TokenEmitido novo = emitir(token.getTenantId(), token.getUserId(), token.getFamilyId());
        return new RotacaoResultado(token.getUserId(), novo);
    }

    @Transactional
    void revogarFamilia(UUID familyId, String motivo) {
        List<RefreshTokenEntity> familia = repository.findByFamilyId(familyId);
        familia.forEach(token -> token.revogar(motivo));
    }

    @Transactional
    void encerrarSessoesDoUsuario(UUID usuarioId, String motivo) {
        repository.revogarTodosDoUsuario(usuarioId, Instant.now(), motivo);
    }

    private TokenEmitido emitir(UUID tenantId, UUID usuarioId, UUID familyId) {
        byte[] segredo = new byte[TAMANHO_SEGREDO_BYTES];
        RANDOM.nextBytes(segredo);
        String valor = BASE64.encodeToString(segredo);

        RefreshTokenEntity entity = RefreshTokenEntity.novo(
                tenantId, usuarioId, familyId, hashDe(valor),
                Instant.now().plus(VALIDADE), usuarioId);
        repository.save(entity);

        // O valor em claro sai daqui uma única vez, direto para o cookie. Não
        // é gravado, não é logado e não pode ser recuperado depois.
        return new TokenEmitido(valor, entity.getExpiresAt());
    }

    private static String hashDe(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em toda JVM; se faltar, o ambiente está quebrado.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    record TokenEmitido(String valor, Instant expiraEm) {
    }

    record RotacaoResultado(UUID usuarioId, TokenEmitido token) {
    }
}
