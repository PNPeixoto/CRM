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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class ContratosTransversaisTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private UUID tenant;
    private UUID usuario;
    private UUID conversa;

    @BeforeEach
    void setUp() {
        cenario.limpar();
        tenant = cenario.criarTenant("contratos", "Contratos");
        usuario = cenario.criarUsuario(tenant, "operador", "12345");
        cenario.concederTudoNoTenant(tenant, usuario,
                "contacts.read", "contacts.write", "conversations.read", "conversations.write",
                "channels.read", "channels.write");
        conversa = criarConversa();
    }

    @AfterEach
    void tearDown() {
        cenario.limpar();
    }

    @Test
    void replayWithTheSameKeyReturnsTheSameMessageWithoutDuplicatingTheQueue() throws Exception {
        String key = "mensagem-2026-0001";
        var first = mockMvc.perform(post("/api/conversas/{id}/mensagens", conversa)
                        .with(comoUsuario()).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Olá Maria\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstId = json.readTree(first.getResponse().getContentAsByteArray())
                .get("id").asText();

        mockMvc.perform(post("/api/conversas/{id}/mensagens", conversa)
                        .with(comoUsuario()).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Olá Maria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        Long count = TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM message WHERE conversation_id = ?",
                Long.class, conversa));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void reusingAKeyWithDifferentContentIsAConflict() throws Exception {
        String key = "mensagem-2026-0002";
        send(key, "Primeiro texto").andExpect(status().isOk());
        send(key, "Texto diferente")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHAVE_IDEMPOTENCIA_EM_CONFLITO"));
    }

    @Test
    void replayDaCriacaoDeContatoDevolveOMesmoRegistroSemDuplicar() throws Exception {
        String key = "contato-2026-0001";
        var first = criarContato(key, "  Maria  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria"))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andReturn();
        String firstId = json.readTree(first.getResponse().getContentAsByteArray())
                .get("id").asText();

        criarContato(key, "  Maria  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        Long count = TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM contact WHERE idempotency_key = ?",
                Long.class, key));
        assertThat(count).isOne();
    }

    @Test
    void chaveDeContatoReutilizadaComOutroConteudoEhConflito() throws Exception {
        String key = "contato-2026-0002";
        criarContato(key, "Maria").andExpect(status().isOk());
        criarContato(key, "Mariana")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHAVE_IDEMPOTENCIA_EM_CONFLITO"));
    }

    @Test
    void rejectsUnknownJsonInvalidSortAndUnboundedHistory() throws Exception {
        mockMvc.perform(post("/api/conversas/{id}/mensagens", conversa)
                        .with(comoUsuario()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Olá\",\"tenantId\":\"" + tenant + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));

        mockMvc.perform(get("/api/contatos?ordenarPor=senha").with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

        mockMvc.perform(get("/api/conversas/{id}/mensagens?limite=101", conversa)
                        .with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

        mockMvc.perform(get("/api/conversas/{id}/mensagens?antesDe=2026-08-03T10:00:00Z", conversa)
                        .with(comoUsuario()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readinessConfirmsThatTheRunningImageMatchesTheAppliedSchema() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void historicoSemCursorAbreAPrimeiraPaginaNoPostgreSQL() throws Exception {
        send("historico-2026-0001", "Mensagem para abrir o historico")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadaEm").isNotEmpty());

        mockMvc.perform(get("/api/conversas/{id}/mensagens", conversa)
                        .with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isArray())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].texto").value("Mensagem para abrir o historico"))
                .andExpect(jsonPath("$.temMais").value(false))
                .andExpect(jsonPath("$.sequenciaDoStream").value(1));
    }

    @Test
    void historicoUsaCursorEstavelSemDuplicarMensagens() throws Exception {
        send("cursor-2026-0001", "Primeira").andExpect(status().isOk());
        send("cursor-2026-0002", "Segunda").andExpect(status().isOk());
        send("cursor-2026-0003", "Terceira").andExpect(status().isOk());

        var primeira = mockMvc.perform(get("/api/conversas/{id}/mensagens", conversa)
                        .param("limite", "2")
                        .with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.temMais").value(true))
                .andReturn();
        var pagina = json.readTree(primeira.getResponse().getContentAsByteArray());
        String primeiroId = pagina.get("itens").get(0).get("id").asText();
        String segundoId = pagina.get("itens").get(1).get("id").asText();

        mockMvc.perform(get("/api/conversas/{id}/mensagens", conversa)
                        .param("limite", "2")
                        .param("antesDe", pagina.get("proximoAntesDe").asText())
                        .param("antesDoId", pagina.get("proximoAntesDoId").asText())
                        .with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.is(primeiroId),
                                org.hamcrest.Matchers.is(segundoId)))))
                .andExpect(jsonPath("$.temMais").value(false));
    }

    @Test
    void streamRecuperaSequenciasEInformaQuandoOHistoricoExpirou() throws Exception {
        send("stream-2026-0001", "Primeira").andExpect(status().isOk());
        send("stream-2026-0002", "Segunda").andExpect(status().isOk());
        send("stream-2026-0003", "Terceira").andExpect(status().isOk());

        var primeira = mockMvc.perform(get("/api/conversas/eventos")
                        .param("apos", "0").param("limite", "2")
                        .with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(2))
                .andExpect(jsonPath("$.eventos[0].sequence").value(1))
                .andExpect(jsonPath("$.eventos[1].sequence").value(2))
                .andExpect(jsonPath("$.ultimaSequencia").value(2))
                .andExpect(jsonPath("$.temMais").value(true))
                .andReturn();
        long cursor = json.readTree(primeira.getResponse().getContentAsByteArray())
                .get("ultimaSequencia").asLong();

        mockMvc.perform(get("/api/conversas/eventos")
                        .param("apos", Long.toString(cursor)).param("limite", "2")
                        .with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(1))
                .andExpect(jsonPath("$.eventos[0].sequence").value(3))
                .andExpect(jsonPath("$.temMais").value(false));

        // Simula uma janela de retenção na qual somente a sequência 3 ainda
        // está disponível. O stream continua em 3, então o cliente em 1 deve
        // abandonar o cursor antigo e refazer o snapshot REST.
        cenario.limpar();
        tenant = cenario.criarTenant("stream-retido", "Stream retido");
        usuario = cenario.criarUsuario(tenant, "operador", "12345");
        cenario.concederTudoNoTenant(tenant, usuario,
                "conversations.read", "conversations.write");
        conversa = criarConversa();
        TenantContext.executarComo(tenant, () -> {
            jdbc.update("""
                    INSERT INTO realtime_stream_cursor (tenant_id, last_sequence)
                    VALUES (?, 3)
                    """, tenant);
            jdbc.update("""
                    INSERT INTO realtime_event
                        (id, tenant_id, sequence, event_type, conversation_id,
                         resource_version, occurred_at)
                    VALUES (?, ?, 3, 'SLA_CHANGED', ?, 0, now())
                    """, UuidV7.gerar(), tenant, conversa);
            return null;
        });
        mockMvc.perform(get("/api/conversas/eventos")
                        .param("apos", "1").param("limite", "20")
                        .with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos.length()").value(0))
                .andExpect(jsonPath("$.resetObrigatorio").value(true))
                .andExpect(jsonPath("$.ultimaSequencia").value(3));
    }

    @Test
    void criaSegredoDoWebhookNoServidorSemExpoLoNaApi() throws Exception {
        String token = "123456:token-somente-do-teste";
        var resultado = mockMvc.perform(post("/api/canais")
                        .with(comoUsuario()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"TELEGRAM","nome":"Bot seguro","token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temToken").value(true))
                .andExpect(jsonPath("$.temSegredoWebhook").value(true))
                .andExpect(jsonPath("$.segredoWebhook").doesNotExist())
                .andExpect(jsonPath("$.estadoRemoto").doesNotExist())
                .andReturn();

        UUID canalId = UUID.fromString(json.readTree(resultado.getResponse().getContentAsByteArray())
                .get("id").asText());
        TenantContext.executarComo(tenant, () -> {
            Integer quantidade = jdbc.queryForObject("""
                    SELECT count(*) FROM channel_credential
                     WHERE channel_connection_id = ? AND deleted_at IS NULL
                    """, Integer.class, canalId);
            String materialCifrado = jdbc.queryForObject("""
                    SELECT ciphertext FROM channel_credential
                     WHERE channel_connection_id = ?
                       AND kind = 'TELEGRAM_BOT_TOKEN' AND deleted_at IS NULL
                    """, String.class, canalId);
            assertThat(quantidade).isEqualTo(2);
            assertThat(materialCifrado).doesNotContain(token);
            return null;
        });
    }

    private org.springframework.test.web.servlet.ResultActions send(String key, String text)
            throws Exception {
        return mockMvc.perform(post("/api/conversas/{id}/mensagens", conversa)
                .with(comoUsuario()).with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"" + text + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions criarContato(String key, String nome)
            throws Exception {
        return mockMvc.perform(post("/api/contatos")
                .with(comoUsuario()).with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PERSON\",\"nome\":\"" + nome + "\"}"));
    }

    private UUID criarConversa() {
        UUID canal = UuidV7.gerar();
        UUID conversaId = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> {
            jdbc.update("""
                    INSERT INTO channel_connection (id, tenant_id, kind, name, created_by, updated_by)
                    VALUES (?, ?, 'LIVE_CHAT', 'Chat', ?, ?)
                    """, canal, tenant, usuario, usuario);
            jdbc.update("""
                    INSERT INTO conversation
                        (id, tenant_id, channel_connection_id, external_contact_id, status,
                         assigned_user_id, created_by, updated_by)
                    VALUES (?, ?, ?, 'maria', 'OPEN', ?, ?, ?)
                    """, conversaId, tenant, canal, usuario, usuario, usuario);
            return null;
        });
        return conversaId;
    }

    private RequestPostProcessor comoUsuario() {
        return jwt().jwt(builder -> builder.subject(usuario.toString())
                .claim("tid", tenant.toString()).claim("login", "operador"));
    }
}
