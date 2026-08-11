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
 * O alcance intermediário: o gestor enxerga o que a equipe dele faz.
 *
 * <p><b>Por que este teste existe.</b> Até a V25 havia só "vê tudo" e "vê o
 * meu". O gestor com alcance de tenant enxergava também o comercial de outra
 * equipe; com alcance próprio, não enxergava o próprio time. Nenhum dos dois é
 * a hierarquia que uma operação comercial tem.
 *
 * <p>Todo cenário tem um terceiro fora da equipe. Um teste que só verifica que
 * o gestor <b>vê</b> a equipe prova metade: a outra metade é ele <b>não</b> ver
 * quem não é dele.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class AlcanceDeEquipeTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private UUID tenant;
    private UUID dono;
    private UUID gestor;
    private UUID liderado;
    private UUID estranho;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha");
        dono = cenario.criarUsuario(tenant, "dono", "12345");
        gestor = cenario.criarUsuario(tenant, "gestor", "12345");
        liderado = cenario.criarUsuario(tenant, "liderado", "12345");
        estranho = cenario.criarUsuario(tenant, "estranho", "12345");

        cenario.concederTudoNoTenant(tenant, dono,
                "organization.manage", "contacts.read", "contacts.write",
                "deals.read", "deals.write", "tasks.read", "tasks.write");
        cenario.conceder(tenant, gestor, "TEAM",
                "contacts.read", "contacts.write", "deals.read", "tasks.read");
        cenario.conceder(tenant, liderado, "OWN",
                "contacts.read", "contacts.write");
        cenario.conceder(tenant, estranho, "OWN",
                "contacts.read", "contacts.write");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    // -----------------------------------------------------------------------
    // O recorte
    // -----------------------------------------------------------------------

    @Test
    void oGestorEnxergaOContatoDoLiderado() throws Exception {
        montarEquipe(gestor, liderado);
        criarContato(liderado, "Cliente do liderado");

        mockMvc.perform(get("/api/contatos").with(como(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Cliente do liderado"));
    }

    @Test
    void oGestorNaoEnxergaQuemNaoEhDaEquipe() throws Exception {
        montarEquipe(gestor, liderado);
        criarContato(liderado, "Cliente do liderado");
        criarContato(estranho, "Cliente de fora");

        mockMvc.perform(get("/api/contatos").with(como(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Cliente do liderado"));
    }

    @Test
    void oGestorContinuaEnxergandoAPropriaCarteira() throws Exception {
        // Um recorte de equipe que escondesse a carteira do próprio gestor
        // seria absurdo, e a primeira listagem denunciaria.
        montarEquipe(gestor, liderado);
        criarContato(gestor, "Cliente do gestor");
        criarContato(liderado, "Cliente do liderado");

        mockMvc.perform(get("/api/contatos").with(como(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void semEquipeOAlcanceDeEquipeEquivaleAoProprio() throws Exception {
        // Falha fechada: papel concedido em TEAM antes de a equipe existir não
        // pode enxergar o tenant inteiro enquanto ninguém foi vinculado.
        criarContato(gestor, "Cliente do gestor");
        criarContato(estranho, "Cliente de fora");

        mockMvc.perform(get("/api/contatos").with(como(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Cliente do gestor"));
    }

    @Test
    void oLideradoNaoEnxergaOGestor() throws Exception {
        // A relação é dirigida: responder a alguém não dá acesso ao que essa
        // pessoa faz.
        montarEquipe(gestor, liderado);
        criarContato(gestor, "Cliente do gestor");
        criarContato(liderado, "Cliente do liderado");

        mockMvc.perform(get("/api/contatos").with(como(liderado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Cliente do liderado"));
    }

    @Test
    void oMesmoRecorteValeParaTarefa() throws Exception {
        // O recorte é resolvido num lugar só, e cada listagem o aplica na
        // própria consulta. Se contato e tarefa divergissem, a hierarquia
        // valeria numa tela e não na outra — que é pior que não existir.
        montarEquipe(gestor, liderado);
        criarTarefa(liderado, "Ligar para o cliente");
        criarTarefa(estranho, "Tarefa de fora");

        mockMvc.perform(get("/api/tarefas").with(como(gestor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Ligar para o cliente"));
    }

    // -----------------------------------------------------------------------
    // Ação sobre registro, não só listagem
    // -----------------------------------------------------------------------

    @Test
    void oGestorEditaOContatoDoLiderado() throws Exception {
        montarEquipe(gestor, liderado);
        UUID contato = criarContato(liderado, "Cliente do liderado");

        mockMvc.perform(put("/api/contatos/" + contato).with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSON","nome":"Cliente renomeado",
                                 "responsavelId":"%s"}
                                """.formatted(liderado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cliente renomeado"));
    }

    @Test
    void oGestorNaoAgeSobreRegistroDeFora() throws Exception {
        montarEquipe(gestor, liderado);
        UUID deFora = criarContato(estranho, "Cliente de fora");

        // Listagem e ação usam a mesma fonte de verdade. Se divergissem, o
        // registro sumiria da tela e continuaria alcançável por id — que é o
        // IDOR clássico em CRM.
        mockMvc.perform(get("/api/contatos/" + deFora).with(como(gestor)))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // Gestão da composição
    // -----------------------------------------------------------------------

    @Test
    void aComposicaoApareceNaListagem() throws Exception {
        montarEquipe(gestor, liderado);

        mockMvc.perform(get("/api/organizacao/equipes").with(como(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gestorId").value(gestor.toString()))
                .andExpect(jsonPath("$[0].liderados[0].login").value("liderado"));
    }

    @Test
    void removerDaEquipeTiraOAcessoNaRequisicaoSeguinte() throws Exception {
        montarEquipe(gestor, liderado);
        criarContato(liderado, "Cliente do liderado");

        mockMvc.perform(get("/api/contatos").with(como(gestor)))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/organizacao/equipes/" + gestor + "/membros/" + liderado)
                        .with(como(dono)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contatos").with(como(gestor)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removerEncerraSemApagarOHistorico() throws Exception {
        montarEquipe(gestor, liderado);

        mockMvc.perform(delete("/api/organizacao/equipes/" + gestor + "/membros/" + liderado)
                        .with(como(dono)))
                .andExpect(status().isNoContent());

        // O período em que alguém respondeu a um gestor explica acessos já
        // registrados na trilha naquele intervalo.
        Long linhas = TenantContext.executarComo(tenant, () -> jdbc.queryForObject("""
                SELECT count(*) FROM team_member
                 WHERE tenant_id = ? AND valid_until IS NOT NULL
                """, Long.class, tenant));
        assertThat(linhas).isEqualTo(1L);
    }

    @Test
    void ninguemRespondeASiMesmo() throws Exception {
        mockMvc.perform(post("/api/organizacao/equipes/" + gestor + "/membros").with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId":"%s"}
                                """.formatted(gestor)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("COMPOSICAO_DE_EQUIPE_INVALIDA"));
    }

    @Test
    void oCicloDeDoisEhRecusado() throws Exception {
        montarEquipe(gestor, liderado);

        mockMvc.perform(post("/api/organizacao/equipes/" + liderado + "/membros").with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId":"%s"}
                                """.formatted(gestor)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("COMPOSICAO_DE_EQUIPE_INVALIDA"));
    }

    @Test
    void incluirDuasVezesEhIdempotente() throws Exception {
        montarEquipe(gestor, liderado);
        montarEquipe(gestor, liderado);

        mockMvc.perform(get("/api/organizacao/equipes").with(como(dono)))
                .andExpect(jsonPath("$[0].liderados.length()").value(1));
    }

    @Test
    void quemNaoAdministraNaoMontaEquipe() throws Exception {
        mockMvc.perform(post("/api/organizacao/equipes/" + gestor + "/membros").with(como(gestor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId":"%s"}
                                """.formatted(liderado)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMudancaDeEquipeDeixaRastro() throws Exception {
        long antes = eventosDeEquipe();
        montarEquipe(gestor, liderado);

        // Incluir alguém sob um gestor amplia o que aquele gestor lê, sem que
        // nenhum papel tenha mudado. É superfície de autorização.
        assertThat(eventosDeEquipe()).isGreaterThan(antes);
    }

    // -----------------------------------------------------------------------

    private void montarEquipe(UUID gestorId, UUID membroId) throws Exception {
        mockMvc.perform(post("/api/organizacao/equipes/" + gestorId + "/membros")
                        .with(como(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuarioId":"%s"}
                                """.formatted(membroId)))
                .andExpect(status().isNoContent());
    }

    private UUID criarContato(UUID responsavel, String nome) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO contact (id, tenant_id, name, owner_user_id, contact_kind)
                VALUES (?, ?, ?, ?, 'PERSON')
                """, id, tenant, nome, responsavel));
        return id;
    }

    private void criarTarefa(UUID responsavel, String titulo) {
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO task (id, tenant_id, title, assigned_user_id)
                VALUES (?, ?, ?, ?)
                """, UuidV7.gerar(), tenant, titulo, responsavel));
    }

    private long eventosDeEquipe() {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject("""
                SELECT count(*) FROM audit_event
                 WHERE tenant_id = ? AND reason_code = 'TEAM_MEMBERSHIP_CHANGED'
                """, Long.class, tenant));
    }

    private RequestPostProcessor como(UUID usuario) {
        return jwt().jwt(builder -> builder.subject(usuario.toString())
                .claim("tid", tenant.toString()).claim("login", "teste"));
    }
}
