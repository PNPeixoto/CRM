package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Direitos do titular: acesso e eliminacao.
 *
 * <p>Cobre o que o aceite do Prompt 18 exige e o que o pedido acrescentou:
 * a exportacao precisa dizer <b>de onde veio</b> cada dado, porque uma lista
 * sem origem obriga o titular a confiar em vez de conferir.
 */
@TesteDeIntegracao
@AutoConfigureMockMvc
class DireitosDoTitularTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private UUID tenant;
    private UUID encarregado;
    private UUID atendente;
    private UUID contato;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha");
        encarregado = cenario.criarUsuario(tenant, "encarregado", "12345");
        atendente = cenario.criarUsuario(tenant, "atendente", "12345");
        cenario.concederTudoNoTenant(tenant, encarregado, "privacy.manage");
        cenario.concederTudoNoTenant(tenant, atendente, "contacts.read", "contacts.write");
        contato = criarContato();
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void exportacaoInformaAOrigemEAFinalidadeDeCadaSecao() throws Exception {
        mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/exportacao")
                        .with(como(encarregado)).with(csrf()))
                .andExpect(status().isOk())
                // Um documento com o dado pessoal inteiro de alguem nao pode
                // ficar em cache de navegador nem de proxy.
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.contatoId").value(contato.toString()))
                .andExpect(jsonPath("$.solicitadaPor").value(encarregado.toString()))
                // A procedencia e o ponto: sem ela o titular nao consegue
                // conferir de onde o dado saiu nem por que a empresa o tem.
                .andExpect(jsonPath("$.secoes[0].origem").value("contact"))
                .andExpect(jsonPath("$.secoes[0].finalidade").isNotEmpty())
                .andExpect(jsonPath("$.secoes[0].retencao").isNotEmpty())
                .andExpect(jsonPath("$.secoes[0].registros[0].nome").value("Fulano de Teste"))
                .andExpect(jsonPath("$.secoes[1].origem").value("conversation"))
                .andExpect(jsonPath("$.secoes[2].origem").value("message"));
    }

    @Test
    void exportacaoDeclaraOQueAindaNaoTemPrazo() throws Exception {
        // Dizer "prazo ainda nao definido" e menos elegante e mais honesto que
        // escrever "indefinido", que sugere decisao onde ha ausencia dela.
        mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/exportacao")
                        .with(como(encarregado)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secoes[0].retencao")
                        .value(org.hamcrest.Matchers.containsString("não definido")))
                .andExpect(jsonPath("$.observacoes").isNotEmpty());
    }

    @Test
    void exportacaoExigePermissaoPropriaDePrivacidade() throws Exception {
        // Ler contato nao autoriza exportar o titular inteiro: sao coisas
        // diferentes, e o catalogo as separa de proposito.
        mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/exportacao")
                        .with(como(atendente)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonimizacaoRemoveODadoPessoalEPreservaOHistorico() throws Exception {
        mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/anonimizacao")
                        .with(como(encarregado)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(campo("name")).isEqualTo("Titular anonimizado");
        assertThat(campo("email")).isNull();
        assertThat(campo("phone")).isNull();
        // A linha permanece: oportunidade e tarefa a referenciam.
        assertThat(existe()).isTrue();
    }

    @Test
    void repetirAAnonimizacaoNaoEhErro() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/anonimizacao")
                            .with(como(encarregado)).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        // O titular pediu que o dado sumisse, e ele sumiu. Repetir o pedido
        // encontrar o resultado ja alcancado nao e falha.
        assertThat(campo("name")).isEqualTo("Titular anonimizado");
    }

    @Test
    void retencaoLegalRecusaComMotivoEmVezDeFalharEmSilencio() throws Exception {
        declararHold(contato);

        mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/anonimizacao")
                        .with(como(encarregado)).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ELIMINACAO_RECUSADA"))
                // A mensagem precisa dizer o que fazer, e nao apenas que nao deu.
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("retenção legal")));

        // O dado continua intacto: recusar e preservar, nao apagar pela metade.
        assertThat(campo("name")).isEqualTo("Fulano de Teste");
    }

    @Test
    void pedidoSobreContatoInexistenteNaoRevelaSeEleExisteEmOutroTenant() throws Exception {
        UUID vizinho = cenario.criarTenant("beta", "Beta");
        UUID usuarioVizinho = cenario.criarUsuario(vizinho, "bia", "12345");
        UUID doVizinho = UuidV7.gerar();
        TenantContext.executarComo(vizinho, () -> {
            jdbc.update("""
                    INSERT INTO contact (id, tenant_id, name, owner_user_id)
                    VALUES (?, ?, 'Alheio', ?)
                    """, doVizinho, vizinho, usuarioVizinho);
            return null;
        });

        mockMvc.perform(post("/api/privacidade/titulares/" + doVizinho + "/exportacao")
                        .with(como(encarregado)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void pedidoDeTitularFicaRegistradoNaTrilha() throws Exception {
        mockMvc.perform(post("/api/privacidade/titulares/" + contato + "/exportacao")
                        .with(como(encarregado)).with(csrf()))
                .andExpect(status().isOk());

        Long registros = TenantContext.executarComo(tenant, () -> jdbc.queryForObject("""
                SELECT count(*) FROM audit_event
                 WHERE tenant_id = ? AND target_id = ?
                   AND action_code IN ('EXPORT_REQUESTED_V1', 'EXPORT_COMPLETED_V1')
                """, Long.class, tenant, contato));

        // Pedido e conclusao, separados: se a geracao falhar no meio, o pedido
        // continua tendo existido.
        assertThat(registros).isEqualTo(2L);
    }

    // -----------------------------------------------------------------------

    private UUID criarContato() {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> {
            jdbc.update("""
                    INSERT INTO contact
                        (id, tenant_id, name, email, phone, company_name, notes, owner_user_id)
                    VALUES (?, ?, 'Fulano de Teste', 'fulano@exemplo.test', '+550000000000',
                            'Empresa Teste', 'anotacao interna', ?)
                    """, id, tenant, atendente);
            return null;
        });
        return id;
    }

    private void declararHold(UUID alvo) {
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO legal_hold
                    (id, tenant_id, target_type, target_id, reason, declared_by, valid_from)
                VALUES (?, ?, 'CONTACT', ?, 'processo judicial em curso', ?, ?)
                """, UuidV7.gerar(), tenant, alvo, encarregado,
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))));
    }

    private String campo(String coluna) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT " + coluna + " FROM contact WHERE id = ?", String.class, contato));
    }

    private boolean existe() {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM contact WHERE id = ?", Long.class, contato)) == 1L;
    }

    private RequestPostProcessor como(UUID usuario) {
        return jwt().jwt(builder -> builder.subject(usuario.toString())
                .claim("tid", tenant.toString()).claim("login", "teste"));
    }
}
