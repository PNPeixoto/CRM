package br.com.pnp.crm;

import br.com.pnp.crm.billing.api.EntitlementService;
import br.com.pnp.crm.billing.api.EntitlementService.Contratacao;
import br.com.pnp.crm.billing.api.EntitlementService.DisponibilidadeTecnica;
import br.com.pnp.crm.billing.api.EntitlementService.EventoDeUso;
import br.com.pnp.crm.billing.api.EntitlementService.ResultadoMedicao;
import br.com.pnp.crm.billing.api.EntitlementService.ResultadoRegistro;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@TesteDeIntegracao
class EntitlementsMedicaoTest {

    private static final String CAPACIDADE = "OMNICHANNEL_MESSAGING";
    private static final String METRICA = "MESSAGE_OUTBOUND_SENT";
    private static final Instant AGOSTO = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant SETEMBRO = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntitlementService entitlements;

    private UUID tenantId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenantId = cenario.criarTenant("entitlements-v27", "Entitlements V27");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void disponibilidadeTecnicaNaoConcedeContratoEReplayNaoDuplica() {
        TenantContext.executarComo(tenantId, () -> {
            var semContrato = entitlements.avaliar(CAPACIDADE, AGOSTO.plusSeconds(60));
            assertThat(semContrato.disponibilidade()).isEqualTo(DisponibilidadeTecnica.DISPONIVEL);
            assertThat(semContrato.contratacao()).isEqualTo(Contratacao.NAO_CONTRATADA);

            UUID grantId = conceder(1, AGOSTO, SETEMBRO, "CALENDAR_MONTH", "UTC",
                    null, null, null);
            var contratado = entitlements.avaliar(CAPACIDADE, AGOSTO.plusSeconds(60));
            assertThat(contratado.contratacao()).isEqualTo(Contratacao.CONTRATADA);
            assertThat(contratado.entitlementGrantId()).isEqualTo(grantId);

            EventoDeUso evento = evento("replay-1", 3, "pedido-1", AGOSTO.plusSeconds(3600));
            assertThat(entitlements.medir(evento).resultado()).isEqualTo(ResultadoRegistro.RECORDED);
            assertThat(entitlements.medir(evento).resultado()).isEqualTo(ResultadoRegistro.REPLAY);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM usage_event WHERE idempotency_key = 'replay-1'
                    """, Long.class)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT sum(quantity) FROM usage_event WHERE idempotency_key = 'replay-1'
                    """, Long.class)).isEqualTo(3L);
            return null;
        });
    }

    @Test
    void eventoAtrasadoUsaVersaoDaOcorrenciaEMudancaNaoReescreveHistorico() {
        Instant fevereiro = Instant.parse("2026-02-01T00:00:00Z");
        Instant marco = Instant.parse("2026-03-01T00:00:00Z");
        TenantContext.executarComo(tenantId, () -> {
            UUID versao1 = conceder(1, Instant.parse("2026-01-01T00:00:00Z"), fevereiro,
                    "CALENDAR_MONTH", "America/Sao_Paulo", 50L, 100L, null);
            UUID versao2 = conceder(2, fevereiro, marco,
                    "CALENDAR_MONTH", "America/Sao_Paulo", 500L, 1_000L, null);

            ResultadoMedicao atrasado = entitlements.medir(evento(
                    "late-january", 7, "origem-janeiro",
                    Instant.parse("2026-01-20T12:00:00Z")));
            ResultadoMedicao atual = entitlements.medir(evento(
                    "february", 11, "origem-fevereiro",
                    Instant.parse("2026-02-20T12:00:00Z")));

            assertThat(atrasado.entitlementGrantId()).isEqualTo(versao1);
            assertThat(atual.entitlementGrantId()).isEqualTo(versao2);
            assertThat(entitlements.agregar(
                    METRICA, Instant.parse("2026-01-20T12:00:00Z"))
                    .orElseThrow().totalQuantity()).isEqualTo(7L);
            assertThat(entitlements.agregar(
                    METRICA, Instant.parse("2026-02-20T12:00:00Z"))
                    .orElseThrow().totalQuantity()).isEqualTo(11L);

            assertThat(jdbc.queryForObject("""
                    SELECT hard_limit FROM entitlement_grant WHERE id = ?
                    """, Long.class, versao1)).isEqualTo(100L);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM entitlement_grant WHERE capability_code = ?
                    """, Long.class, CAPACIDADE)).isEqualTo(2L);
            return null;
        });
    }

    @Test
    void tenantNaoLeNemConsomeConcessaoDeOutro() {
        TenantContext.executarComo(tenantId, () -> {
            conceder(1, AGOSTO, SETEMBRO, "CALENDAR_MONTH", "UTC",
                    null, null, null);
            entitlements.medir(evento("tenant-a", 4, "fonte-a", AGOSTO.plusSeconds(10)));
            return null;
        });

        UUID tenantB = cenario.criarTenant("entitlements-outro", "Entitlements Outro");
        TenantContext.executarComo(tenantB, () -> {
            assertThat(entitlements.avaliar(CAPACIDADE, AGOSTO.plusSeconds(10)).contratacao())
                    .isEqualTo(Contratacao.NAO_CONTRATADA);
            assertThat(entitlements.medir(evento(
                    "tenant-b", 4, "fonte-b", AGOSTO.plusSeconds(10))).resultado())
                    .isEqualTo(ResultadoRegistro.NOT_CONTRACTED);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM entitlement_grant", Long.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_event", Long.class))
                    .isZero();
            return null;
        });
    }

    @Test
    void hardLimitConcorrenteEhAtomicoSemContadorMutavel() throws Exception {
        TenantContext.executarComo(tenantId, () -> {
            conceder(1, AGOSTO, SETEMBRO, "CALENDAR_MONTH", "UTC",
                    null, 10L, null);
            return null;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = CompletableFuture.supplyAsync(() -> medirComoTenant(
                    evento("hard-a", 6, "fonte-hard-a", AGOSTO.plusSeconds(100))), executor);
            var segunda = CompletableFuture.supplyAsync(() -> medirComoTenant(
                    evento("hard-b", 6, "fonte-hard-b", AGOSTO.plusSeconds(100))), executor);
            List<ResultadoRegistro> resultados = List.of(
                    primeira.get().resultado(), segunda.get().resultado());
            assertThat(resultados).containsExactlyInAnyOrder(
                    ResultadoRegistro.RECORDED, ResultadoRegistro.HARD_LIMIT_EXCEEDED);
        }

        TenantContext.executarComo(tenantId, () -> {
            assertThat(entitlements.agregar(METRICA, AGOSTO.plusSeconds(100)).get().totalQuantity())
                    .isEqualTo(6L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_event", Long.class))
                    .isOne();
            return null;
        });
    }

    @Test
    void softHardECarenciaTemResultadosDistintos() {
        TenantContext.executarComo(tenantId, () -> {
            conceder(1, AGOSTO, SETEMBRO, "CALENDAR_MONTH", "UTC",
                    5L, 10L, Instant.parse("2026-08-20T00:00:00Z"));

            assertThat(entitlements.medir(evento(
                    "soft", 6, "fonte-soft", Instant.parse("2026-08-10T12:00:00Z"))).resultado())
                    .isEqualTo(ResultadoRegistro.SOFT_LIMIT_EXCEEDED);
            assertThat(entitlements.medir(evento(
                    "grace", 5, "fonte-grace", Instant.parse("2026-08-15T12:00:00Z"))).resultado())
                    .isEqualTo(ResultadoRegistro.HARD_LIMIT_GRACE);
            assertThat(entitlements.medir(evento(
                    "blocked", 1, "fonte-blocked", Instant.parse("2026-08-25T12:00:00Z"))).resultado())
                    .isEqualTo(ResultadoRegistro.HARD_LIMIT_EXCEEDED);

            var agregado = entitlements.agregar(METRICA, Instant.parse("2026-08-25T12:00:00Z")).get();
            assertThat(agregado.totalQuantity()).isEqualTo(11L);
            assertThat(agregado.eventCount()).isEqualTo(2L);
            return null;
        });
    }

    @Test
    void agregadoReconciliaQuantidadeEFontesDoLedger() {
        TenantContext.executarComo(tenantId, () -> {
            conceder(1, AGOSTO, SETEMBRO, "CALENDAR_MONTH", "UTC",
                    null, null, null);
            entitlements.medir(evento("source-1", 2, "pedido-101", AGOSTO.plusSeconds(10)));
            entitlements.medir(evento("source-2", 3, "pedido-102", AGOSTO.plusSeconds(20)));
            entitlements.medir(evento("source-3", 5, "pedido-103", AGOSTO.plusSeconds(30)));

            var agregado = entitlements.agregar(METRICA, AGOSTO.plusSeconds(40)).orElseThrow();
            var fontes = entitlements.listarFontes(
                    agregado.entitlementGrantId(), agregado.metricCode(),
                    agregado.windowStartedAt(), agregado.windowEndedAt());

            assertThat(fontes).extracting(EntitlementService.FonteDeUso::sourceId)
                    .containsExactly("pedido-101", "pedido-102", "pedido-103");
            assertThat(fontes.stream().mapToLong(EntitlementService.FonteDeUso::quantity).sum())
                    .isEqualTo(agregado.totalQuantity());
            assertThat(fontes).hasSize((int) agregado.eventCount());
            return null;
        });
    }

    private UUID conceder(
            int versao,
            Instant inicio,
            Instant fim,
            String janela,
            String timezone,
            Long soft,
            Long hard,
            Instant carenciaAte) {
        UUID id = UuidV7.gerar();
        jdbc.update("""
                INSERT INTO entitlement_grant (
                    id, tenant_id, capability_code, version_number, contract_reference,
                    valid_from, valid_until, window_type, time_zone, soft_limit,
                    hard_limit, hard_limit_grace_until
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, CAPACIDADE, versao, "contrato-teste-v" + versao,
                java.sql.Timestamp.from(inicio), java.sql.Timestamp.from(fim),
                janela, timezone, soft, hard,
                carenciaAte == null ? null : java.sql.Timestamp.from(carenciaAte));
        return id;
    }

    private ResultadoMedicao medirComoTenant(EventoDeUso evento) {
        return TenantContext.executarComo(tenantId, () -> entitlements.medir(evento));
    }

    private static EventoDeUso evento(
            String chave,
            long quantidade,
            String fonte,
            Instant ocorridoEm) {
        return new EventoDeUso(METRICA, quantidade, "ORDER", fonte, chave, ocorridoEm);
    }
}
