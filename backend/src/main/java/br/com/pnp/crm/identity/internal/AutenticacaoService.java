package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.identity.api.UsuarioAutenticado;
import br.com.pnp.crm.shared.api.CredenciaisInvalidasException;
import br.com.pnp.crm.shared.api.SessaoExpiradaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.tenant.api.TenantLookup;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Orquestra login, MFA, rotação e revogação de sessão. */
@Service
class AutenticacaoService {

    private final TenantLookup tenantLookup;
    private final AppUserRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioProgressivoService bloqueio;
    private final MfaService mfa;
    private final String hashDummy;

    AutenticacaoService(TenantLookup tenantLookup, AppUserRepository usuarios,
                        PasswordEncoder passwordEncoder, AccessTokenService accessTokenService,
                        RefreshTokenService refreshTokenService,
                        BloqueioProgressivoService bloqueio, MfaService mfa) {
        this.tenantLookup = tenantLookup;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueio = bloqueio;
        this.mfa = mfa;
        // Gerado pelo encoder real na subida: acompanha automaticamente toda
        // mudança de parâmetros e mantém o custo de conta inexistente igual.
        this.hashDummy = passwordEncoder.encode(SegredosAleatorios.gerarUrlSeguro(24));
    }

    SessaoAberta entrar(String tenantSlug, String login, String senha,
                        String codigoMfa, String ip) {
        Credencial credencial = validarCredencial(tenantSlug, login, senha, ip);
        return TenantContext.executarComo(credencial.tenantId(), () -> {
            boolean mfaVerified;
            try {
                mfaVerified = mfa.verifyForLogin(
                        credencial.tenantId(), credencial.usuario().getId(), codigoMfa);
            } catch (MfaInvalidException invalid) {
                bloqueio.registrarFalha(credencial.tenantId(), login, ip);
                throw invalid;
            }
            bloqueio.registrarSucesso(credencial.tenantId(), login, ip);
            return abrirSessao(credencial.usuario(), mfaVerified);
        });
    }

    MfaService.Enrollment iniciarCadastroMfa(String tenantSlug, String login, String senha,
                                             String codigoMfaAtual, String ip) {
        Credencial credencial = validarCredencial(tenantSlug, login, senha, ip);
        return TenantContext.executarComo(credencial.tenantId(), () -> {
            try {
                MfaService.Enrollment enrollment = mfa.startEnrollment(
                        credencial.tenantId(), credencial.usuario(), codigoMfaAtual, tenantSlug);
                bloqueio.registrarSucesso(credencial.tenantId(), login, ip);
                return enrollment;
            } catch (MfaInvalidException invalid) {
                bloqueio.registrarFalha(credencial.tenantId(), login, ip);
                throw invalid;
            }
        });
    }

    CadastroMfaAtivado ativarCadastroMfa(String challenge, String codigo) {
        UUID tenantId = mfa.resolveEnrollmentTenant(challenge)
                .orElseThrow(MfaEnrollmentInvalidException::new);
        return TenantContext.executarComo(tenantId, () -> {
            MfaService.Activation activation = mfa.activate(tenantId, challenge, codigo);
            AppUserEntity user = buscarUsuarioAtivoPorId(tenantId, activation.userId())
                    .orElseThrow(MfaEnrollmentInvalidException::new);
            return new CadastroMfaAtivado(abrirSessao(user, true), activation.recoveryCodes());
        });
    }

    SessaoAberta renovar(String refreshTokenApresentado) {
        UUID tenant = refreshTokenService.resolverTenant(refreshTokenApresentado)
                .orElseThrow(SessaoExpiradaException::new);
        return TenantContext.executarComo(tenant, () -> {
            RefreshTokenService.RotacaoResultado rotation =
                    refreshTokenService.rotacionar(refreshTokenApresentado);
            AppUserEntity user = buscarUsuarioAtivoPorId(tenant, rotation.usuarioId())
                    .orElseThrow(SessaoExpiradaException::new);
            String accessToken = accessTokenService.emitir(
                    user.getId(), tenant, user.getLogin(),
                    rotation.token().familyId(), rotation.mfaVerified());
            return new SessaoAberta(accessToken, rotation.token(), paraDto(user),
                    rotation.mfaVerified());
        });
    }

