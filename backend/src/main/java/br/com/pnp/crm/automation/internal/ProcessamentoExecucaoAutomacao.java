package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Maquina de estados persistida. Nenhum codigo de falha recebe texto externo. */
@Component
class ProcessamentoExecucaoAutomacao {

    private final JdbcTemplate jdbc;
    private final DefinicaoAutomacaoService definicoes;

    ProcessamentoExecucaoAutomacao(JdbcTemplate jdbc,
                                   DefinicaoAutomacaoService definicoes) {
        this.jdbc = jdbc;
        this.definicoes = definicoes;
    }

    @Transactional
    Trabalho preparar(UUID executionId, int leaseSegundos) {
        Execucao execucao = carregarExecucaoBloqueada(executionId);
        if (execucao == null || !execucao.status().equals("RUNNING")) return null;

        registrarInicioSeNecessario(executionId);
        if (!execucao.deadlineAt().isAfter(Instant.now())) {
            expirar(execucao);
            return null;
        }

        PassoLinha passo = jdbc.query("""
                        SELECT id, step_key, action_type, effect_type, retry_safe,
                               max_attempts, attempt_count, status, lease_until
                          FROM automation_execution_step
                         WHERE tenant_id = ? AND execution_id = ?
                           AND ((status = 'PENDING' AND not_before <= now())
                                OR (status = 'RUNNING' AND lease_until <= now()))
                         ORDER BY CASE WHEN status = 'RUNNING' THEN 0 ELSE 1 END,
                                  created_at, id
                         LIMIT 1 FOR UPDATE
                        """, (rs, row) -> new PassoLinha(
                        rs.getObject("id", UUID.class), rs.getString("step_key"),
                        rs.getString("action_type"), rs.getString("effect_type"),
                        rs.getBoolean("retry_safe"), rs.getInt("max_attempts"),
                        rs.getInt("attempt_count"), rs.getString("status"),
                        instante(rs.getTimestamp("lease_until"))),
                        TenantContext.obrigatorio(), executionId)
                .stream().findFirst().orElse(null);

        if (passo == null) {
            Long abertos = jdbc.queryForObject("""
                    SELECT count(*) FROM automation_execution_step
                     WHERE tenant_id = ? AND execution_id = ?
                       AND status IN ('PENDING', 'RUNNING')
                    """, Long.class, TenantContext.obrigatorio(), executionId);
            if (abertos == null || abertos == 0) {
                concluirExecucao(executionId, "SUCCEEDED", null,
                        "ALL_STEPS_COMPLETED", "WORKER");
            } else {
                liberarLease(executionId);
            }
            return null;
        }

        if (passo.status().equals("RUNNING")
                && (!passo.retrySafe() || passo.attemptCount() >= passo.maxAttempts())) {
            String codigo = passo.retrySafe()
                    ? "MAX_ATTEMPTS_REACHED" : "UNSAFE_RETRY_BLOCKED";
            falhar(execucao, passo, codigo);
            return null;
        }

        int tentativa = passo.attemptCount() + 1;
        jdbc.update("""
                UPDATE automation_execution_step
                   SET status = 'RUNNING', attempt_count = ?,
                       started_at = COALESCE(started_at, now()),
                       lease_until = now() + make_interval(secs => ?),
                       failure_code = NULL
                 WHERE tenant_id = ? AND id = ?
                """, tentativa, leaseSegundos, TenantContext.obrigatorio(), passo.id());
        transicao(executionId, passo.id(), "STEP", passo.status(), "RUNNING",
                passo.status().equals("RUNNING")
                        ? "LEASE_RECOVERED_SAFE_RETRY" : "STEP_RESERVED",
                "WORKER");

        AutomationModel.EspecificacaoSnapshot snapshot =
                definicoes.lerSnapshot(execucao.specification());
        AutomationModel.PassoSnapshot passoSnapshot = snapshot.passo(passo.stepKey());
        return new Trabalho(executionId, passo.id(), execucao.versionId(),
                execucao.dryRun(), execucao.triggerType(), execucao.sourceReferenceId(),
                passoSnapshot, snapshot);
    }

