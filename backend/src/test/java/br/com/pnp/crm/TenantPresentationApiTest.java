package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class TenantPresentationApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired CenarioMultiTenant scenario;
    @Autowired JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID userA;

    @BeforeEach
    void setUp() {
        scenario.limpar();
        tenantA = scenario.criarTenant("alpha", "Alpha");
        userA = scenario.criarUsuario(tenantA, "ana", "12345");
        scenario.concederTudoNoTenant(tenantA, userA, "organization.manage");
    }

    @AfterEach
    void tearDown() {
        scenario.limpar();
    }

    @Test
    void completesOnboardingAndPersistsPresentationAcrossReloads() throws Exception {
        mockMvc.perform(get("/api/empresa/apresentacao").with(token(tenantA, userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingConcluido").value(false));

        mockMvc.perform(put("/api/empresa/perfil-inicial")
                        .with(token(tenantA, userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"CONFECTIONERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingConcluido").value(true))
                .andExpect(jsonPath("$.segmento").value("CONFECTIONERY"))
                .andExpect(jsonPath("$.funilPadrao.nome").value("Encomendas"));

        mockMvc.perform(get("/api/empresa/apresentacao").with(token(tenantA, userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmento").value("CONFECTIONERY"));

        Long funis = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM pipeline WHERE is_default AND deleted_at IS NULL",
                Long.class));
        Long etapas = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM pipeline_stage WHERE deleted_at IS NULL", Long.class));
        assertThat(funis).isOne();
        assertThat(etapas).isEqualTo(8);
    }

    @Test
    void conclusoesConcorrentesConvergemSemDuplicarFunil() throws Exception {
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var concluir = (java.util.concurrent.Callable<Integer>) () -> {
                prontas.countDown();
                iniciar.await();
                return mockMvc.perform(put("/api/empresa/perfil-inicial")
                                .with(token(tenantA, userA)).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"segmento\":\"GENERAL_SERVICES\"}"))
                        .andReturn().getResponse().getStatus();
            };
            var primeira = executor.submit(concluir);
            var segunda = executor.submit(concluir);
            prontas.await();
            iniciar.countDown();
            assertThat(List.of(primeira.get(), segunda.get())).containsOnly(200);
        }

        Long perfis = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForObject("SELECT count(*) FROM tenant_profile", Long.class));
        Long funis = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForObject("SELECT count(*) FROM pipeline WHERE is_default", Long.class));
        Long etapas = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForObject("SELECT count(*) FROM pipeline_stage", Long.class));
        assertThat(perfis).isOne();
        assertThat(funis).isOne();
        assertThat(etapas).isEqualTo(6);
    }

    @Test
    void segmentoPodeMudarSemReaplicarOuSobrescreverFunilPersonalizado() throws Exception {
        mockMvc.perform(put("/api/empresa/perfil-inicial")
                        .with(token(tenantA, userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"GENERAL_SERVICES\"}"))
                .andExpect(status().isOk());
        TenantContext.executarComo(tenantA, () -> jdbc.update(
                "UPDATE pipeline SET name = 'Funil personalizado' WHERE is_default"));

        mockMvc.perform(put("/api/empresa/apresentacao")
                        .with(token(tenantA, userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"RESTAURANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmento").value("RESTAURANT"))
                .andExpect(jsonPath("$.versaoPreset").value(1))
                .andExpect(jsonPath("$.funilPadrao.nome").value("Pedidos"));

        Map<String, Object> profile = TenantContext.executarComo(tenantA, () ->
                jdbc.queryForMap("SELECT business_segment, preset_version FROM tenant_profile"));
        String nomeDoFunil = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT name FROM pipeline WHERE is_default", String.class));
        Long etapas = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForObject("SELECT count(*) FROM pipeline_stage", Long.class));
        assertThat(profile.get("business_segment")).isEqualTo("RESTAURANT");
        assertThat(profile.get("preset_version")).isEqualTo(1);
        assertThat(nomeDoFunil).isEqualTo("Funil personalizado");
        assertThat(etapas).isEqualTo(6);
    }

    @Test
    void apresentacaoNaoPodeSerAlteradaAntesDoPerfilInicial() throws Exception {
        mockMvc.perform(put("/api/empresa/apresentacao")
                        .with(token(tenantA, userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"RENTAL\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PERFIL_INICIAL_PENDENTE"));
    }

    @Test
    void rejectsTenantProvidedByBodyQueryOrHeader() throws Exception {
        mockMvc.perform(put("/api/empresa/perfil-inicial")
                        .with(token(tenantA, userA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"RENTAL\",\"tenantId\":\"" + tenantA + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/empresa/apresentacao?tenantId=" + tenantA)
                        .with(token(tenantA, userA)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/empresa/apresentacao")
                        .header("X-Tenant-Id", tenantA).with(token(tenantA, userA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membershipVigenteEhObrigatorioParaLerOuAlterarApresentacao() throws Exception {
        UUID semMembership = scenario.criarUsuario(tenantA, "sem-membership", "12345");

        mockMvc.perform(get("/api/empresa/apresentacao")
                        .with(token(tenantA, semMembership)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/empresa/perfil-inicial")
                        .with(token(tenantA, semMembership)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"RENTAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leituraDaApresentacaoNaoConcedeAlteracaoDoOnboarding() throws Exception {
        UUID leitor = scenario.criarUsuario(tenantA, "leitor", "12345");
        // Um membership sem permissão de mutação basta para metadado de
        // bootstrap, mas não para concluir o onboarding.
        scenario.conceder(tenantA, leitor, "TENANT");

        mockMvc.perform(get("/api/empresa/apresentacao").with(token(tenantA, leitor)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/empresa/perfil-inicial")
                        .with(token(tenantA, leitor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"RENTAL\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/empresa/apresentacao")
                        .with(token(tenantA, leitor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmento\":\"RENTAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantCannotReadOrUpdateAnotherProfile() {
        UUID tenantB = scenario.criarTenant("beta", "Beta");
        UUID userB = scenario.criarUsuario(tenantB, "bia", "12345");
        insertProfile(tenantA, userA, "GENERAL_SERVICES");
        insertProfile(tenantB, userB, "RENTAL");

        var rowsSeenByA = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForList("SELECT tenant_id, business_segment FROM tenant_profile"));
        int updatedByA = TenantContext.executarComo(tenantA,
                () -> jdbc.update("UPDATE tenant_profile SET business_segment = 'RESTAURANT'"));
        String segmentB = TenantContext.executarComo(tenantB, () -> jdbc.queryForObject(
                "SELECT business_segment FROM tenant_profile", String.class));

        assertThat(rowsSeenByA).hasSize(1);
        assertThat(rowsSeenByA.getFirst().get("tenant_id")).isEqualTo(tenantA);
        assertThat(updatedByA).isOne();
        assertThat(segmentB).isEqualTo("RENTAL");
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
}