    void sair(UUID tenantId, UUID usuarioId, UUID familyId) {
        TenantContext.executarComo(tenantId, () -> {
            refreshTokenService.revogarFamilia(tenantId, familyId, "logout");
            return null;
        });
    }

    void sairDeTodosOsDispositivos(UUID tenantId, UUID usuarioId) {
        TenantContext.executarComo(tenantId, () -> {
            refreshTokenService.encerrarSessoesDoUsuario(
                    tenantId, usuarioId, "revogacao solicitada pelo usuario");
            return null;
        });
    }

    @Transactional(readOnly = true)
    Optional<UsuarioAutenticado> descrever(UUID tenantId, UUID usuarioId) {
        return buscarUsuarioAtivoPorId(tenantId, usuarioId).map(AutenticacaoService::paraDto);
    }

    @Transactional(readOnly = true)
    Optional<AppUserEntity> buscarUsuarioAtivo(UUID tenantId, String login) {
        return usuarios.buscarPorLogin(tenantId, normalizarLogin(login))
                .filter(AppUserEntity::isActive);
    }

    @Transactional(readOnly = true)
    Optional<AppUserEntity> buscarUsuarioAtivoPorId(UUID tenantId, UUID usuarioId) {
        return usuarios.findByIdAndTenantIdAndDeletedAtIsNull(usuarioId, tenantId)
                .filter(AppUserEntity::isActive);
    }

    private Credencial validarCredencial(String tenantSlug, String login, String senha, String ip) {
        Optional<UUID> tenantId = tenantLookup.resolverIdPorSlug(tenantSlug);
        if (tenantId.isEmpty()) {
            gastarTempoDeVerificacao(senha);
            throw new CredenciaisInvalidasException();
        }
        UUID tenant = tenantId.get();
        if (bloqueio.estaBloqueado(tenant, login, ip)) {
            gastarTempoDeVerificacao(senha);
            throw new CredenciaisInvalidasException();
        }

        return TenantContext.executarComo(tenant, () -> {
            Optional<AppUserEntity> user = buscarUsuarioAtivo(tenant, login);
            boolean matches = user.map(found ->
                    passwordEncoder.matches(senha, found.getPasswordHash())).orElseGet(() -> {
                        gastarTempoDeVerificacao(senha);
                        return false;
                    });
            if (!matches) {
                bloqueio.registrarFalha(tenant, login, ip);
                throw new CredenciaisInvalidasException();
            }
            return new Credencial(tenant, user.orElseThrow());
        });
    }

    private SessaoAberta abrirSessao(AppUserEntity user, boolean mfaVerified) {
        RefreshTokenService.TokenEmitido refresh = refreshTokenService.abrirSessao(
                user.getTenantId(), user.getId(), mfaVerified);
        String accessToken = accessTokenService.emitir(
                user.getId(), user.getTenantId(), user.getLogin(),
                refresh.familyId(), mfaVerified);
        return new SessaoAberta(accessToken, refresh, paraDto(user), mfaVerified);
    }

    private void gastarTempoDeVerificacao(String senha) {
        passwordEncoder.matches(senha, hashDummy);
    }

    private static String normalizarLogin(String login) {
        return login == null ? "" : login.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static UsuarioAutenticado paraDto(AppUserEntity entity) {
        return new UsuarioAutenticado(
                entity.getId(), entity.getTenantId(), entity.getLogin(), entity.getFullName());
    }

    private record Credencial(UUID tenantId, AppUserEntity usuario) {
    }

    record SessaoAberta(String accessToken, RefreshTokenService.TokenEmitido refreshToken,
                        UsuarioAutenticado usuario, boolean mfaVerified) {
    }

    record CadastroMfaAtivado(SessaoAberta sessao, java.util.List<String> codigosRecuperacao) {
        CadastroMfaAtivado {
            codigosRecuperacao = java.util.List.copyOf(codigosRecuperacao);
        }
    }
}
