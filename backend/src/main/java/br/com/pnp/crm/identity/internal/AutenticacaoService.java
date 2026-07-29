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

/**
 * Casos de uso de autenticação: entrar, renovar, sair, identificar-se.
 *
 * <p>Esta é a camada de aplicação: orquestra transação, contexto de tenant e
 * bloqueio, e não contém regra criptográfica nem acesso direto a dados.
 */
@Service
class AutenticacaoService {

    /**
     * Hash Argon2id de um valor que nenhum usuário tem, usado quando o login
     * não existe.
     *
     * <p>Sem isto, o caminho "usuário inexistente" retorna em microssegundos e
     * o caminho "senha errada" gasta o tempo do Argon2 — dezenas de
     * milissegundos, medíveis pela rede. A diferença transforma a tela de
     * login num verificador de existência de contas, que é o primeiro passo de
     * qualquer ataque dirigido. Comparar contra este hash iguala os dois
     * tempos.
     *
     * <p>Foi gerado com os mesmos parâmetros do encoder real. Não é segredo e
     * não precisa ser: ele existe para gastar tempo, não para proteger nada.
     */
    private static final String HASH_DUMMY =
            "$argon2id$v=19$m=16384,t=2,p=1$3nUgapjwXyOihLKkHEykhQ$+y6IgEuBhsJemmGjKglEZv3MQvbtcuGvu9Tln3JwzaQ";

    private final TenantLookup tenantLookup;
    private final AppUserRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final BloqueioProgressivoService bloqueio;

    AutenticacaoService(TenantLookup tenantLookup,
                        AppUserRepository usuarios,
                        PasswordEncoder passwordEncoder,
                        AccessTokenService accessTokenService,
                        RefreshTokenService refreshTokenService,
                        BloqueioProgressivoService bloqueio) {
        this.tenantLookup = tenantLookup;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.bloqueio = bloqueio;
    }

    /**
     * Autentica e abre sessão.
     *
     * <p>Todo caminho de falha termina em {@link CredenciaisInvalidasException}
     * — mesmo corpo, mesmo status, mesmo tempo. Tenant inexistente, login
     * inexistente, senha errada e conta inativa são indistinguíveis de fora.
     */
    SessaoAberta entrar(String tenantSlug, String login, String senha, String ip) {
        // Tenant inexistente também precisa gastar o tempo do hash, senão o
        // slug vira o canal de enumeração que o login deixou de ser.
        Optional<UUID> tenantId = tenantLookup.resolverIdPorSlug(tenantSlug);
        if (tenantId.isEmpty()) {
            gastarTempoDeVerificacao(senha);
            throw new CredenciaisInvalidasException();
        }

        UUID tenant = tenantId.get();

        if (bloqueio.estaBloqueado(tenant, login, ip)) {
            // Resposta idêntica à de credencial errada: dizer "bloqueado"
            // confirmaria que a conta existe e que alguém está atacando.
            throw new CredenciaisInvalidasException();
        }

        return TenantContext.executarComo(tenant, () -> {
            Optional<AppUserEntity> usuario = buscarUsuarioAtivo(tenant, login);

            if (usuario.isEmpty() || !passwordEncoder.matches(senha, usuario.get().getPasswordHash())) {
                // A chamada acima já gastou o tempo do Argon2 quando o usuário
                // existe. Quando não existe, o curto-circuito do || pularia a
                // verificação, então ela é feita aqui contra o hash dummy.
                if (usuario.isEmpty()) {
                    gastarTempoDeVerificacao(senha);
                }
                bloqueio.registrarFalha(tenant, login, ip);
                throw new CredenciaisInvalidasException();
            }

            AppUserEntity encontrado = usuario.get();
            bloqueio.registrarSucesso(tenant, login);

            String accessToken = accessTokenService.emitir(
                    encontrado.getId(), tenant, encontrado.getLogin());
            RefreshTokenService.TokenEmitido refresh =
                    refreshTokenService.abrirSessao(tenant, encontrado.getId());

            return new SessaoAberta(accessToken, refresh, paraDto(encontrado));
        });
    }

    /**
     * Rotaciona o refresh token e emite um novo access token.
     *
     * <p>O tenant é descoberto a partir do próprio token, nunca informado pelo
     * cliente, e o contexto só é definido depois disso — daí a resolução
     * acontecer fora do {@code executarComo}.
     */
    SessaoAberta renovar(String refreshTokenApresentado) {
        UUID tenant = refreshTokenService.resolverTenant(refreshTokenApresentado)
                .orElseThrow(SessaoExpiradaException::new);

        return TenantContext.executarComo(tenant, () -> {
            RefreshTokenService.RotacaoResultado rotacao =
                    refreshTokenService.rotacionar(refreshTokenApresentado);

            AppUserEntity usuario = buscarUsuarioAtivoPorId(tenant, rotacao.usuarioId())
                    // Usuário desativado ou excluído entre a emissão e a
                    // renovação: a sessão morre aqui, sem esperar o token expirar.
                    .orElseThrow(SessaoExpiradaException::new);

            String accessToken = accessTokenService.emitir(
                    usuario.getId(), tenant, usuario.getLogin());

            return new SessaoAberta(accessToken, rotacao.token(), paraDto(usuario));
        });
    }

    void sair(UUID tenantId, UUID usuarioId) {
        TenantContext.executarComo(tenantId, () -> {
            refreshTokenService.encerrarSessoesDoUsuario(usuarioId, "logout");
            return null;
        });
    }

    @Transactional(readOnly = true)
    Optional<UsuarioAutenticado> descrever(UUID tenantId, UUID usuarioId) {
        return buscarUsuarioAtivoPorId(tenantId, usuarioId).map(AutenticacaoService::paraDto);
    }

    @Transactional(readOnly = true)
    Optional<AppUserEntity> buscarUsuarioAtivo(UUID tenantId, String login) {
        return usuarios.findByTenantIdAndLoginAndDeletedAtIsNull(tenantId, login)
                .filter(AppUserEntity::isActive);
    }

    @Transactional(readOnly = true)
    Optional<AppUserEntity> buscarUsuarioAtivoPorId(UUID tenantId, UUID usuarioId) {
        return usuarios.findByIdAndTenantIdAndDeletedAtIsNull(usuarioId, tenantId)
                .filter(AppUserEntity::isActive);
    }

    private void gastarTempoDeVerificacao(String senha) {
        passwordEncoder.matches(senha, HASH_DUMMY);
    }

    private static UsuarioAutenticado paraDto(AppUserEntity entity) {
        return new UsuarioAutenticado(
                entity.getId(), entity.getTenantId(), entity.getLogin(), entity.getFullName());
    }

    record SessaoAberta(String accessToken,
                        RefreshTokenService.TokenEmitido refreshToken,
                        UsuarioAutenticado usuario) {
    }
}