    @Transactional
    void concluir(Trabalho trabalho, Resultado resultado) {
        Execucao execucao = carregarExecucaoBloqueada(trabalho.executionId());
        if (execucao == null) return;
        PassoEstado estado = jdbc.query("""
                        SELECT status, attempt_count, max_attempts, retry_safe, effect_type
                          FROM automation_execution_step
                         WHERE tenant_id = ? AND id = ? FOR UPDATE
                        """, (rs, row) -> new PassoEstado(
                        rs.getString("status"), rs.getInt("attempt_count"),
                        rs.getInt("max_attempts"), rs.getBoolean("retry_safe"),
                        rs.getString("effect_type")),
                        TenantContext.obrigatorio(), trabalho.stepId())
                .stream().findFirst().orElse(null);
        if (estado == null || !estado.status().equals("RUNNING")) return;

        if (resultado.tipo() == TipoResultado.SUCESSO
                || resultado.tipo() == TipoResultado.SIMULADO) {
            String novo = resultado.tipo() == TipoResultado.SIMULADO ? "SKIPPED" : "SUCCEEDED";
            jdbc.update("""
                    UPDATE automation_execution_step
                       SET status = ?, finished_at = now(), lease_until = NULL,
                           failure_code = NULL
                     WHERE tenant_id = ? AND id = ?
                    """, novo, TenantContext.obrigatorio(), trabalho.stepId());
            transicao(trabalho.executionId(), trabalho.stepId(), "STEP", "RUNNING", novo,
                    resultado.tipo() == TipoResultado.SIMULADO
                            ? "DRY_RUN_NO_EFFECT" : "ACTION_SUCCEEDED",
                    "WORKER");
            if (!execucao.status().equals("CANCELLED")
                    && !execucao.status().equals("FAILED")
                    && !execucao.status().equals("TIMED_OUT")) {
                agendarProximos(trabalho);
            } else if (!estado.effectType().equals("INTERNAL")) {
                compensacao(trabalho.executionId(), trabalho.stepId(),
                        estado.effectType(), "TERMINAL_DURING_ACTION");
            }
            liberarLease(trabalho.executionId());
            concluirSeSemPassos(trabalho.executionId(), execucao.status());
            return;
        }

        if (resultado.tipo() == TipoResultado.TRANSITORIO
                && execucao.status().equals("RUNNING")
                && estado.retrySafe() && estado.attemptCount() < estado.maxAttempts()) {
            long espera = Math.min(60, 1L << Math.min(estado.attemptCount(), 6));
            jdbc.update("""
                    UPDATE automation_execution_step
                       SET status = 'PENDING', not_before = now() + make_interval(secs => ?),
                           lease_until = NULL, failure_code = ?
                     WHERE tenant_id = ? AND id = ?
                    """, espera, resultado.codigo(), TenantContext.obrigatorio(),
                    trabalho.stepId());
            transicao(trabalho.executionId(), trabalho.stepId(), "STEP", "RUNNING", "PENDING",
                    "SAFE_RETRY_SCHEDULED", "WORKER");
            liberarLease(trabalho.executionId());
            return;
        }

        PassoLinha linha = new PassoLinha(trabalho.stepId(), trabalho.passo().chave(),
                trabalho.passo().acao(), trabalho.passo().efeito().name(),
                estado.retrySafe(), estado.maxAttempts(), estado.attemptCount(),
                estado.status(), null);
        falhar(execucao, linha, resultado.codigo());
    }

    private Execucao carregarExecucaoBloqueada(UUID executionId) {
        return jdbc.query("""
                        SELECT e.id, e.status, e.deadline_at, e.dry_run,
                               e.trigger_type, e.source_reference_id,
                               e.definition_version_id, v.specification::text
                          FROM automation_execution e
                          JOIN automation_definition_version v
                            ON v.tenant_id = e.tenant_id AND v.id = e.definition_version_id
                         WHERE e.tenant_id = ? AND e.id = ?
                         FOR UPDATE OF e
                        """, (rs, row) -> new Execucao(
                        rs.getObject("id", UUID.class), rs.getString("status"),
                        instante(rs.getTimestamp("deadline_at")), rs.getBoolean("dry_run"),
                        rs.getString("trigger_type"),
                        rs.getObject("source_reference_id", UUID.class),
                        rs.getObject("definition_version_id", UUID.class),
                        rs.getString("specification")),
                        TenantContext.obrigatorio(), executionId)
                .stream().findFirst().orElse(null);
    }

