package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.AutorizacaoDeEscuta;
import br.com.pnp.crm.shared.api.TenantContext;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização por alcance e recusa de IDOR.
 *
 * <p>O teste vive contra o runtime PostgreSQL restrito, o mesmo da aplicação:
 * verificar autorização com um papel que ignora RLS mediria a regra de
 * aplicação sozinha e deixaria passar o caso em que ela é a única defesa.
 *
 * <p>Todo cenário tem dono e intruso. Uma suíte que só exercita o caminho
 * autorizado prova que o sistema funciona, não que ele protege.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class AutorizacaoPorAlcanceTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AutorizacaoDeEscuta autorizacaoDeEscuta;

    private UUID tenant;
    private UUID gerente;
    private UUID atendente;
    private UUID colega;

    @BeforeEach
    void setUp() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha Serviços");
        gerente = cenario.criarUsuario(tenant, "gerente", "12345");
        atendente = cenario.criarUsuario(tenant, "atendente", "12345");
        colega = cenario.criarUsuario(tenant, "colega", "12345");

        cenario.concederTudoNoTenant(tenant, gerente,
                "contacts.read", "contacts.write", "deals.read", "deals.write",
                "tasks.read", "tasks.write", "conversations.read", "conversations.write",
                "channels.read", "channels.write", "reports.read");
        cenario.conceder(tenant, atendente, "OWN",
                "contacts.read", "contacts.write", "deals.read", "deals.write",
                "tasks.read", "tasks.write", "conversations.read", "conversations.write",
                "channels.read", "channels.write");
        cenario.conceder(tenant, colega, "OWN",
                "contacts.read", "contacts.write", "deals.read", "deals.write",
                "tasks.read", "tasks.write");
    }

    @AfterEach
    void tearDown() {
        cenario.limpar();
    }

    // -----------------------------------------------------------------------
    // Alcance
    // -----------------------------------------------------------------------

    @Test
    void semMembershipVigenteNadaEhAutorizado() throws Exception {
        UUID estranho = cenario.criarUsuario(tenant, "estranho", "12345");

        mockMvc.perform(get("/api/contatos").with(como(estranho)))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissaoAusenteNoPapelNaoEhSupridaPelasOutras() throws Exception {
        // O colega tem contatos, negócios e tarefas, mas nenhuma permissão de canal.
        mockMvc.perform(get("/api/canais").with(como(colega)))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissaoDeLeituraNaoAutorizaCriacaoImplicitaDoFunil() throws Exception {
        UUID leitor = cenario.criarUsuario(tenant, "leitor-de-funil", "12345");
        cenario.concederTudoNoTenant(tenant, leitor, "deals.read");
        concluirOnboarding();

        mockMvc.perform(get("/api/funis").with(como(leitor)))
                .andExpect(status().isForbidden());

        Long quantidade = TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM pipeline", Long.class));
        org.assertj.core.api.Assertions.assertThat(quantidade).isZero();

        mockMvc.perform(get("/api/funis").with(como(gerente)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/funis").with(como(leitor)))
                .andExpect(status().isOk());
    }

    @Test
    void alcanceProprioNaoViraTenantEmRecursoColetivoOuTempoReal() throws Exception {
        // Canal e a inbox atual não têm filtro por responsável. Portanto uma
        // concessão OWN deve falhar, e não ser promovida a todo o tenant.
        mockMvc.perform(get("/api/canais").with(como(atendente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/conversas").with(como(atendente)))
                .andExpect(status().isForbidden());

        assertThatThrownBy(() -> autorizacaoDeEscuta
                .exigirEscutaDeConversas(tenant, atendente))
                .isInstanceOf(br.com.pnp.crm.shared.api.AcessoNegadoException.class);
        assertThatCode(() -> autorizacaoDeEscuta
                .exigirEscutaDeConversas(tenant, gerente))
                .doesNotThrowAnyException();
    }

    @Test
    void alcanceProprioNaoLeRegistroDeOutroNaListagem() throws Exception {
        criarContato(atendente, "Contato do atendente");
        criarContato(colega, "Contato do colega");

        mockMvc.perform(get("/api/contatos").with(como(atendente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Contato do atendente"));

        mockMvc.perform(get("/api/contatos").with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void alcanceTenantVeCarteirasComResponsavelIdentificado() throws Exception {
        criarContato(atendente, "Maria do atendente");
        criarContato(colega, "Maria do colega");
        criarContato(gerente, "Maria do gerente");

        mockMvc.perform(get("/api/contatos").with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].responsavelLogin")
                        .value(containsInAnyOrder("atendente", "colega", "gerente")))
                .andExpect(jsonPath("$[*].responsavelNome")
                        .value(containsInAnyOrder(
                                "Usuário atendente", "Usuário colega", "Usuário gerente")));

        criarTarefa(atendente, "Tarefa do atendente");
        criarTarefa(colega, "Tarefa do colega");
        mockMvc.perform(get("/api/tarefas").with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].responsavelLogin")
                        .value(containsInAnyOrder("atendente", "colega")));

        UUID etapa = criarEtapa();
        criarOportunidade(atendente, etapa, null);
        criarOportunidade(colega, etapa, null);
        UUID funil = TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT pipeline_id FROM pipeline_stage WHERE id = ?", UUID.class, etapa));
        mockMvc.perform(get("/api/funis/" + funil + "/oportunidades").with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].responsavelLogin")
                        .value(containsInAnyOrder("atendente", "colega")));
    }

    @Test
    void alcanceProprioNaoAcessaRegistroDeOutroPeloIdentificador() throws Exception {
        UUID doColega = criarContato(colega, "Contato do colega");

        // Leitura, edição e exclusão pelo id direto — o caminho clássico de
        // IDOR, em que o filtro da listagem é respeitado e o acesso pontual não.
        mockMvc.perform(get("/api/contatos/" + doColega).with(como(atendente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/contatos/" + doColega).with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSON","nome":"Sequestrado"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/contatos/" + doColega).with(como(atendente)).with(csrf()))
                .andExpect(status().isForbidden());

        // E o registro continua intacto.
        mockMvc.perform(get("/api/contatos/" + doColega).with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Contato do colega"));
    }

    @Test
    void alcanceProprioNaoTransfereRegistroParaForaDoProprioAlcance() throws Exception {
        UUID meu = criarContato(atendente, "Meu contato");

        // Editar transferindo a responsabilidade seria criar um registro que o
        // autor não pode mais ler: a chamada seguinte devolveria 403 sobre algo
        // que ele acabou de alterar.
        mockMvc.perform(put("/api/contatos/" + meu).with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSON","nome":"Meu contato","responsavelId":"%s"}
                                """.formatted(colega)))
                .andExpect(status().isForbidden());
    }

    @Test
    void alcanceProprioNaoCriaRegistroParaOutraPessoa() throws Exception {
        mockMvc.perform(post("/api/contatos").with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSON","nome":"De outro","responsavelId":"%s"}
                                """.formatted(colega)))
                .andExpect(status().isForbidden());
    }

    @Test
    void consolidadoDoTenantExigeAlcanceDeTenant() throws Exception {
        // O dashboard soma o tenant inteiro. Entregá-lo a alcance próprio
        // devolveria, por agregado, exatamente o que o recorte por registro nega.
        cenario.conceder(tenant, atendente, "OWN", "reports.read");

        mockMvc.perform(get("/api/relatorios/visao-geral").with(como(atendente)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/relatorios/visao-geral").with(como(gerente)))
                .andExpect(status().isOk());
    }

    @Test
    void alcanceDeTenantVenceQuandoAPessoaAcumulaOsDoisPapeis() throws Exception {
        criarContato(colega, "Contato do colega");
        // Ganhar um papel restrito não pode reduzir o que o papel amplo já dava.
        cenario.conceder(tenant, gerente, "OWN", "contacts.read");

        mockMvc.perform(get("/api/contatos").with(como(gerente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void escopoDeUnidadeNaoAnulaConcessaoPropriaDaMesmaPermissao() throws Exception {
        UUID misto = cenario.criarUsuario(tenant, "misto", "12345");
        cenario.conceder(tenant, misto, "OWN", "contacts.read", "contacts.write");
        UUID meu = criarContato(misto, "Contato próprio");
        cenario.concederNaUnidade(tenant, misto, criarUnidade("sul", "Unidade Sul"),
                "contacts.read");

        mockMvc.perform(get("/api/contatos").with(como(misto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(meu.toString()));
    }

    @Test
    void escopoDeUnidadeNaoDecideSobreRegistroDeDominio() throws Exception {
        // ADR-0008: enquanto nenhuma tabela de domínio declara unidade, UNIT
        // não vira alcance. Falha fechada, e não silenciosamente como TENANT.
        UUID regional = cenario.criarUsuario(tenant, "regional", "12345");
        cenario.concederNaUnidade(tenant, regional, criarUnidade("centro", "Unidade Centro"),
                "contacts.read");

        mockMvc.perform(get("/api/contatos").with(como(regional)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Isolamento entre tenants sob autorização válida
    // -----------------------------------------------------------------------

    @Test
    void membershipDeUmTenantNaoAutorizaNoOutro() throws Exception {
        UUID outro = cenario.criarTenant("beta", "Beta Serviços");

        // Token com o mesmo usuário e o tenant do vizinho: a autorização
        // resolve membership no tenant do token, e ali ele não existe.
        mockMvc.perform(get("/api/contatos")
                        .with(jwt().jwt(builder -> builder.subject(gerente.toString())
                                .claim("tid", outro.toString()).claim("login", "gerente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void revogarMembershipTemEfeitoNaRequisicaoSeguinte() throws Exception {
        criarContato(atendente, "Meu contato");
        mockMvc.perform(get("/api/contatos").with(como(atendente)))
                .andExpect(status().isOk());

        TenantContext.executarComo(tenant, () -> jdbc.update("""
                UPDATE organization_membership
                   SET status = 'REVOKED'
                 WHERE tenant_id = ? AND user_id = ?
                """, tenant, atendente));

        mockMvc.perform(get("/api/contatos").with(como(atendente)))
                .andExpect(status().isForbidden());
    }

    @Test
    void faltaDePermissaoEhDecididaAntesDeConsultarIdentificador() throws Exception {
        UUID contato = criarContato(atendente, "Contato existente");
        UUID semLeitura = cenario.criarUsuario(tenant, "sem-leitura", "12345");
        cenario.conceder(tenant, semLeitura, "OWN", "tasks.read");

        mockMvc.perform(get("/api/contatos/" + contato).with(como(semLeitura)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/contatos/" + UUID.randomUUID()).with(como(semLeitura)))
                .andExpect(status().isForbidden());
    }

    @Test
    void alcanceProprioNaoReferenciaContatoOuOportunidadeDeOutroResponsavel() throws Exception {
        UUID contatoDoColega = criarContato(colega, "Contato do colega");
        UUID etapa = criarEtapa();

        mockMvc.perform(post("/api/oportunidades").with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOportunidade(etapa, contatoDoColega)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tarefas").with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Contato alheio","contatoId":"%s"}
                                """.formatted(contatoDoColega)))
                .andExpect(status().isForbidden());

        UUID oportunidadeDoColega = criarOportunidade(colega, etapa, null);
        mockMvc.perform(post("/api/tarefas").with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Negócio alheio","oportunidadeId":"%s"}
                                """.formatted(oportunidadeDoColega)))
                .andExpect(status().isForbidden());
    }

    @Test
    void alcanceProprioNegaEscritaDiretaEmNegocioETarefaDeOutro() throws Exception {
        UUID etapa = criarEtapa();
        UUID oportunidadeDoColega = criarOportunidade(colega, etapa, null);
        UUID tarefaDoColega = criarTarefa(colega, "Tarefa do colega");

        mockMvc.perform(post("/api/oportunidades/" + oportunidadeDoColega + "/mover")
                        .with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapaId\":\"" + etapa + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/tarefas/" + tarefaDoColega)
                        .with(como(atendente)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Tentativa\"}"))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private UUID criarContato(UUID responsavel, String nome) throws Exception {
        // Criado pelo próprio responsável, para que o dono seja ele mesmo sem
        // depender de o endpoint aceitar responsavelId de terceiro.
        String corpo = mockMvc.perform(post("/api/contatos").with(como(responsavel)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSON","nome":"%s"}
                                """.formatted(nome)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(corpo, "$.id"));
    }

    private UUID criarEtapa() {
        UUID funil = br.com.pnp.crm.shared.api.UuidV7.gerar();
        UUID etapa = br.com.pnp.crm.shared.api.UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> {
            jdbc.update("""
                    INSERT INTO pipeline
                        (id, tenant_id, name, is_default, created_by, updated_by)
                    VALUES (?, ?, 'Funil de autorização', true, ?, ?)
                    """, funil, tenant, gerente, gerente);
            jdbc.update("""
                    INSERT INTO pipeline_stage
                        (id, tenant_id, pipeline_id, name, position, created_by, updated_by)
                    VALUES (?, ?, ?, 'Nova', 10, ?, ?)
                    """, etapa, tenant, funil, gerente, gerente);
            return null;
        });
        return etapa;
    }

    private void concluirOnboarding() {
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO tenant_profile
                    (tenant_id, business_segment, preset_version, onboarding_completed,
                     created_by, updated_by)
                VALUES (?, 'GENERAL_SERVICES', 1, true, ?, ?)
                """, tenant, gerente, gerente));
    }

    private UUID criarOportunidade(UUID usuario, UUID etapa, UUID contato) throws Exception {
        String corpo = mockMvc.perform(post("/api/oportunidades").with(como(usuario)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoOportunidade(etapa, contato)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(corpo, "$.id"));
    }

    private String corpoOportunidade(UUID etapa, UUID contato) {
        return "{\"titulo\":\"Oportunidade\",\"etapaId\":\"" + etapa
                + "\",\"valorCentavos\":100"
                + (contato == null ? "" : ",\"contatoId\":\"" + contato + "\"")
                + "}";
    }

    private UUID criarTarefa(UUID usuario, String titulo) throws Exception {
        String corpo = mockMvc.perform(post("/api/tarefas").with(como(usuario)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"" + titulo + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(corpo, "$.id"));
    }

    private UUID criarUnidade(String codigo, String nome) {
        UUID id = br.com.pnp.crm.shared.api.UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO organizational_unit (id, tenant_id, code, name)
                VALUES (?, ?, ?, ?)
                """, id, tenant, codigo, nome));
        return id;
    }

    private RequestPostProcessor como(UUID usuario) {
        return jwt().jwt(builder -> builder.subject(usuario.toString())
                .claim("tid", tenant.toString()).claim("login", "teste"));
    }
}
