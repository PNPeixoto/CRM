package br.com.pnp.crm;

import br.com.pnp.crm.report.internal.ReportExportService;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class RelatoriosExportacoesTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ReportExportService exportacoes;

    private UUID tenant;
    private UUID usuario;
    private UUID outroTenant;
    private UUID outroUsuario;

    @BeforeEach
    void setUp() {
        cenario.limpar();
        tenant = cenario.criarTenant("relatorios", "Relatorios");
        usuario = cenario.criarUsuario(tenant, "gestor-relatorios", "12345");
        cenario.concederTudoNoTenant(tenant, usuario, "reports.read");
        outroTenant = cenario.criarTenant("outro-relatorios", "Outro Relatorios");
        outroUsuario = cenario.criarUsuario(outroTenant, "outro-gestor", "12345");
        cenario.concederTudoNoTenant(outroTenant, outroUsuario, "reports.read");
    }

    @AfterEach
    void tearDown() {
        cenario.limpar();
    }

    @Test
    void catalogoEExportacaoReconciliamComSnapshotEReplayNaoDuplicaArquivo() throws Exception {
        mockMvc.perform(get("/api/relatorios/metricas").with(como(tenant, usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.code == 'deals.open.value_minor')].currencyCode")
                        .value("BRL"))
                .andExpect(jsonPath("$[?(@.code == 'messages.inbound.today.count')].timeZone")
                        .value("America/Sao_Paulo"));

        UUID id = solicitar("relatorio-seguro-0001", tenant, usuario);
        UUID replay = solicitar("relatorio-seguro-0001", tenant, usuario);
        assertThat(replay).isEqualTo(id);
        assertThat(contarJobs(tenant)).isOne();

        reservar(id, tenant);
        TenantContext.executarComo(tenant, () -> {
            exportacoes.processar(id);
            return null;
        });

        var consulta = mockMvc.perform(get("/api/relatorios/exportacoes/{id}", id)
                        .with(como(tenant, usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.metricCatalogVersion").value("OVERVIEW_V1"))
                .andExpect(jsonPath("$.byteSize").isNumber())
                .andReturn();
        assertThat(json.readTree(consulta.getResponse().getContentAsByteArray())
                .get("expiresAt").asText()).isNotBlank();

        String storageKey = TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT storage_key FROM report_export_job WHERE id = ?", String.class, id));
        byte[] cifrado = Files.readAllBytes(Path.of(System.getProperty("java.io.tmpdir"),
                "crm-pnp-report-exports-test", storageKey));
        assertThat(new String(cifrado, StandardCharsets.UTF_8))
                .doesNotContain("metric_code")
                .doesNotContain("contacts.total.count");

        var urlResponse = mockMvc.perform(post("/api/relatorios/exportacoes/{id}/url", id)
                        .with(como(tenant, usuario)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isNotEmpty())
                .andReturn();
        String url = json.readTree(urlResponse.getResponse().getContentAsByteArray())
                .get("url").asText();

        String csv = mockMvc.perform(get(url).with(como(tenant, usuario)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType("text/csv"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv)
                .contains("metric_code", "contacts.total.count", "deals.conversion.percent")
                .doesNotContain("storage_key", "request_hash");

        TenantContext.executarComo(tenant, () -> {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM audit_event
                     WHERE target_id = ? AND action_code = 'EXPORT_REQUESTED_V1'
                    """, Long.class, id)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM audit_event
                     WHERE target_id = ? AND action_code = 'EXPORT_COMPLETED_V1'
                    """, Long.class, id)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM audit_event
                     WHERE target_id = ? AND action_code = 'EXPORT_DOWNLOADED_V1'
                    """, Long.class, id)).isOne();
            return null;
        });
    }

    @Test
    void revogacaoDepoisDoPedidoImpedeExecucaoEDownload() throws Exception {
        UUID negada = solicitar("relatorio-revogado-0001", tenant, usuario);
        reservar(negada, tenant);
        revogarAcesso(tenant, usuario);

        TenantContext.executarComo(tenant, () -> {
            exportacoes.processar(negada);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM report_export_job WHERE id = ?", String.class, negada))
                    .isEqualTo("DENIED");
            assertThat(jdbc.queryForObject(
                    "SELECT storage_key FROM report_export_job WHERE id = ?", String.class, negada))
                    .isNull();
            return null;
        });

        UUID usuarioComAcesso = cenario.criarUsuario(tenant, "segundo-gestor", "12345");
        cenario.concederTudoNoTenant(tenant, usuarioComAcesso, "reports.read");
        mockMvc.perform(get("/api/relatorios/exportacoes/{id}", negada)
                        .with(como(tenant, usuarioComAcesso)))
                .andExpect(status().isNotFound());

        UUID concluida = solicitar("relatorio-revogado-0002", tenant, usuarioComAcesso);
        reservar(concluida, tenant);
        TenantContext.executarComo(tenant, () -> {
            exportacoes.processar(concluida);
            return null;
        });
        revogarAcesso(tenant, usuarioComAcesso);
        mockMvc.perform(post("/api/relatorios/exportacoes/{id}/url", concluida)
                        .with(como(tenant, usuarioComAcesso)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rlsCancelamentoExpiracaoELegalHoldProtegemOArtefato() throws Exception {
        UUID id = solicitar("relatorio-hold-0001", tenant, usuario);
        mockMvc.perform(get("/api/relatorios/exportacoes/{id}", id)
                        .with(como(outroTenant, outroUsuario)))
                .andExpect(status().isNotFound());
        assertThat(TenantContext.executarComo(outroTenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM report_export_job WHERE id = ?", Long.class, id))).isZero();

        mockMvc.perform(post("/api/relatorios/exportacoes/{id}/cancelamento", id)
                        .with(como(tenant, usuario)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        UUID comHold = solicitar("relatorio-hold-0002", tenant, usuario);
        reservar(comHold, tenant);
        TenantContext.executarComo(tenant, () -> {
            exportacoes.processar(comHold);
            jdbc.update("""
                    INSERT INTO legal_hold
                        (id, tenant_id, target_type, target_id, reason, declared_by,
                         valid_from, created_by, updated_by)
                    VALUES (?, ?, 'REPORT_EXPORT', ?, 'teste de preservacao', ?,
                            now() - interval '1 minute', ?, ?)
                    """, UuidV7.gerar(), tenant, comHold, usuario, usuario, usuario);
            jdbc.update("UPDATE report_export_job SET expires_at = now() - interval '1 second' WHERE id = ?",
                    comHold);
            exportacoes.expurgar(comHold);
            var preservado = jdbc.queryForMap("""
                    SELECT status, storage_key, purged_at FROM report_export_job WHERE id = ?
                    """, comHold);
            assertThat(preservado.get("status")).isEqualTo("EXPIRED");
            assertThat(preservado.get("storage_key")).isNotNull();
            assertThat(preservado.get("purged_at")).isNull();
            jdbc.update("""
                    UPDATE legal_hold SET deleted_at = now(), updated_at = now(), updated_by = ?
                     WHERE target_type = 'REPORT_EXPORT' AND target_id = ?
                    """, usuario, comHold);
            exportacoes.expurgar(comHold);
            assertThat(jdbc.queryForObject(
                    "SELECT storage_key FROM report_export_job WHERE id = ?", String.class, comHold))
                    .isNull();
            return null;
        });
    }

    @Test
    void pedidosConcorrentesConvergemEQuotaDaFilaLimitaUmJobPorTenant() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<UUID> pedido = () -> {
                inicio.await();
                return TenantContext.executarComo(tenant, () -> exportacoes.solicitar(
                        "OVERVIEW_V1", "CSV", "relatorio-concorrente-0001", usuario).id());
            };
            Future<UUID> primeiro = executor.submit(pedido);
            Future<UUID> segundo = executor.submit(pedido);
            inicio.countDown();
            assertThat(primeiro.get()).isEqualTo(segundo.get());
        }
        assertThat(contarJobs(tenant)).isOne();

        TenantContext.executarComo(tenant, () -> exportacoes.solicitar(
                "OVERVIEW_V1", "CSV", "relatorio-concorrente-0002", usuario));
        var reservadas = jdbc.queryForList("""
                SELECT export_id
                  FROM reservar_exportacoes_relatorio(10, 60, 1, 3, 'teste-concorrencia')
                """, UUID.class);
        assertThat(reservadas).hasSize(1);
        assertThat(jdbc.queryForList("""
                SELECT export_id
                  FROM reservar_exportacoes_relatorio(10, 60, 1, 3, 'teste-concorrencia-2')
                """, UUID.class)).isEmpty();
    }

    private UUID solicitar(String key, UUID tenantId, UUID userId) throws Exception {
        var resposta = mockMvc.perform(post("/api/relatorios/exportacoes")
                        .with(como(tenantId, userId)).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportCode\":\"OVERVIEW_V1\",\"format\":\"CSV\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        return UUID.fromString(json.readTree(resposta.getResponse().getContentAsByteArray())
                .get("id").asText());
    }

    private void reservar(UUID id, UUID tenantId) {
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                UPDATE report_export_job
                   SET status = 'PROCESSING', attempts = attempts + 1,
                       lease_owner = 'teste', lease_until = now() + interval '1 minute'
                 WHERE id = ?
                """, id));
    }

    private long contarJobs(UUID tenantId) {
        return TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM report_export_job", Long.class));
    }

    private void revogarAcesso(UUID tenantId, UUID userId) {
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                UPDATE membership_scope s
                   SET valid_until = now() - interval '1 second'
                  FROM organization_membership m
                 WHERE s.membership_id = m.id AND s.tenant_id = m.tenant_id
                   AND m.tenant_id = ? AND m.user_id = ? AND s.valid_until IS NULL
                """, tenantId, userId));
    }

    private RequestPostProcessor como(UUID tenantId, UUID userId) {
        return jwt().jwt(builder -> builder.subject(userId.toString())
                .claim("tid", tenantId.toString()).claim("login", "gestor-relatorios"));
    }
}
