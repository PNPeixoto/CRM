package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class AutenticacaoSeguraTest {

    private static final String COOKIE = "crm_refresh";

    @Autowired MockMvc mockMvc;
    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void prepare() {
        cenario.limpar();
        clearRedis();
        tenantId = cenario.criarTenant("alpha", "Alpha");
        userId = cenario.criarUsuario(tenantId, "peixoto", "12345");
    }

    @AfterEach
    void clean() {
        cenario.limpar();
        clearRedis();
    }

    @Test
    @DisplayName("cookie tem escopo mínimo e não autentica mutação suscetível a CSRF")
    void cookieCsrfAndRevokeAll() throws Exception {
        MvcResult first = login("12345", null, "10.0.0.1", 200);
        MvcResult second = login("12345", null, "10.0.0.2", 200);
        String setCookie = first.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth")
                .doesNotContain("Domain=");

        String accessToken = JsonPath.read(first.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(post("/api/auth/sessions/revoke-all")
                        .cookie(first.getResponse().getCookie(COOKIE)))
                .andExpect(status().isForbidden());

        // O access token só existe em memória e precisa ser copiado para o
        // Authorization pelo código da própria origem; o navegador não o
        // anexa automaticamente, portanto este caminho não é credencial-cookie.
        mockMvc.perform(post("/api/auth/sessions/revoke-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        refresh(first.getResponse().getCookie(COOKIE).getValue(), 401);
        refresh(second.getResponse().getCookie(COOKIE).getValue(), 401);
    }

    @Test
    @DisplayName("recuperação é uniforme, de uso único e revoga todas as sessões")
    void passwordResetIsSingleUseAndRevokesSessions() throws Exception {
        MvcResult session = login("12345", null, "10.0.1.1", 200);
        String oldRefresh = session.getResponse().getCookie(COOKIE).getValue();

        MvcResult existing = requestReset("alpha", "peixoto");
        MvcResult missing = requestReset("nao-existe", "ninguem");
        assertThat(existing.getResponse().getStatus()).isEqualTo(202);
        assertThat(existing.getResponse().getContentAsString())
                .isEqualTo(missing.getResponse().getContentAsString()).isEmpty();
        assertThat(existing.getResponse().getCookie(COOKIE)).isNull();

        String clearToken = SegredosAleatorios.gerarUrlSeguro(32);
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO password_reset_token
                    (id, tenant_id, user_id, token_hash, expires_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UuidV7.gerar(), tenantId, userId,
                SegredosAleatorios.sha256(clearToken),
                Timestamp.from(Instant.now().plusSeconds(900)), userId));

        String newPassword = "uma senha longa e exclusiva 2026";
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"%s","novaSenha":"%s"}
                                """.formatted(clearToken, newPassword)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"%s","novaSenha":"%s"}
                                """.formatted(clearToken, newPassword)))
                .andExpect(status().isUnprocessableEntity());
        refresh(oldRefresh, 401);
        login("12345", null, "10.0.1.2", 401);
        login(newPassword, null, "10.0.1.3", 200);

        String stored = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject("""
                SELECT token_hash FROM password_reset_token WHERE user_id = ?
                ORDER BY created_at DESC LIMIT 1
                """, String.class, userId));
        assertThat(stored).isNotEqualTo(clearToken).hasSize(64);
    }

    @Test
    @DisplayName("administrador não abre sessão sem MFA e recovery code só vale uma vez")
    void administrativeMfaAndSingleUseRecoveryCode() throws Exception {
        makeOwner();
        MvcResult blocked = login("12345", null, "10.0.2.1", 422);
        assertThat(JsonPath.<String>read(blocked.getResponse().getContentAsString(), "$.codigo"))
                .isEqualTo("MFA_CADASTRO_NECESSARIO");
        assertThat(blocked.getResponse().getCookie(COOKIE)).isNull();

        MvcResult enrollment = mockMvc.perform(post("/api/auth/mfa/enrollment")
                        .with(remoteAddress("10.0.2.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("12345", null)))
                .andExpect(status().isOk()).andReturn();
        String body = enrollment.getResponse().getContentAsString();
        String challenge = JsonPath.read(body, "$.desafio");
        String secret = JsonPath.read(body, "$.segredo");
        assertThat(enrollment.getResponse().getCookie(COOKIE)).isNull();

        String code = Totp.code(secret, Instant.now().getEpochSecond() / 30);
        MvcResult activation = mockMvc.perform(post("/api/auth/mfa/activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"desafio":"%s","codigo":"%s"}
                                """.formatted(challenge, code)))
                .andExpect(status().isOk()).andReturn();
        assertThat(activation.getResponse().getCookie(COOKIE)).isNotNull();
        List<String> recoveryCodes = JsonPath.read(
                activation.getResponse().getContentAsString(), "$.codigosRecuperacao");
        assertThat(recoveryCodes).hasSize(10).allMatch(value -> value.matches("[A-Z2-7-]+"));
        assertThat(activation.getResponse().getContentAsString()).doesNotContain(secret);

        MvcResult required = login("12345", null, "10.0.2.2", 422);
        assertThat(JsonPath.<String>read(required.getResponse().getContentAsString(), "$.codigo"))
                .isEqualTo("MFA_NECESSARIO");

        String recoveryCode = recoveryCodes.getFirst();
        login("12345", recoveryCode, "10.0.2.2", 200);
        MvcResult reused = login("12345", recoveryCode, "10.0.2.3", 422);
        assertThat(JsonPath.<String>read(reused.getResponse().getContentAsString(), "$.codigo"))
                .isEqualTo("MFA_INVALIDO");

        String encrypted = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT secret_ciphertext FROM mfa_authenticator WHERE user_id = ? AND deleted_at IS NULL",
                String.class, userId));
        assertThat(encrypted).isNotEqualTo(secret).doesNotContain(secret);
        Long used = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM mfa_recovery_code WHERE used_at IS NOT NULL", Long.class));
        assertThat(used).isOne();
    }

    @Test
    @DisplayName("rate limit bloqueia a origem, não permite DoS global contra a conta")
    void rateLimitDoesNotGloballyLockVictim() throws Exception {
        for (int attempt = 0; attempt < 6; attempt++) {
            login("errada", null, "10.0.3.1", 401);
        }
        login("12345", null, "10.0.3.1", 401);
        login("12345", null, "10.0.3.2", 200);
    }

    @Test
    @DisplayName("refresh respeita limites de inatividade e duração absoluta")
    void refreshHasInactivityAndAbsoluteLimits() throws Exception {
        String inactive = login("12345", null, "10.0.4.1", 200)
                .getResponse().getCookie(COOKIE).getValue();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                UPDATE refresh_token SET issued_at = now() - interval '61 minutes'
                 WHERE token_hash = ?
                """, SegredosAleatorios.sha256(inactive)));
        refresh(inactive, 401);

        String tooOld = login("12345", null, "10.0.4.2", 200)
                .getResponse().getCookie(COOKIE).getValue();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                UPDATE refresh_token SET session_started_at = now() - interval '25 hours'
                 WHERE token_hash = ?
                """, SegredosAleatorios.sha256(tooOld)));
        refresh(tooOld, 401);
    }

    private MvcResult requestReset(String company, String identifier) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"%s","identificador":"%s"}
                                """.formatted(company, identifier)))
                .andReturn();
    }

    private MvcResult login(String password, String mfaCode, String ip, int status) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(password, mfaCode)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(status)).andReturn();
    }

    private void refresh(String token, int status) throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, token)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().is(status));
    }

    private String loginBody(String password, String mfaCode) {
        String optional = mfaCode == null ? "" : ",\"codigoMfa\":\"" + mfaCode + "\"";
        return "{\"empresa\":\"alpha\",\"login\":\"peixoto\",\"senha\":\""
                + password + "\"" + optional + "}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(
            String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void makeOwner() {
        TenantContext.executarComo(tenantId, () -> {
            UUID membershipId = UuidV7.gerar();
            UUID roleId = UuidV7.gerar();
            jdbc.update("""
                    INSERT INTO organization_membership (id, tenant_id, user_id)
                    VALUES (?, ?, ?)
                    """, membershipId, tenantId, userId);
            jdbc.update("""
                    INSERT INTO app_role (id, tenant_id, code, name, system_role)
                    VALUES (?, ?, 'OWNER', 'Proprietário', true)
                    """, roleId, tenantId);
            jdbc.update("""
                    INSERT INTO membership_scope
                        (id, tenant_id, membership_id, role_id, scope_type)
                    VALUES (?, ?, ?, ?, 'TENANT')
                    """, UuidV7.gerar(), tenantId, membershipId, roleId);
            return null;
        });
    }

    private void clearRedis() {
        var connection = redis.getConnectionFactory().getConnection();
        try {
            connection.serverCommands().flushAll();
        } finally {
            connection.close();
        }
    }
}