    private void registrarInicioSeNecessario(UUID executionId) {
        Long existente = jdbc.queryForObject("""
                SELECT count(*) FROM automation_transition
                 WHERE tenant_id = ? AND execution_id = ?
                   AND entity_type = 'EXECUTION' AND new_status = 'RUNNING'
                """, Long.class, TenantContext.obrigatorio(), executionId);
        if (existente == null || existente == 0) {
            transicao(executionId, null, "EXECUTION", "QUEUED", "RUNNING",
                    "WORKER_RESERVED", "WORKER");
        }
    }

    private void agendarProximos(Trabalho trabalho) {
        for (String chave : trabalho.passo().proximos()) {
            AutomationModel.PassoSnapshot proximo = trabalho.snapshot().passo(chave);
            jdbc.update("""
                    INSERT INTO automation_execution_step (
                        id, tenant_id, execution_id, definition_version_id, step_key,
                        action_type, effect_type, retry_safe, max_attempts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, execution_id, step_key) DO NOTHING
                    """, UuidV7.gerar(), TenantContext.obrigatorio(), trabalho.executionId(),
                    trabalho.versionId(), proximo.chave(), proximo.acao(),
                    proximo.efeito().name(), proximo.repeticaoSegura(),
                    proximo.maxTentativas());
        }
    }

    private void concluirSeSemPassos(UUID executionId, String estadoAnterior) {
        if (!estadoAnterior.equals("RUNNING")) return;
        Long abertos = jdbc.queryForObject("""
                SELECT count(*) FROM automation_execution_step
                 WHERE tenant_id = ? AND execution_id = ?
                   AND status IN ('PENDING', 'RUNNING')
                """, Long.class, TenantContext.obrigatorio(), executionId);
        if (abertos == null || abertos == 0) {
            concluirExecucao(executionId, "SUCCEEDED", null,
                    "ALL_STEPS_COMPLETED", "WORKER");
        }
    }

    private void expirar(Execucao execucao) {
        cancelarPassosAbertos(execucao.id(), "EXECUTION_TIMEOUT");
        registrarCompensacoes(execucao.id(), "EXECUTION_TIMEOUT");
        concluirExecucao(execucao.id(), "TIMED_OUT", "EXECUTION_TIMEOUT",
                "EXECUTION_TIMEOUT", "WORKER");
    }

    private void falhar(Execucao execucao, PassoLinha passo, String codigo) {
        jdbc.update("""
                UPDATE automation_execution_step
                   SET status = 'FAILED', finished_at = now(), lease_until = NULL,
                       failure_code = ?
                 WHERE tenant_id = ? AND id = ?
                """, codigo, TenantContext.obrigatorio(), passo.id());
        transicao(execucao.id(), passo.id(), "STEP", passo.status(), "FAILED",
                codigo, "WORKER");
        cancelarPassosAbertos(execucao.id(), "EXECUTION_FAILED");
        registrarCompensacoes(execucao.id(), "EXECUTION_FAILED");
        concluirExecucao(execucao.id(), "FAILED", codigo, codigo, "WORKER");
    }

    private void cancelarPassosAbertos(UUID executionId, String motivo) {
        List<PassoCancelar> passos = jdbc.query("""
                SELECT id, status, effect_type FROM automation_execution_step
                 WHERE tenant_id = ? AND execution_id = ?
                   AND status IN ('PENDING', 'RUNNING')
                 FOR UPDATE
                """, (rs, row) -> new PassoCancelar(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("effect_type")),
                TenantContext.obrigatorio(), executionId);
        for (PassoCancelar passo : passos) {
            if (passo.status().equals("RUNNING") && !passo.effectType().equals("INTERNAL")) {
                compensacao(executionId, passo.id(), passo.effectType(), motivo);
            }
            jdbc.update("""
                    UPDATE automation_execution_step
                       SET status = 'CANCELLED', finished_at = now(), lease_until = NULL,
                           failure_code = ?
                     WHERE tenant_id = ? AND id = ?
                    """, motivo, TenantContext.obrigatorio(), passo.id());
            transicao(executionId, passo.id(), "STEP", passo.status(), "CANCELLED",
                    motivo, "WORKER");
        }
    }

