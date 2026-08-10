package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.automation.api.AutomationAction;
import br.com.pnp.crm.automation.api.AutomationTrigger;
import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TesteDeIntegracao
@Import(MotorDeAutomacoesTest.AcoesDeTeste.class)
@TestPropertySource(properties = "app.automation.max-executions-per-minute=2")
class MotorDeAutomacoesTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired DefinicaoAutomacaoService definicoes;
    @Autowired MotorAutomacaoService motor;
    @Autowired ControleExecucaoAutomacao controle;
    @Autowired ReservaExecucoesAutomacao reserva;
    @Autowired ProcessamentoExecucaoAutomacao processamento;
    @Autowired AutomationActionRegistry actions;
    @Autowired AtomicInteger efeitosExternos;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        efeitosExternos.set(0);
        tenantId = cenario.criarTenant("automation-test", "Automation Test");
        userId = cenario.criarUsuario(tenantId, "automation-owner", "Senha forte 123!");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void replayNaoDuplicaERecursaoEhExplicavelmenteRejeitada() {
        UUID definicao = ativa("replay", "NOOP_V1", 1);
        AutomationTrigger gatilho = new AutomationTrigger(
                tenantId, "MESSAGE_RECEIVED", "message:abc", UUID.randomUUID(), null);

        UUID primeira = motor.disparar(gatilho).getFirst();
        UUID repetida = motor.disparar(gatilho).getFirst();
        UUID rejeitada = motor.disparar(new AutomationTrigger(
                tenantId, "MESSAGE_RECEIVED", "message:recursiva",
                UUID.randomUUID(), primeira)).getFirst();

        assertThat(repetida).isEqualTo(primeira);
        TenantContext.executarComo(tenantId, () -> {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM automation_execution
                     WHERE definition_id = ? AND trigger_key = 'message:abc'
                    """, Long.class, definicao)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM automation_execution WHERE id = ?
                    """, String.class, rejeitada)).isEqualTo("REJECTED");
            assertThat(jdbc.queryForObject("""
                    SELECT failure_code FROM automation_execution WHERE id = ?
                    """, String.class, rejeitada)).isEqualTo("RECURSION_BLOCKED");
            return null;
        });
    }

    @Test
    void versaoEditadaNaoMudaExecucaoAntigaESnapshotEhImutavel() {
        UUID definicao = ativa("versionada", "NOOP_V1", 1);
        UUID antiga = motor.disparar(new AutomationTrigger(
                tenantId, "MESSAGE_RECEIVED", "message:v1", null, null)).getFirst();

        TenantContext.executarComo(tenantId, () -> {
            definicoes.criarVersao(definicao,
                    novaVersao("Versionada nova", "TEST_EXTERNAL_V1", 1), userId);
            definicoes.ativar(definicao, 2, userId);
            assertThat(jdbc.queryForObject("""
                    SELECT v.version_number
                      FROM automation_execution e
                      JOIN automation_definition_version v ON v.id = e.definition_version_id
                     WHERE e.id = ?
                    """, Integer.class, antiga)).isEqualTo(1);
            UUID versaoAntiga = jdbc.queryForObject("""
                    SELECT definition_version_id FROM automation_execution WHERE id = ?
                    """, UUID.class, antiga);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE automation_definition_version
                       SET specification = '{"alterada":true}' WHERE id = ?
                    """, versaoAntiga))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("append-only");
            return null;
        });
    }

    @Test
    void dryRunNaoExecutaEfeitoExternoETrilhaNaoCarregaChaveDoGatilho() {
        UUID definicao = ativa("dry-run", "TEST_EXTERNAL_V1", 1);
        UUID execucao = TenantContext.executarComo(tenantId, () ->
                motor.dispararManual(definicao, "segredo-nao-vai-para-log", null,
                        true, 1, userId));

        worker().processar();

        assertThat(efeitosExternos).hasValue(0);
        TenantContext.executarComo(tenantId, () -> {
            assertThat(controle.buscar(execucao).status()).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("""
                    SELECT status FROM automation_execution_step WHERE execution_id = ?
                    """, String.class, execucao)).isEqualTo("SKIPPED");
            String trilha = jdbc.queryForObject("""
                    SELECT string_agg(reason_code, ',') FROM automation_transition
                     WHERE execution_id = ?
                    """, String.class, execucao);
            assertThat(trilha).doesNotContain("segredo-nao-vai-para-log");
            return null;
        });
    }

    @Test
    void pausaRetomadaECancelamentoSaoIdempotentes() {
        UUID definicao = ativa("controle", "NOOP_V1", 1);
        UUID execucao = TenantContext.executarComo(tenantId, () ->
                motor.dispararManual(definicao, "controle-1", null,
                        false, null, userId));

        TenantContext.executarComo(tenantId, () -> {
            assertThat(controle.pausar(execucao, userId).status()).isEqualTo("PAUSED");
            assertThat(controle.pausar(execucao, userId).status()).isEqualTo("PAUSED");
            assertThat(controle.retomar(execucao, userId).status()).isEqualTo("QUEUED");
            assertThat(controle.cancelar(execucao, userId).status()).isEqualTo("CANCELLED");
            assertThat(controle.cancelar(execucao, userId).status()).isEqualTo("CANCELLED");
            return null;
        });
    }

    @Test
    void leasesEConcorrenciaDaDefinicaoImpedemDoisWorkersNoMesmoTrabalho() {
        UUID definicao = ativa("lease", "NOOP_V1", 1);
        TenantContext.executarComo(tenantId, () -> {
            motor.dispararManual(definicao, "lease-1", null, false, null, userId);
            motor.dispararManual(definicao, "lease-2", null, false, null, userId);
            return null;
        });

        List<ReservaExecucoesAutomacao.Reservada> primeira = reserva.proximoLote();
        List<ReservaExecucoesAutomacao.Reservada> concorrente = reserva.proximoLote();
        assertThat(primeira).hasSize(1);
        assertThat(concorrente).isEmpty();

        ReservaExecucoesAutomacao.Reservada item = primeira.getFirst();
        TenantContext.executarComo(item.tenantId(), () -> {
            var trabalho = processamento.preparar(item.executionId(), 30);
            processamento.concluir(trabalho,
                    ProcessamentoExecucaoAutomacao.Resultado.sucesso());
            return null;
        });
        assertThat(reserva.proximoLote()).hasSize(1);
    }

    @Test
    void timeoutEncerraSemExecutarAcao() {
        UUID definicao = ativa("timeout", "TEST_EXTERNAL_V1", 1);
        UUID execucao = TenantContext.executarComo(tenantId, () -> {
            UUID id = motor.dispararManual(definicao, "timeout-1", null,
                    false, null, userId);
            jdbc.update("""
                    UPDATE automation_execution SET deadline_at = ? WHERE id = ?
                    """, Timestamp.from(Instant.now().minusSeconds(1)), id);
            jdbc.update("""
                    UPDATE automation_execution_step SET status = 'RUNNING'
                     WHERE execution_id = ?
                    """, id);
            return id;
        });

        worker().processar();

        assertThat(efeitosExternos).hasValue(0);
        TenantContext.executarComo(tenantId, () -> {
            assertThat(controle.buscar(execucao).status()).isEqualTo("TIMED_OUT");
            assertThat(jdbc.queryForMap("""
                    SELECT effect_type, status, reason_code FROM automation_compensation
                     WHERE execution_id = ?
                    """, execucao))
                    .containsEntry("effect_type", "EXTERNAL_IRREVERSIBLE")
                    .containsEntry("status", "IMPOSSIBLE")
                    .containsEntry("reason_code", "EXECUTION_TIMEOUT");
            return null;
        });
    }

    @Test
    void quotaPorTenantBloqueiaTerceiroGatilhoNaJanela() {
        UUID definicao = ativa("quota", "NOOP_V1", 2);

        TenantContext.executarComo(tenantId, () -> {
            motor.dispararManual(definicao, "quota-1", null, false, null, userId);
            motor.dispararManual(definicao, "quota-2", null, false, null, userId);
            assertThatThrownBy(() -> motor.dispararManual(
                    definicao, "quota-3", null, false, null, userId))
                    .isInstanceOf(QuotaAutomacaoExcedidaException.class);
            return null;
        });
    }

    @Test
    void falhaTransitoriaRepeteSomenteAcaoSegura() {
        UUID definicao = ativa("retry-seguro",
                new AutomationModel.EspecificacaoRequest(
                        "MESSAGE_RECEIVED", "inicio", 30,
                        List.of(new AutomationModel.PassoRequest(
                                "inicio", "TEST_TRANSIENT_V1", 2, List.of()))), 1);
        UUID execucao = TenantContext.executarComo(tenantId, () ->
                motor.dispararManual(definicao, "retry-1", null,
                        false, null, userId));

        worker().processar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("""
                    UPDATE automation_execution_step SET not_before = now() - interval '1 second'
                     WHERE execution_id = ?
                    """, execucao);
            return null;
        });
        worker().processar();

        assertThat(efeitosExternos).hasValue(2);
        TenantContext.executarComo(tenantId, () -> {
            assertThat(controle.buscar(execucao).status()).isEqualTo("SUCCEEDED");
            assertThat(jdbc.queryForObject("""
                    SELECT attempt_count FROM automation_execution_step WHERE execution_id = ?
                    """, Integer.class, execucao)).isEqualTo(2);
            return null;
        });
    }

    @Test
    void falhaPosteriorRegistraCompensacaoReversivel() {
        UUID definicao = ativa("compensacao",
                new AutomationModel.EspecificacaoRequest(
                        "MESSAGE_RECEIVED", "primeiro", 30,
                        List.of(
                                new AutomationModel.PassoRequest(
                                        "primeiro", "TEST_REVERSIBLE_V1", 1,
                                        List.of("falhar")),
                                new AutomationModel.PassoRequest(
                                        "falhar", "TEST_FAILURE_V1", 1, List.of()))), 1);
        UUID execucao = TenantContext.executarComo(tenantId, () ->
                motor.dispararManual(definicao, "compensacao-1", null,
                        false, null, userId));

        worker().processar();
        worker().processar();

        TenantContext.executarComo(tenantId, () -> {
            assertThat(controle.buscar(execucao).status()).isEqualTo("FAILED");
            assertThat(jdbc.queryForMap("""
                    SELECT effect_type, status, reason_code FROM automation_compensation
                     WHERE execution_id = ?
                    """, execucao))
                    .containsEntry("effect_type", "EXTERNAL_REVERSIBLE")
                    .containsEntry("status", "REGISTERED")
                    .containsEntry("reason_code", "EXECUTION_FAILED");
            return null;
        });
    }

    private UUID ativa(String codigo, String acao, int concorrencia) {
        return ativa(codigo, especificacao(acao), concorrencia);
    }

    private UUID ativa(String codigo, AutomationModel.EspecificacaoRequest especificacao,
                       int concorrencia) {
        return TenantContext.executarComo(tenantId, () -> {
            var criada = definicoes.criar(new AutomationModel.DefinicaoRequest(
                    codigo, "Automacao " + codigo, concorrencia,
                    especificacao), userId);
            definicoes.ativar(criada.id(), 1, userId);
            return criada.id();
        });
    }

    private AutomationModel.NovaVersaoRequest novaVersao(
            String nome, String acao, int concorrencia) {
        return new AutomationModel.NovaVersaoRequest(
                nome, concorrencia, especificacao(acao));
    }

    private AutomationModel.EspecificacaoRequest especificacao(String acao) {
        return new AutomationModel.EspecificacaoRequest(
                "MESSAGE_RECEIVED", "inicio", 30,
                List.of(new AutomationModel.PassoRequest(
                        "inicio", acao, 1, List.of())));
    }

    private AutomationWorker worker() {
        return new AutomationWorker(reserva, processamento, actions);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AcoesDeTeste {

        @Bean
        AtomicInteger efeitosExternos() {
            return new AtomicInteger();
        }

        @Bean
        AutomationAction acaoExternaDeTeste(AtomicInteger efeitosExternos) {
            return new AutomationAction() {
                @Override
                public String tipo() {
                    return "TEST_EXTERNAL_V1";
                }

                @Override
                public NaturezaEfeito natureza() {
                    return NaturezaEfeito.EXTERNAL_IRREVERSIBLE;
                }

                @Override
                public boolean repeticaoSegura() {
                    return false;
                }

                @Override
                public Resultado executar(Contexto contexto) {
                    efeitosExternos.incrementAndGet();
                    return Resultado.SUCESSO;
                }
            };
        }

        @Bean
        AutomationAction acaoTransitoriaDeTeste(AtomicInteger efeitosExternos) {
            return new AutomationAction() {
                @Override
                public String tipo() {
                    return "TEST_TRANSIENT_V1";
                }

                @Override
                public NaturezaEfeito natureza() {
                    return NaturezaEfeito.EXTERNAL_REVERSIBLE;
                }

                @Override
                public boolean repeticaoSegura() {
                    return true;
                }

                @Override
                public Resultado executar(Contexto contexto) {
                    return efeitosExternos.incrementAndGet() == 1
                            ? Resultado.FALHA_TRANSITORIA : Resultado.SUCESSO;
                }
            };
        }

        @Bean
        AutomationAction acaoReversivelDeTeste() {
            return new AutomationAction() {
                @Override
                public String tipo() {
                    return "TEST_REVERSIBLE_V1";
                }

                @Override
                public NaturezaEfeito natureza() {
                    return NaturezaEfeito.EXTERNAL_REVERSIBLE;
                }

                @Override
                public boolean repeticaoSegura() {
                    return false;
                }

                @Override
                public Resultado executar(Contexto contexto) {
                    return Resultado.SUCESSO;
                }
            };
        }

        @Bean
        AutomationAction acaoDeFalhaDeTeste() {
            return new AutomationAction() {
                @Override
                public String tipo() {
                    return "TEST_FAILURE_V1";
                }

                @Override
                public NaturezaEfeito natureza() {
                    return NaturezaEfeito.INTERNAL;
                }

                @Override
                public boolean repeticaoSegura() {
                    return true;
                }

                @Override
                public Resultado executar(Contexto contexto) {
                    return Resultado.FALHA_PERMANENTE;
                }
            };
        }
    }
}
