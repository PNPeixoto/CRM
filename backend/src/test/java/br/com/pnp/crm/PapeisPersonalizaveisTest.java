package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O cliente monta a própria hierarquia de papéis.
 *
 * <p>A parte fácil é o CRUD. O que este teste protege são as quatro recusas que
 * transformam uma tela de administração em algo seguro de entregar: papel de
 * sistema imutável, último administrador preservado, papel em uso não some por
 * baixo de quem trabalha, e toda mudança de privilégio deixa rastro.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class PapeisPersonalizaveisTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private UUID tenant;
    private UUID dono;
    private UUID vendedor;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha");
        dono = cenario.criarUsuario(tenant, "dono", "12345");
        vendedor = cenario.criarUsuario(tenant, "vendedor", "12345");

        cenario.concederTudoNoTenant(tenant, dono,
                "organization.manage", "contacts.read", "contacts.write",
                "deals.read", "deals.write", "conversations.read", "reports.read");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    // -----------------------------------------------------------------------
    // O caminho que o cliente percorre
    // -----------------------------------------------------------------------

    @Test
    void oClienteCriaOsProprioPapeisDaHierarquia() throws Exception {
        criar("SDR", "SDR", "contacts.read", "contacts.write", "deals.read");
        criar("CLOSER", "Closer", "deals.read", "deals.write", "reports.read");

        mockMvc.perform(get("/api/organizacao/papeis").with(como(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papeis[?(@.codigo=='SDR')].nome").value("SDR"))
                .andExpect(jsonPath("$.papeis[?(@.codigo=='CLOSER')].permissoes.length()")
                        .value(3));
    }

    @Test
    void renomearPreservaOCodigo() throws Exception {
        UUID papel = criar("SDR", "SDR", "contacts.read");

        mockMvc.perform(put("/api/organizacao/papeis/" + papel).with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Pré-vendas","descricao":"Qualifica lead","ativo":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Pré-vendas"))
                // O código é a identidade estável: renomear é rotina, trocar
                // identidade quebraria toda referência já gravada.
                .andExpect(jsonPath("$.codigo").value("SDR"));
    }

    @Test
    void atribuirPapelApareceNaListaDeMembros() throws Exception {
        UUID papel = criar("SDR", "SDR", "contacts.read");

        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"OWN"}
                                """.formatted(papel)))
                .andExpect(status().isNoContent());

        // Ordenado por nome completo, e o cenário nomeia "Usuário <login>":
        // dono vem antes de vendedor. Indexar é mais legível aqui que um filtro
        // JsonPath aninhado, e a ordenação é parte do contrato da listagem.
        mockMvc.perform(get("/api/organizacao/membros").with(como(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].login").value("vendedor"))
                .andExpect(jsonPath("$[1].atribuicoes[0].papelCodigo").value("SDR"))
                .andExpect(jsonPath("$[1].atribuicoes[0].alcance").value("OWN"));
    }

    @Test
    void oPapelAtribuidoPassaAValerNaRequisicaoSeguinte() throws Exception {
        // Fecha o ciclo: o CRUD não serve de nada se a concessão não chegar à
        // decisão de autorização. O vendedor não lê contato até receber o papel.
        mockMvc.perform(get("/api/contatos").with(como(vendedor)))
                .andExpect(status().isForbidden());

        UUID papel = criar("SDR", "SDR", "contacts.read");
        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"OWN"}
                                """.formatted(papel)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contatos").with(como(vendedor)))
                .andExpect(status().isOk());
    }

    @Test
    void revogarTiraOAcessoNaRequisicaoSeguinte() throws Exception {
        UUID papel = criar("SDR", "SDR", "contacts.read");
        UUID atribuicao = atribuir(papel, vendedor, "OWN");

        mockMvc.perform(get("/api/contatos").with(como(vendedor)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/organizacao/membros/" + membership(vendedor)
                        + "/papeis/" + atribuicao).with(como(dono)))
                .andExpect(status().isNoContent());

        // Vale na chamada seguinte porque a decisão relê o membership; não
        // depende de expirar token nem de invalidar cache entre requisições.
        mockMvc.perform(get("/api/contatos").with(como(vendedor)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // As quatro recusas
    // -----------------------------------------------------------------------

    @Test
    void papelDeSistemaNaoEhEditado() throws Exception {
        UUID sistema = papelDeSistema();

        mockMvc.perform(put("/api/organizacao/papeis/" + sistema).with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Qualquer","descricao":null,"ativo":true}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAPEL_DE_SISTEMA_IMUTAVEL"));
    }

    @Test
    void papelDeSistemaNaoEhRemovido() throws Exception {
        UUID sistema = papelDeSistema();

        mockMvc.perform(delete("/api/organizacao/papeis/" + sistema).with(como(dono)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAPEL_DE_SISTEMA_IMUTAVEL"));
    }

    @Test
    void papelComAtribuicaoVivaNaoEhRemovidoEmCascata() throws Exception {
        UUID papel = criar("SDR", "SDR", "contacts.read");
        atribuir(papel, vendedor, "OWN");

        // Apagar revogaria o acesso de quem está trabalhando, sem aviso. A
        // recusa traz a contagem para a decisão ser consciente.
        mockMvc.perform(delete("/api/organizacao/papeis/" + papel).with(como(dono)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAPEL_EM_USO"));
    }

    @Test
    void papelSemAtribuicaoEhRemovido() throws Exception {
        UUID papel = criar("TEMPORARIO", "Temporário", "contacts.read");

        mockMvc.perform(delete("/api/organizacao/papeis/" + papel).with(como(dono)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/organizacao/papeis").with(como(dono)))
                .andExpect(jsonPath("$.papeis[?(@.codigo=='TEMPORARIO')]").isEmpty());
    }

    @Test
    void aUltimaAtribuicaoDeSistemaNaoEhRevogada() throws Exception {
        UUID sistema = papelDeSistema();
        UUID atribuicao = atribuirDireto(sistema, dono);

        // Sem esta recusa o tenant fica sem ninguém capaz de criar papel ou
        // conceder permissão, e a saída passa a exigir acesso ao banco.
        mockMvc.perform(delete("/api/organizacao/membros/" + membership(dono)
                        + "/papeis/" + atribuicao).with(como(dono)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ULTIMO_PROPRIETARIO"));
    }

    @Test
    void havendoOutroProprietarioARevogacaoEhPermitida() throws Exception {
        UUID sistema = papelDeSistema();
        UUID doDono = atribuirDireto(sistema, dono);
        atribuirDireto(sistema, vendedor);

        mockMvc.perform(delete("/api/organizacao/membros/" + membership(dono)
                        + "/papeis/" + doDono).with(como(dono)))
                .andExpect(status().isNoContent());
    }

    // -----------------------------------------------------------------------
    // Rastro e superfície
    // -----------------------------------------------------------------------

    @Test
    void todaMudancaDePrivilegioDeixaRastro() throws Exception {
        long antes = eventosDePapel();

        UUID papel = criar("SDR", "SDR", "contacts.read");
        atribuir(papel, vendedor, "OWN");

        // Papel é superfície de privilégio: alteração sem rastro impede
        // reconstruir depois quem podia o quê, e quando.
        assertThat(eventosDePapel()).isGreaterThan(antes);
    }

    @Test
    void oRastroApontaOPapelPeloIdentificador() throws Exception {
        UUID papel = criar("SDR", "SDR", "contacts.read");

        // Identificador, nunca nome nem lista de permissões: a trilha precisa
        // dizer o que mudou sem virar uma segunda cópia do que mudou.
        UUID alvo = TenantContext.executarComo(tenant, () -> jdbc.queryForObject("""
                SELECT target_id FROM audit_event
                 WHERE tenant_id = ? AND action_code = 'ROLE_CHANGED_V1'
                 ORDER BY occurred_at DESC LIMIT 1
                """, UUID.class, tenant));

        assertThat(alvo).isEqualTo(papel);
        assertThat(TenantContext.executarComo(tenant, () -> jdbc.queryForObject("""
                SELECT target_type FROM audit_event
                 WHERE tenant_id = ? AND action_code = 'ROLE_CHANGED_V1'
                 ORDER BY occurred_at DESC LIMIT 1
                """, String.class, tenant))).isEqualTo("ROLE");
    }

    @Test
    void aRecusaNaoFingeTerDeixadoRastro() throws Exception {
        // Estes endpoints são transacionais, e a exceção marca a transação para
        // rollback — um INSERT de auditoria feito antes de lançar seria
        // descartado junto. Registrar a recusa aqui produziria a aparência de
        // rastro sem o rastro, que é pior que não registrar.
        UUID sistema = papelDeSistema();
        long antes = eventosDePapel();

        mockMvc.perform(delete("/api/organizacao/papeis/" + sistema).with(como(dono)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(eventosDePapel()).isEqualTo(antes);
    }

    @Test
    void codigoDuplicadoEhRecusadoComMensagem() throws Exception {
        criar("SDR", "SDR", "contacts.read");

        mockMvc.perform(post("/api/organizacao/papeis").with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"SDR","nome":"Outro","descricao":null,
                                 "permissoes":["contacts.read"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CODIGO_DE_PAPEL_EM_USO"));
    }

    @Test
    void quemNaoAdministraNaoEnxergaOsPapeis() throws Exception {
        cenario.conceder(tenant, vendedor, "OWN", "contacts.read");

        mockMvc.perform(get("/api/organizacao/papeis").with(como(vendedor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrarNoAlcanceProprioNaoServeParaRecursoColetivo() throws Exception {
        // Papel não tem responsável por registro. Aceitar alcance próprio aqui
        // transformaria uma concessão restrita em poder sobre a empresa toda.
        UUID restrito = cenario.criarUsuario(tenant, "restrito", "12345");
        cenario.conceder(tenant, restrito, "OWN", "organization.manage");

        mockMvc.perform(get("/api/organizacao/papeis").with(como(restrito)))
                .andExpect(status().isForbidden());
    }

    @Test
    void codigoForaDoFormatoEhRecusadoAntesDoBanco() throws Exception {
        mockMvc.perform(post("/api/organizacao/papeis").with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"sdr minusculo","nome":"SDR","descricao":null,
                                 "permissoes":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void alcanceDeUnidadeNaoEhOferecido() throws Exception {
        // UNIT é aceito pelo banco e não concede nada enquanto nenhuma tabela
        // de domínio declarar unidade (ADR-0008). Oferecê-lo produziria papel
        // que parece funcionar e falha em silêncio.
        UUID papel = criar("SDR", "SDR", "contacts.read");

        mockMvc.perform(post("/api/organizacao/membros/" + membership(vendedor) + "/papeis")
                        .with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"UNIT"}
                                """.formatted(papel)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void papelDeOutroTenantNaoEhAlcancavel() throws Exception {
        UUID outro = cenario.criarTenant("beta", "Beta");
        UUID intruso = cenario.criarUsuario(outro, "intruso", "12345");
        cenario.concederTudoNoTenant(outro, intruso, "organization.manage");
        UUID papel = criar("SDR", "SDR", "contacts.read");

        mockMvc.perform(put("/api/organizacao/papeis/" + papel)
                        .with(jwt().jwt(builder -> builder.subject(intruso.toString())
                                .claim("tid", outro.toString()).claim("login", "teste")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Sequestrado","descricao":null,"ativo":true}
                                """))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------

    private UUID criar(String codigo, String nome, String... permissoes) throws Exception {
        String lista = String.join("\",\"", permissoes);
        String corpo = mockMvc.perform(post("/api/organizacao/papeis").with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"%s","nome":"%s","descricao":null,
                                 "permissoes":["%s"]}
                                """.formatted(codigo, nome, lista)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(corpo.replaceAll("^.*\"id\":\"([^\"]+)\".*$", "$1"));
    }

    private UUID atribuir(UUID papel, UUID usuario, String alcance) throws Exception {
        mockMvc.perform(post("/api/organizacao/membros/" + membership(usuario) + "/papeis")
                        .with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"papelId":"%s","alcance":"%s"}
                                """.formatted(papel, alcance)))
                .andExpect(status().isNoContent());

        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject("""
                SELECT id FROM membership_scope
                 WHERE tenant_id = ? AND membership_id = ? AND role_id = ?
                   AND deleted_at IS NULL
                """, UUID.class, tenant, membership(usuario), papel));
    }

    /** Atribuição inserida direto: o papel de sistema não passa pela API. */
    private UUID atribuirDireto(UUID papel, UUID usuario) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO membership_scope
                    (id, tenant_id, membership_id, role_id, scope_type, valid_from)
                VALUES (?, ?, ?, ?, 'TENANT', now() - interval '1 day')
                """, id, tenant, membership(usuario), papel));
        return id;
    }

    private UUID papelDeSistema() {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO app_role (id, tenant_id, code, name, system_role)
                VALUES (?, ?, 'OWNER', 'Proprietário', true)
                """, id, tenant));
        return id;
    }

    private long eventosDePapel() {
        return contarAuditoria("action_code = 'ROLE_CHANGED_V1'");
    }


    private long contarAuditoria(String condicao) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE tenant_id = ? AND " + condicao,
                Long.class, tenant));
    }

    private UUID membership(UUID usuario) {
        return cenario.membershipVigente(tenant, usuario);
    }

    private RequestPostProcessor como(UUID usuario) {
        return jwt().jwt(builder -> builder.subject(usuario.toString())
                .claim("tid", tenant.toString()).claim("login", "teste"));
    }
}
