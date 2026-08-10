package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas e transicoes idempotentes comandadas por usuario. */
@Service
class ControleExecucaoAutomacao {

    private static final List<String> TERMINAIS = List.of(
            "SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT", "REJECTED");

    private final JdbcTemplate jdbc;

    ControleExecucaoAutomacao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    List<AutomationModel.ExecucaoResumo> listar(UUID definicaoId) {
        String filtro = definicaoId == null ? "" : " AND e.definition_id = ?";
        Object[] parametros = definicaoId == null
                ? new Object[]{TenantContext.obrigatorio()}
                : new Object[]{TenantContext.obrigatorio(), definicaoId};
        return jdbc.query("""
                SELECT e.id, e.definition_id, v.version_number, e.trigger_type,
                       e.dry_run, e.status, e.failure_code, e.deadline_at,
                       e.started_at, e.finished_at, e.created_at
                  FROM automation_execution e
                  JOIN automation_definition_version v
                    ON v.tenant_id = e.tenant_id AND v.id = e.definition_version_id
                 WHERE e.tenant_id = ?
                """ + filtro + " ORDER BY e.created_at DESC, e.id DESC LIMIT 200",
                (rs, row) -> new AutomationModel.ExecucaoResumo(
                        rs.getObject("id", UUID.class),
                        rs.getObject("definition_id", UUID.class),
                        rs.getInt("version_number"), rs.getString("trigger_type"),
                        rs.getBoolean("dry_run"), rs.getString("status"),
                        rs.getString("failure_code"), instante(rs.getTimestamp("deadline_at")),
                        instante(rs.getTimestamp("started_at")),
                        instante(rs.getTimestamp("finished_at")),
                        instante(rs.getTimestamp("created_at"))), parametros);
    }

    @Transactional(readOnly = true)
    AutomationModel.ExecucaoResumo buscar(UUID id) {
        return jdbc.query("""
                        SELECT e.id, e.definition_id, v.version_number, e.trigger_type,
                               e.dry_run, e.status, e.failure_code, e.deadline_at,
                               e.started_at, e.finished_at, e.created_at
                          FROM automation_execution e
                          JOIN automation_definition_version v
                            ON v.tenant_id = e.tenant_id
                           AND v.id = e.definition_version_id
                         WHERE e.tenant_id = ? AND e.id = ?
                        """, (rs, row) -> new AutomationModel.ExecucaoResumo(
                        rs.getObject("id", UUID.class),
                        rs.getObject("definition_id", UUID.class),
                        rs.getInt("version_number"), rs.getString("trigger_type"),
                        rs.getBoolean("dry_run"), rs.getString("status"),
                        rs.getString("failure_code"), instante(rs.getTimestamp("deadline_at")),
                        instante(rs.getTimestamp("started_at")),
                        instante(rs.getTimestamp("finished_at")),
                        instante(rs.getTimestamp("created_at"))),
                        TenantContext.obrigatorio(), id)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
    }

    @Transactional(readOnly = true)
    List<AutomationModel.TransicaoResumo> transicoes(UUID id) {
        buscar(id);
        return jdbc.query("""
                SELECT id, step_id, entity_type, previous_status, new_status,
                       reason_code, actor_type, occurred_at
                  FROM automation_transition
                 WHERE tenant_id = ? AND execution_id = ?
                 ORDER BY occurred_at, id
                """, (rs, row) -> new AutomationModel.TransicaoResumo(
                rs.getObject("id", UUID.class), rs.getObject("step_id", UUID.class),
                rs.getString("entity_type"), rs.getString("previous_status"),
                rs.getString("new_status"), rs.getString("reason_code"),
                rs.getString("actor_type"), instante(rs.getTimestamp("occurred_at"))),
                TenantContext.obrigatorio(), id);
    }

