package br.com.pnp.crm;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Login inválido responde igual, não importa o motivo.
 *
 * <p>Se usuário inexistente e senha errada produzissem respostas distintas —
 * no corpo, no status <b>ou no tempo</b> — a tela de login viraria um
 * verificador de contas: o atacante descobre quais logins existem antes de
 * tentar qualquer senha, e concentra o esforço só neles.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class LoginRespostaUniformeTest {

    /**
     * Piso de tempo que só é alcançado se o Argon2 tiver realmente rodado.
     * Bem abaixo do custo real (dezenas de ms) para não ficar sensível à
     * máquina, mas muito acima de um caminho que retorna sem calcular hash.
     */
    private static final Duration CUSTO_MINIMO_ESPERADO = Duration.ofMillis(5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioMultiTenant cenario;

    @BeforeEach
    void prepararTenant() {
        cenario.limpar();
        cenario.criarUsuario(cenario.criarTenant("alpha", "Alpha"), "peixoto", "12345");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    @DisplayName("usuário inexistente e senha errada produzem a mesma resposta")
    void respostasIndistinguiveis() throws Exception {
        Resultado usuarioInexistente = tentarLogin("alpha", "nao-existe", "12345");
        Resultado senhaErrada = tentarLogin("alpha", "peixoto", "senha-errada");

        assertThat(usuarioInexistente.status()).isEqualTo(senhaErrada.status());
        assertThat(usuarioInexistente.codigo()).isEqualTo(senhaErrada.codigo());
        assertThat(usuarioInexistente.mensagem()).isEqualTo(senhaErrada.mensagem());
    }

    @Test
    @DisplayName("empresa inexistente responde igual a credencial errada")
    void tenantInexistenteNaoSeDenuncia() throws Exception {
        Resultado empresaInexistente = tentarLogin("nao-existe", "peixoto", "12345");
        Resultado senhaErrada = tentarLogin("alpha", "peixoto", "senha-errada");

        assertThat(empresaInexistente.status()).isEqualTo(senhaErrada.status());
        assertThat(empresaInexistente.codigo()).isEqualTo(senhaErrada.codigo());
        assertThat(empresaInexistente.mensagem()).isEqualTo(senhaErrada.mensagem());
    }

    @Test
    @DisplayName("o caminho do usuário inexistente também paga o custo do hash")
    void tempoNaoDenunciaExistenciaDoUsuario() throws Exception {
        // Comparar as duas durações entre si seria instável em CI. O que
        // importa provar é que o caminho "usuário não existe" não retorna de
        // graça: se ele pulasse o hash dummy, ficaria ordens de grandeza mais
        // rápido e ficaria abaixo deste piso.
        assertThat(tentarLogin("alpha", "nao-existe", "12345").duracao())
                .isGreaterThan(CUSTO_MINIMO_ESPERADO);

        assertThat(tentarLogin("nao-existe", "peixoto", "12345").duracao())
                .isGreaterThan(CUSTO_MINIMO_ESPERADO);
    }

    @Test
    @DisplayName("nenhuma resposta de erro devolve cookie de sessão")
    void falhaNaoAbreSessao() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("alpha", "peixoto", "senha-errada")))
                .andReturn();

        assertThat(resultado.getResponse().getCookie("crm_refresh")).isNull();
    }

    private Resultado tentarLogin(String empresa, String login, String senha) throws Exception {
        long inicio = System.nanoTime();
        MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(empresa, login, senha)))
                .andReturn();
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);

        String json = resultado.getResponse().getContentAsString();

        return new Resultado(
                resultado.getResponse().getStatus(),
                JsonPath.<String>read(json, "$.codigo"),
                JsonPath.<String>read(json, "$.detail"),
                duracao);
    }

    /**
     * JSON montado à mão de propósito: serializar um {@code Map} deixaria o
     * teste dependente da configuração do serializador da aplicação, que é
     * parte do que está sob teste.
     */
    private String corpo(String empresa, String login, String senha) {
        return """
                {"empresa":"%s","login":"%s","senha":"%s"}
                """.formatted(empresa, login, senha);
    }

    private record Resultado(int status, String codigo, String mensagem, Duration duracao) {
    }
}
