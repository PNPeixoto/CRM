package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rotação de refresh token e detecção de reuso.
 *
 * <p>O cenário simulado é o roubo: o atacante copia o cookie e o usa. A vítima
 * continua usando o dela. Um dos dois vai apresentar um token já rotacionado —
 * e não há como saber qual é qual. A única resposta segura é derrubar a
 * família inteira e obrigar os dois a fazer login.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class RefreshTokenRotacaoTest {

    private static final String COOKIE = "crm_refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioMultiTenant cenario;

    @Autowired
    private JdbcTemplate jdbc;

    private java.util.UUID tenantId;

    @BeforeEach
    void prepararUsuario() {
        cenario.limpar();
        tenantId = cenario.criarTenant("alpha", "Alpha");
        cenario.criarUsuario(tenantId, "peixoto", "12345");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    @DisplayName("refresh devolve token novo e invalida o anterior")
    void rotacionaARenovar() throws Exception {
        String primeiro = fazerLoginEObterRefresh();
        String segundo = renovarComSucesso(primeiro);

        assertThat(segundo).isNotEqualTo(primeiro);
    }

    @Test
    @DisplayName("usar duas vezes o mesmo refresh token revoga a família inteira")
    void reusoRevogaFamilia() throws Exception {
        String roubado = fazerLoginEObterRefresh();

        // Uso legítimo: rotaciona e devolve um token novo e válido.
        String legitimo = renovarComSucesso(roubado);

        // O atacante apresenta a cópia que guardou. Já foi usada.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, roubado)))
                .andExpect(status().isUnauthorized());

        // A consequência é o ponto do teste: o token da vítima, que era
        // perfeitamente válido, também morre.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, legitimo)))
                .andExpect(status().isUnauthorized());

        Long revogados = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE revoked_at IS NOT NULL", Long.class));
        Long total = TenantContext.executarComo(tenantId,
                () -> jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class));

        // A contagem usa o mesmo runtime restrito e o contexto do tenant.
        assertThat(revogados).isEqualTo(total);
    }

    @Test
    @DisplayName("refresh inexistente responde igual a refresh revogado")
    void tokenDesconhecidoNaoSeDenuncia() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(COOKIE, "valor-que-nunca-existiu")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sem cookie de refresh a resposta é 401, não 500")
    void semCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("duas rotações concorrentes permitem somente uma resposta válida")
    void apenasUmaRotacaoConcorrenteVence() throws Exception {
        String refresh = fazerLoginEObterRefresh();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/auth/refresh")
                                .cookie(new Cookie(COOKIE, refresh)))
                        .andReturn().getResponse().getStatus();
            };

            var first = executor.submit(request);
            var second = executor.submit(request);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 401);
        }
    }

    private String fazerLoginEObterRefresh() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"empresa":"alpha","login":"peixoto","senha":"12345"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = resultado.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly())
                .as("o refresh precisa ser inacessível ao JavaScript")
                .isTrue();
        return cookie.getValue();
    }

    private String renovarComSucesso(String refresh) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(COOKIE, refresh)))
                .andExpect(status().isOk())
                .andReturn();
        return resultado.getResponse().getCookie(COOKIE).getValue();
    }
}