    @Transactional
    AutomationModel.ExecucaoResumo pausar(UUID id, UUID autorId) {
        Estado atual = bloquear(id);
        if (atual.status().equals("PAUSED") || TERMINAIS.contains(atual.status())) {
            return buscar(id);
        }
        if (!atual.status().equals("QUEUED") && !atual.status().equals("RUNNING")) {
            throw new RequisicaoInvalidaException("A execucao nao pode ser pausada.");
        }
        jdbc.update("""
                UPDATE automation_execution
                   SET status = 'PAUSED', lease_owner = NULL, lease_until = NULL,
                       updated_by = ?
                 WHERE tenant_id = ? AND id = ?
                """, autorId, TenantContext.obrigatorio(), id);
        transicao(id, null, "EXECUTION", atual.status(), "PAUSED", "USER_PAUSED", "USER");
        return buscar(id);
    }

    @Transactional
    AutomationModel.ExecucaoResumo retomar(UUID id, UUID autorId) {
        Estado atual = bloquear(id);
        if (atual.status().equals("QUEUED") || atual.status().equals("RUNNING")) {
            return buscar(id);
        }
        if (!atual.status().equals("PAUSED")) {
            throw new RequisicaoInvalidaException("A execucao nao pode ser retomada.");
        }
        jdbc.update("""
                UPDATE automation_execution
                   SET status = 'QUEUED', updated_by = ?
                 WHERE tenant_id = ? AND id = ?
                """, autorId, TenantContext.obrigatorio(), id);
        transicao(id, null, "EXECUTION", "PAUSED", "QUEUED", "USER_RESUMED", "USER");
        return buscar(id);
    }

    @Transactional
    AutomationModel.ExecucaoResumo cancelar(UUID id, UUID autorId) {
        Estado atual = bloquear(id);
        if (atual.status().equals("CANCELLED") || TERMINAIS.contains(atual.status())) {
            return buscar(id);
        }
        jdbc.update("""
                UPDATE automation_execution
                   SET status = 'CANCELLED', finished_at = now(),
                       lease_owner = NULL, lease_until = NULL, updated_by = ?
                 WHERE tenant_id = ? AND id = ?
                """, autorId, TenantContext.obrigatorio(), id);
        List<PassoEmAberto> passos = jdbc.query("""
                SELECT id, status, effect_type FROM automation_execution_step
                 WHERE tenant_id = ? AND execution_id = ?
                   AND status IN ('PENDING', 'RUNNING')
                 FOR UPDATE
                """, (rs, row) -> new PassoEmAberto(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("effect_type")), TenantContext.obrigatorio(), id);
        for (PassoEmAberto passo : passos) {
            jdbc.update("""
                    UPDATE automation_execution_step
                       SET status = 'CANCELLED', finished_at = now(), lease_until = NULL,
                           failure_code = 'USER_CANCELLED'
                     WHERE tenant_id = ? AND id = ?
                    """, TenantContext.obrigatorio(), passo.id());
            transicao(id, passo.id(), "STEP", passo.status(), "CANCELLED",
                    "USER_CANCELLED", "USER");
            if (passo.status().equals("RUNNING") && !passo.efeito().equals("INTERNAL")) {
                compensacao(id, passo.id(), passo.efeito(), "CANCELLED_IN_FLIGHT");
            }
        }
        registrarCompensacoesConcluidas(id, "USER_CANCELLED");
        transicao(id, null, "EXECUTION", atual.status(), "CANCELLED",
                "USER_CANCELLED", "USER");
        return buscar(id);
    }

    private Estado bloquear(UUID id) {
        return jdbc.query("""
                        SELECT status FROM automation_execution
                         WHERE tenant_id = ? AND id = ? FOR UPDATE
                        """, (rs, row) -> new Estado(rs.getString("status")),
                        TenantContext.obrigatorio(), id)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
    }

    private void registrarCompensacoesConcluidas(UUID executionId, String motivo) {
        List<PassoEmAberto> concluidos = jdbc.query("""
                SELECT id, status, effect_type FROM automation_execution_step
                 WHERE tenant_id = ? AND execution_id = ? AND status = 'SUCCEEDED'
                   AND effect_type <> 'INTERNAL'
                """, (rs, row) -> new PassoEmAberto(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("effect_type")), TenantContext.obrigatorio(), executionId);
        concluidos.forEach(passo -> compensacao(executionId, passo.id(), passo.efeito(), motivo));
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

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Execucao de automacao");
    }

    private record Estado(String status) {
    }

    private record PassoEmAberto(UUID id, String status, String efeito) {
    }
}
