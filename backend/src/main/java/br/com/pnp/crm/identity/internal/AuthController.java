package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.identity.api.UsuarioAutenticado;
import br.com.pnp.crm.shared.api.SessaoExpiradaException;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final String COOKIE_REFRESH = "crm_refresh";

    /**
     * O cookie só é enviado para {@code /api/auth}. Qualquer outra rota da API
     * nunca o recebe, o que reduz a superfície: um endpoint com falha de log
     * ou de eco não tem como vazar um cookie que não chega até ele.
     */
    private static final String CAMINHO_COOKIE = "/api/auth";

    private final AutenticacaoService autenticacao;
    private final PasswordResetService passwordReset;
    private final boolean cookieSecure;

    AuthController(AutenticacaoService autenticacao, PasswordResetService passwordReset,
                   @Value("${app.security.cookie-secure:true}") boolean cookieSecure) {
        this.autenticacao = autenticacao;
        this.passwordReset = passwordReset;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    ResponseEntity<AuthDtos.SessaoResponse> login(@Valid @RequestBody AuthDtos.LoginRequest requisicao,
                                                  HttpServletRequest http) {
        AutenticacaoService.SessaoAberta sessao = autenticacao.entrar(
                requisicao.empresa(), requisicao.login(), requisicao.senha(),
                requisicao.codigoMfa(), ipDaRequisicao(http));
        return respostaComCookie(sessao);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthDtos.SessaoResponse> refresh(
            @CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SessaoExpiradaException();
        }
        return respostaComCookie(autenticacao.renovar(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        autenticacao.sair(tenantDoToken(jwt), UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION)));
        // O cookie é sobrescrito com validade zero. Sem isto o navegador
        // continuaria enviando um refresh já revogado a cada chamada, o que
        // funciona mas polui o log com falhas de sessão que não são incidente.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/sessions/revoke-all")
    ResponseEntity<Void> revokeAll(@AuthenticationPrincipal Jwt jwt) {
        autenticacao.sairDeTodosOsDispositivos(
                tenantDoToken(jwt), UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/mfa/enrollment")
    ResponseEntity<AuthDtos.MfaEnrollmentResponse> startMfaEnrollment(
            @Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest http) {
        MfaService.Enrollment enrollment = autenticacao.iniciarCadastroMfa(
                request.empresa(), request.login(), request.senha(), request.codigoMfa(),
                ipDaRequisicao(http));
        return ResponseEntity.ok(new AuthDtos.MfaEnrollmentResponse(
                enrollment.challenge(), enrollment.secret(), enrollment.otpauthUri(),
                enrollment.expiresInSeconds()));
    }

    @PostMapping("/mfa/activation")
    ResponseEntity<AuthDtos.MfaActivationResponse> activateMfa(
            @Valid @RequestBody AuthDtos.MfaActivationRequest request) {
        AutenticacaoService.CadastroMfaAtivado activated =
                autenticacao.ativarCadastroMfa(request.desafio(), request.codigo());
        ResponseCookie cookie = cookieFor(activated.sessao().refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthDtos.MfaActivationResponse(
                        sessionBody(activated.sessao()), activated.codigosRecuperacao()));
    }

    @PostMapping("/password-reset/request")
    ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        passwordReset.request(request.empresa(), request.identificador());
        // Sempre 202 e corpo vazio, exista ou não empresa/usuário.
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetConfirmation request) {
        UUID tenantId = passwordReset.resolveTenant(request.codigo())
                .orElseThrow(InvalidPasswordResetException::new);
        TenantContext.executarComo(tenantId, () -> {
            passwordReset.confirm(tenantId, request.codigo(), request.novaSenha());
            return null;
        });
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    ResponseEntity<AuthDtos.UsuarioResponse> me(@AuthenticationPrincipal Jwt jwt) {
        UsuarioAutenticado usuario = autenticacao
                .descrever(tenantDoToken(jwt), UUID.fromString(jwt.getSubject()))
                // Token assinado e válido cujo usuário sumiu ou foi desativado:
                // é sessão morta, não erro interno.
                .orElseThrow(SessaoExpiradaException::new);
        return ResponseEntity.ok(new AuthDtos.UsuarioResponse(
                usuario.id(), usuario.tenantId(), usuario.login(), usuario.nomeCompleto()));
    }

    private ResponseEntity<AuthDtos.SessaoResponse> respostaComCookie(
            AutenticacaoService.SessaoAberta sessao) {
        ResponseCookie cookie = cookieRefresh(
                sessao.refreshToken().valor(), cookieValidity(sessao.refreshToken().expiraEm()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(sessionBody(sessao));
    }

    private AuthDtos.SessaoResponse sessionBody(AutenticacaoService.SessaoAberta sessao) {
        return new AuthDtos.SessaoResponse(
                sessao.accessToken(), AccessTokenService.VALIDADE.toSeconds(),
                new AuthDtos.UsuarioResponse(
                        sessao.usuario().id(), sessao.usuario().tenantId(),
                        sessao.usuario().login(), sessao.usuario().nomeCompleto()),
                sessao.mfaVerified());
    }

    private ResponseCookie cookieFor(RefreshTokenService.TokenEmitido token) {
        return cookieRefresh(token.valor(), cookieValidity(token.expiraEm()));
    }

    private Duration cookieValidity(Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * {@code SameSite=Strict} e não {@code None}. O FinUp precisou de
     * {@code None} porque servia frontend e API em origens diferentes; aqui há
     * proxy reverso e mesma origem, então o ajuste mais restritivo funciona —
     * e com ele o navegador simplesmente não envia o cookie em navegação
     * vinda de outro site, que é a raiz do CSRF.
     */
    private ResponseCookie cookieRefresh(String valor, Duration validade) {
        return ResponseCookie.from(COOKIE_REFRESH, valor)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(CAMINHO_COOKIE)
                .maxAge(validade)
                .build();
    }

    private UUID tenantDoToken(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString(AccessTokenService.CLAIM_TENANT));
    }

    /**
     * Usa o IP da conexão, não o {@code X-Forwarded-For}.
     *
     * <p>Ler o cabeçalho pela esquerda — que é o erro presente no FinUp —
     * deixa o cliente escolher a própria identidade: basta mandar
     * {@code X-Forwarded-For: 1.2.3.4} para trocar de IP a cada tentativa e
     * furar o bloqueio por endereço. Enquanto não houver proxy com número
     * conhecido de saltos confiáveis, o IP da conexão é a única informação que
     * o cliente não controla.
     */
    private String ipDaRequisicao(HttpServletRequest http) {
        return http.getRemoteAddr();
    }
}
