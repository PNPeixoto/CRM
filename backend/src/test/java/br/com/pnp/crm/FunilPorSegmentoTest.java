package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class FunilPorSegmentoTest {

    @Autowired MockMvc mockMvc;
    @Autowired CenarioMultiTenant scenario;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        scenario.limpar();
    }

    @AfterEach
    void tearDown() {
        scenario.limpar();
    }

    @Test
    void everySegmentCreatesItsExpectedPipeline() throws Exception {
        Map<String, ExpectedPipeline> expected = Map.of(
                "GENERAL_SERVICES", new ExpectedPipeline("Funil de vendas",
                        List.of("Novo lead", "Contato feito", "Proposta enviada", "Negociação", "Ganho", "Perdido")),
                "CONFECTIONERY", new ExpectedPipeline("Encomendas",
                        List.of("Novo orçamento", "Orçamento enviado", "Aguardando sinal", "Confirmado",
                                "Produção", "Pronto", "Entregue", "Perdido")),
                "RESTAURANT", new ExpectedPipeline("Pedidos",
                        List.of("Recebido", "Confirmado", "Em preparo", "Pronto", "Finalizado", "Cancelado")),
                "RENTAL", new ExpectedPipeline("Locações",
                        List.of("Novo contato", "Orçamento", "Aguardando confirmação", "Reservado",
                                "Entregue", "Concluído", "Perdido")));

        int index = 0;
        for (var entry : expected.entrySet()) {
            UUID tenantId = scenario.criarTenant("tenant-" + index, "Tenant " + index);
            UUID userId = scenario.criarUsuario(tenantId, "user" + index, "12345");
            insertProfile(tenantId, userId, entry.getKey());
            scenario.concederTudoNoTenant(tenantId, userId, "deals.read", "deals.write");

            mockMvc.perform(get("/api/funis").with(token(tenantId, userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nome").value(entry.getValue().name()))
                    .andExpect(jsonPath("$[0].etapas[*].nome",
                            contains(entry.getValue().stages().toArray())));
            index++;
        }
    }

    @Test
    void concurrentRequestsCreateOnlyOneDefaultPipeline() throws Exception {
        UUID tenantId = scenario.criarTenant("alpha", "Alpha");
        UUID userId = scenario.criarUsuario(tenantId, "ana", "12345");
        insertProfile(tenantId, userId, "GENERAL_SERVICES");
        scenario.concederTudoNoTenant(tenantId, userId, "deals.read", "deals.write");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var request = (java.util.concurrent.Callable<Integer>) () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(get("/api/funis").with(token(tenantId, userId)))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            ready.await();
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsOnly(200);
        }

        Long pipelines = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM pipeline WHERE is_default", Long.class));
        Long stages = TenantContext.executarComo(tenantId,
                () -> jdbc.queryForObject("SELECT count(*) FROM pipeline_stage", Long.class));
        assertThat(pipelines).isOne();
        assertThat(stages).isEqualTo(6);
    }

    @Test
    void directApiCallCannotCreateGeneralPipelineBeforeSegmentChoice() throws Exception {
        UUID tenantId = scenario.criarTenant("pending", "Pending");
        UUID userId = scenario.criarUsuario(tenantId, "user", "12345");
        // Autorizado de propósito: o que se mede aqui é a recusa por segmento
        // ainda não escolhido, não a recusa por permissão.
        scenario.concederTudoNoTenant(tenantId, userId, "deals.read", "deals.write");

        mockMvc.perform(get("/api/funis").with(token(tenantId, userId)))
                .andExpect(status().isUnprocessableEntity());

        Long pipelines = TenantContext.executarComo(tenantId,
                () -> jdbc.queryForObject("SELECT count(*) FROM pipeline", Long.class));
        assertThat(pipelines).isZero();
    }

    private void insertProfile(UUID tenantId, UUID userId, String segment) {
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO tenant_profile
                    (tenant_id, business_segment, preset_version, onboarding_completed,
                     created_by, updated_by)
                VALUES (?, ?, 1, true, ?, ?)
                """, tenantId, segment, userId, userId));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(
            UUID tenantId, UUID userId) {
        return jwt().jwt(builder -> builder.subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("login", "test"));
    }

    private record ExpectedPipeline(String name, List<String> stages) {
    }
}