    private void registrarCompensacoes(UUID executionId, String motivo) {
        List<PassoEfeito> passos = jdbc.query("""
                SELECT id, effect_type FROM automation_execution_step
                 WHERE tenant_id = ? AND execution_id = ? AND status = 'SUCCEEDED'
                   AND effect_type <> 'INTERNAL'
                """, (rs, row) -> new PassoEfeito(
                rs.getObject("id", UUID.class), rs.getString("effect_type")),
                TenantContext.obrigatorio(), executionId);
        passos.forEach(passo -> compensacao(
                executionId, passo.id(), passo.effectType(), motivo));
    }

    private void compensacao(UUID executionId, UUID stepId, String efeito, String motivo) {
        String status = efeito.equals("EXTERNAL_REVERSIBLE") ? "REGISTERED" : "IMPOSSIBLE";
        jdbc.update("""
                INSERT INTO automation_compensation (
                    id, tenant_id, execution_id, step_id, effect_type, status, reason_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, execution_id, step_id) DO NOTHING
                """, UuidV7.gerar(), TenantContext.obrigatorio(), executionId, stepId,
                efeito, status, motivo);
    }

    private void concluirExecucao(UUID executionId, String novo, String falha,
                                  String motivo, String ator) {
        String anterior = jdbc.queryForObject("""
                SELECT status FROM automation_execution
                 WHERE tenant_id = ? AND id = ?
                """, String.class, TenantContext.obrigatorio(), executionId);
        if (anterior == null || anterior.equals(novo)) return;
        jdbc.update("""
                UPDATE automation_execution
                   SET status = ?, failure_code = ?, finished_at = now(),
                       lease_owner = NULL, lease_until = NULL
                 WHERE tenant_id = ? AND id = ?
                """, novo, falha, TenantContext.obrigatorio(), executionId);
        transicao(executionId, null, "EXECUTION", anterior, novo, motivo, ator);
    }

    private void liberarLease(UUID executionId) {
        jdbc.update("""
                UPDATE automation_execution SET lease_owner = NULL, lease_until = NULL
                 WHERE tenant_id = ? AND id = ?
                """, TenantContext.obrigatorio(), executionId);
    }

    private void transicao(UUID executionId, UUID stepId, String entidade,
                           String anterior, String novo, String motivo, String ator) {
        jdbc.update("""
                INSERT INTO automation_transition (
                    id, tenant_id, execution_id, step_id, entity_type,
                    previous_status, new_status, reason_code, actor_type
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UuidV7.gerar(), TenantContext.obrigatorio(), executionId, stepId,
                entidade, anterior, novo, motivo, ator);
    }

    private static Instant instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    record Trabalho(
            UUID executionId,
            UUID stepId,
            UUID versionId,
            boolean dryRun,
            String triggerType,
            UUID sourceReferenceId,
            AutomationModel.PassoSnapshot passo,
            AutomationModel.EspecificacaoSnapshot snapshot) {
    }

    record Resultado(TipoResultado tipo, String codigo) {
        static Resultado sucesso() {
            return new Resultado(TipoResultado.SUCESSO, null);
        }

        static Resultado simulado() {
            return new Resultado(TipoResultado.SIMULADO, null);
        }

        static Resultado transitorio(String codigo) {
            return new Resultado(TipoResultado.TRANSITORIO, codigo);
        }

        static Resultado permanente(String codigo) {
            return new Resultado(TipoResultado.PERMANENTE, codigo);
        }
    }

    enum TipoResultado {
        SUCESSO, SIMULADO, TRANSITORIO, PERMANENTE
    }

    private record Execucao(
            UUID id, String status, Instant deadlineAt, boolean dryRun,
            String triggerType, UUID sourceReferenceId, UUID versionId,
            String specification) {
    }

    private record PassoLinha(
            UUID id, String stepKey, String actionType, String effectType,
            boolean retrySafe, int maxAttempts, int attemptCount,
            String status, Instant leaseUntil) {
    }

    private record PassoEstado(
            String status, int attemptCount, int maxAttempts,
            boolean retrySafe, String effectType) {
    }

    private record PassoCancelar(UUID id, String status, String effectType) {
    }

    private record PassoEfeito(UUID id, String effectType) {
    }
}
