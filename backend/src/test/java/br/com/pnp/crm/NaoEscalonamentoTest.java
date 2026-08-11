package br.com.pnp.crm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administrar papel não é poder se tornar qualquer coisa.
 *
 * <p><b>Por que este teste existe.</b> {@code organization.manage} dá a alguém
 * o poder de criar papel e atribuí-lo. Sem restrição, essa pessoa escreveria um
 * papel com {@code privacy.manage} e {@code audit.read}, atribuiria a si mesma,
 * e teria concedido a si o que ninguém lhe deu — escalonamento por um caminho
 * que parece administração de rotina.
 *
 * <p>Os casos cobrem as três formas de contornar a regra: conceder permissão
 * que não se tem, conceder sob alcance mais amplo que o próprio, e repropor um
 * papel poderoso que já existia. A terceira é a que costuma faltar.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class NaoEscalonamentoTest {

    private static final String ACIMA_DO_PRIVILEGIO = "CONCESSAO_ACIMA_DO_PRIVILEGIO";

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;

    private UUID tenant;
    private UUID dono;
    private UUID gestor;
    private UUID vendedor;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha");
        dono = cenario.criarUsuario(tenant, "dono", "12345");
        gestor = cenario.criarUsuario(tenant, "gestor", "12345");
        vendedor = cenario.criarUsuario(tenant, "vendedor", "12345");

        // O dono tem tudo. O gestor administra, mas só possui parte — é
        // exatamente a configuração em que o escalonamento seria tentador.
        cenario.concederTudoNoTenant(tenant, dono,
                "organization.manage", "contacts.read", "contacts.write",
                "deals.read", "deals.write", "privacy.manage", "audit.read");
        cenario.concederTudoNoTenant(tenant, gestor,
                "organization.manage", "contacts.read", "deals.read");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    // -----------------------------------------------------------------------
    // 1. Conceder exige possuir
    // -----------------------------------------------------------------------

    @Test
    void naoSeCriaPapelComPermissaoQueOAutorNaoTem() throws Exception {
        mockMvc.perform(post("/api/organizacao/papeis").with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"DIRETOR","nome":"Diretor","descricao":null,
                                 "permissoes":["contacts.read","privacy.manage"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(ACIMA_DO_PRIVILEGIO));
    }

    @Test
    void aMensagemNaoRevelaQualPermissaoFaltou() throws Exception {
        // Enumerar o que falta devolveria, a cada tentativa, um mapa do próprio
        // privilégio — útil justamente para quem está sondando.
        mockMvc.perform(post("/api/organizacao/papeis").with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"DIRETOR","nome":"Diretor","descricao":null,
                                 "permissoes":["audit.read"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("audit.read"))));
    }

    @Test
    void oQueOAutorPossuiEhAceito() throws Exception {
        mockMvc.perform(post("/api/organizacao/papeis").with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"SDR","nome":"SDR","descricao":null,
                                 "permissoes":["contacts.read","deals.read"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes.length()").value(2));
    }

    // -----------------------------------------------------------------------
    // 2. Conceder exige alcance ao menos igual
    // -----------------------------------------------------------------------

    @Test
    void quemSoTemAlcanceProprioNaoConcedeNoTenant() throws Exception {
        UUID restrito = cenario.criarUsuario(tenant, "restrito", "12345");
        cenario.concederTudoNoTenant(tenant, restrito, "organization.manage");
        cenario.conceder(tenant, restrito, "OWN", "contacts.read");

        UUID papel = criarPapel(dono, "CARTEIRA", "contacts.read");

        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(restrito))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"TENANT"}
                                """.formatted(papel)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(ACIMA_DO_PRIVILEGIO));
    }

    @Test
    void quemSoTemAlcanceProprioConcedeAlcanceProprio() throws Exception {
        UUID restrito = cenario.criarUsuario(tenant, "restrito", "12345");
        cenario.concederTudoNoTenant(tenant, restrito, "organization.manage");
        cenario.conceder(tenant, restrito, "OWN", "contacts.read");

        UUID papel = criarPapel(dono, "CARTEIRA", "contacts.read");

        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(restrito))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"OWN"}
                                """.formatted(papel)))
                .andExpect(status().isNoContent());
    }

    @Test
    void oAlcanceEhVerificadoPorPermissaoENaoPeloConjunto() throws Exception {
        // A armadilha: o autor tem ALGUMA coisa em TENANT, então uma checagem
        // preguiçosa concluiria que ele "tem alcance de tenant" e liberaria a
        // permissão que ele só possui sob alcance próprio.
        UUID misto = cenario.criarUsuario(tenant, "misto", "12345");
        cenario.concederTudoNoTenant(tenant, misto, "organization.manage", "contacts.read");
        cenario.conceder(tenant, misto, "OWN", "deals.write");

        UUID papel = criarPapel(dono, "FECHADOR", "deals.write");

        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(misto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"TENANT"}
                                """.formatted(papel)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(ACIMA_DO_PRIVILEGIO));
    }

    // -----------------------------------------------------------------------
    // 3. Editar exige conter
    // -----------------------------------------------------------------------

    @Test
    void naoSeEditaPapelQueConcedeAlemDoAutor() throws Exception {
        UUID poderoso = criarPapel(dono, "AUDITOR", "audit.read", "privacy.manage");

        mockMvc.perform(put("/api/organizacao/papeis/" + poderoso).with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Comercial","descricao":null,"ativo":true}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(ACIMA_DO_PRIVILEGIO));
    }

    @Test
    void naoSeReduzPapelPoderosoParaDepoisSeApropriarDele() throws Exception {
        // Sem "editar exige conter", este é o caminho: pegar o papel poderoso,
        // trocar as permissões pelas suas, e ficar com um papel já atribuído a
        // outras pessoas — cujo privilégio muda sem nenhuma atribuição nova.
        UUID poderoso = criarPapel(dono, "AUDITOR", "audit.read", "privacy.manage");

        mockMvc.perform(put("/api/organizacao/papeis/" + poderoso + "/permissoes")
                        .with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissoes":["contacts.read"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(ACIMA_DO_PRIVILEGIO));
    }

    @Test
    void oAutorEditaOPapelQueEleProprioPoderiaTerCriado() throws Exception {
        UUID papel = criarPapel(gestor, "SDR", "contacts.read");

        mockMvc.perform(put("/api/organizacao/papeis/" + papel + "/permissoes")
                        .with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissoes":["contacts.read","deals.read"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoes.length()").value(2));
    }

    // -----------------------------------------------------------------------
    // 4. A propriedade que as três regras juntas produzem
    // -----------------------------------------------------------------------

    @Test
    void oPrivilegioDoTenantNaoCrescePorDelegacao() throws Exception {
        // O dono delega ao gestor um subconjunto; o gestor delega adiante. Em
        // nenhum ponto da cadeia aparece permissão que o dono não tivesse.
        // Subconjunto de subconjunto continua subconjunto — é o que garante que
        // quem entra depois nunca excede quem concedeu.
        UUID papel = criarPapel(gestor, "SDR", "contacts.read", "deals.read");

        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"TENANT"}
                                """.formatted(papel)))
                .andExpect(status().isNoContent());

        // O vendedor agora administra — e continua sem alcançar o que o gestor
        // nunca teve.
        cenario.concederTudoNoTenant(tenant, vendedor, "organization.manage");

        mockMvc.perform(post("/api/organizacao/papeis").with(como(vendedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"SUPER","nome":"Super","descricao":null,
                                 "permissoes":["privacy.manage"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(ACIMA_DO_PRIVILEGIO));
    }

    @Test
    void oCatalogoMarcaOQueOAutorNaoPodeConceder() throws Exception {
        // A tela desabilita o que não pode ser concedido em vez de deixar
        // tentar e receber 422. É conveniência: a decisão continua no backend.
        mockMvc.perform(get("/api/organizacao/papeis").with(como(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.permissoes[?(@.codigo=='contacts.read')].delegavelNoTenant")
                        .value(true))
                .andExpect(jsonPath(
                        "$.permissoes[?(@.codigo=='privacy.manage')].delegavelNoTenant")
                        .value(false));
    }

    // -----------------------------------------------------------------------

    private UUID criarPapel(UUID autor, String codigo, String... permissoes) throws Exception {
        String lista = String.join("\",\"", permissoes);
        String corpo = mockMvc.perform(post("/api/organizacao/papeis").with(como(autor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"%s","nome":"%s","descricao":null,
                                 "permissoes":["%s"]}
                                """.formatted(codigo, codigo, lista)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(corpo.replaceAll("^.*\"id\":\"([^\"]+)\".*$", "$1"));
    }

    private UUID membership(UUID usuario) {
        return cenario.membershipVigente(tenant, usuario);
    }

    private RequestPostProcessor como(UUID usuario) {
        return jwt().jwt(builder -> builder.subject(usuario.toString())
                .claim("tid", tenant.toString()).claim("login", "teste"));
    }
}
