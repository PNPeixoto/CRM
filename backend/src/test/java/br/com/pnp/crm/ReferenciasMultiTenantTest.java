package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class ReferenciasMultiTenantTest {

    @Autowired MockMvc mockMvc;
    @Autowired CenarioMultiTenant scenario;
    @Autowired JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;
    private UUID contactB;
    private UUID stageA;
    private UUID stageB;
    private UUID dealB;
    private UUID conversationB;

    @BeforeEach
    void setUp() {
        scenario.limpar();
        tenantA = scenario.criarTenant("alpha", "Alpha");
        tenantB = scenario.criarTenant("beta", "Beta");
        userA = scenario.criarUsuario(tenantA, "ana", "12345");
        userB = scenario.criarUsuario(tenantB, "bia", "12345");
        stageA = insertPipeline(tenantA, userA, "A");
        stageB = insertPipeline(tenantB, userB, "B");
        contactB = insertContact(tenantB, userB);
        dealB = insertDeal(tenantB, userB, contactB, stageB);
        conversationB = insertConversation(tenantB, userB);
        // Plenamente autorizada no próprio tenant: o que este teste mede é a
        // recusa de referência a dado alheio, e não a falta de permissão. Sem
        // a concessão toda chamada pararia em 403 antes de tocar a referência.
        scenario.concederTudoNoTenant(tenantA, userA,
                "contacts.read", "contacts.write", "deals.read", "deals.write",
                "tasks.write", "conversations.read");
    }

    @AfterEach
    void tearDown() {
        scenario.limpar();
    }

    @Test
    void endpointsRejectReferencesFromAnotherTenant() throws Exception {
        mockMvc.perform(post("/api/contatos").with(token()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Contato A\",\"responsavelId\":\"" + userB + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/oportunidades").with(token()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealBody(stageA, contactB, null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/oportunidades").with(token()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dealBody(stageB, null, null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tarefas").with(token()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Tarefa A\",\"oportunidadeId\":\"" + dealB + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conversas/" + conversationB + "/mensagens").with(token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsCrossTenantReferenceEvenWithoutApplicationValidation() {
        UUID taskId = UuidV7.gerar();
        assertThatThrownBy(() -> TenantContext.executarComo(tenantA, () -> jdbc.update("""
                INSERT INTO task (id, tenant_id, title, contact_id, created_by, updated_by)
                VALUES (?, ?, 'inválida', ?, ?, ?)
                """, taskId, tenantA, contactB, userA, userA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String dealBody(UUID stageId, UUID contactId, UUID ownerId) {
        return "{\"titulo\":\"Negócio A\",\"etapaId\":\"" + stageId
                + "\",\"valorCentavos\":100"
                + (contactId == null ? "" : ",\"contatoId\":\"" + contactId + "\"")
                + (ownerId == null ? "" : ",\"responsavelId\":\"" + ownerId + "\"")
                + "}";
    }

    private UUID insertContact(UUID tenantId, UUID userId) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO contact (id, tenant_id, name, owner_user_id, created_by, updated_by)
                VALUES (?, ?, 'Contato', ?, ?, ?)
                """, id, tenantId, userId, userId, userId));
        return id;
    }

    private UUID insertPipeline(UUID tenantId, UUID userId, String suffix) {
        UUID pipelineId = UuidV7.gerar();
        UUID stageId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("""
                    INSERT INTO pipeline (id, tenant_id, name, is_default, created_by, updated_by)
                    VALUES (?, ?, ?, true, ?, ?)
                    """, pipelineId, tenantId, "Funil " + suffix, userId, userId);
            jdbc.update("""
                    INSERT INTO pipeline_stage
                        (id, tenant_id, pipeline_id, name, position, created_by, updated_by)
                    VALUES (?, ?, ?, 'Nova', 10, ?, ?)
                    """, stageId, tenantId, pipelineId, userId, userId);
            return null;
        });
        return stageId;
    }

    private UUID insertDeal(UUID tenantId, UUID userId, UUID contactId, UUID stageId) {
        UUID id = UuidV7.gerar();
        UUID pipelineId = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT pipeline_id FROM pipeline_stage WHERE id = ?", UUID.class, stageId));
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO deal
                    (id, tenant_id, pipeline_id, stage_id, contact_id, title, owner_user_id,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, 'Negócio', ?, ?, ?)
                """, id, tenantId, pipelineId, stageId, contactId, userId, userId, userId));
        return id;
    }

    private UUID insertConversation(UUID tenantId, UUID userId) {
        UUID channelId = UuidV7.gerar();
        UUID conversationId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("""
                    INSERT INTO channel_connection (id, tenant_id, kind, name, created_by, updated_by)
                    VALUES (?, ?, 'LIVE_CHAT', 'Chat', ?, ?)
                    """, channelId, tenantId, userId, userId);
            jdbc.update("""
                    INSERT INTO conversation
                        (id, tenant_id, channel_connection_id, external_contact_id, status,
                         assigned_user_id, created_by, updated_by)
                    VALUES (?, ?, ?, 'external', 'OPEN', ?, ?, ?)
                    """, conversationId, tenantId, channelId, userId, userId, userId);
            return null;
        });
        return conversationId;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(builder -> builder.subject(userA.toString())
                .claim("tid", tenantA.toString()).claim("login", "ana"));
    }
}
