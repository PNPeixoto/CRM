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
                "contacts.read", "conversations.read", "conversations.write");
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

    private org.springframework.test.web.servlet.ResultActions send(String key, String text)
            throws Exception {
        return mockMvc.perform(post("/api/conversas/{id}/mensagens", conversa)
                .with(comoUsuario()).with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"" + text + "\"}"));
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
