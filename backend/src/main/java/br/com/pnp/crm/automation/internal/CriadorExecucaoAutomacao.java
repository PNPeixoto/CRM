package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.automation.api.AutomationTrigger;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** Cria a execucao uma unica vez e aplica quotas sob lock por tenant. */
@Component
class CriadorExecucaoAutomacao {

    private final JdbcTemplate jdbc;
    private final DefinicaoAutomacaoService definicoes;
    private final int maxPorMinuto;
    private final int maxPendentes;

    CriadorExecucaoAutomacao(
            JdbcTemplate jdbc,
            DefinicaoAutomacaoService definicoes,
            @Value("${app.automation.max-executions-per-minute:100}") int maxPorMinuto,
            @Value("${app.automation.max-pending-per-tenant:1000}") int maxPendentes) {
        this.jdbc = jdbc;
        this.definicoes = definicoes;
        this.maxPorMinuto = Math.clamp(maxPorMinuto, 1, 10_000);
        this.maxPendentes = Math.clamp(maxPendentes, 1, 100_000);
    }

    @Transactional
    UUID criar(ConsultaDefinicaoAutomacao.DefinicaoVersionada definicao,
               AutomationTrigger trigger, boolean dryRun, UUID autorId) {
        UUID tenantId = TenantContext.obrigatorio();
        if (!tenantId.equals(trigger.tenantId())) {
            throw new RequisicaoInvalidaException("Tenant do gatilho nao corresponde ao contexto.");
        }

        UUID existente = existente(definicao.definicaoId(), trigger.triggerKey());
        if (existente != null) return existente;

        Pai pai = pai(trigger.causedByExecutionId(), definicao.definicaoId());
        if (pai != null && pai.recursivo()) {
            return criarRejeitada(definicao, trigger, pai, autorId);
        }

        // Serializa criacoes do mesmo tenant para tornar quota e backlog
        // deterministas mesmo com varios produtores concorrentes.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                rs -> { /* o retorno SQL e void; concluir a linha confirma o lock */ },
                tenantId);
        existente = existente(definicao.definicaoId(), trigger.triggerKey());
        if (existente != null) return existente;
        exigirQuota(tenantId);

        AutomationModel.EspecificacaoSnapshot snapshot =
                definicoes.lerSnapshot(definicao.especificacaoJson());
        UUID executionId = UuidV7.gerar();
        UUID rootId = pai == null ? null : pai.rootId();
        Instant limite = Instant.now().plusSeconds(definicao.duracaoMaximaSegundos());
        try {
            jdbc.update("""
                    INSERT INTO automation_execution (
                        id, tenant_id, definition_id, definition_version_id,
                        trigger_key, trigger_type, source_reference_id,
                        root_execution_id, parent_execution_id, dry_run, status,
                        deadline_at, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?)
                    """, executionId, tenantId, definicao.definicaoId(), definicao.versaoId(),
                    trigger.triggerKey(), trigger.triggerType(), trigger.sourceReferenceId(),
                    rootId, trigger.causedByExecutionId(), dryRun,
                    Timestamp.from(limite), autorId, autorId);
            inserirPasso(executionId, definicao.versaoId(), snapshot.passo(snapshot.passoInicial()));
            transicao(executionId, null, "EXECUTION", null, "QUEUED",
                    dryRun ? "DRY_RUN_CREATED" : "TRIGGER_ACCEPTED", "SYSTEM");
            return executionId;
        } catch (DataIntegrityViolationException e) {
            UUID concorrente = existente(definicao.definicaoId(), trigger.triggerKey());
            if (concorrente != null) return concorrente;
            throw e;
        }
    }

    private UUID criarRejeitada(ConsultaDefinicaoAutomacao.DefinicaoVersionada definicao,
                                AutomationTrigger trigger, Pai pai, UUID autorId) {
        UUID existente = existente(definicao.definicaoId(), trigger.triggerKey());
        if (existente != null) return existente;
        UUID id = UuidV7.gerar();
        try {
            jdbc.update("""
                    INSERT INTO automation_execution (
                        id, tenant_id, definition_id, definition_version_id,
                        trigger_key, trigger_type, source_reference_id,
                        root_execution_id, parent_execution_id, dry_run, status,
                        deadline_at, finished_at, failure_code, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, 'REJECTED',
                              now(), now(), 'RECURSION_BLOCKED', ?, ?)
                    """, id, TenantContext.obrigatorio(), definicao.definicaoId(),
                    definicao.versaoId(), trigger.triggerKey(), trigger.triggerType(),
                    trigger.sourceReferenceId(), pai.rootId(),
                    trigger.causedByExecutionId(), autorId, autorId);
            transicao(id, null, "EXECUTION", null, "REJECTED",
                    "RECURSION_BLOCKED", "SYSTEM");
            return id;
        } catch (DataIntegrityViolationException e) {
            UUID concorrente = existente(definicao.definicaoId(), trigger.triggerKey());
            if (concorrente != null) return concorrente;
            throw e;
        }
    }

    private Pai pai(UUID paiId, UUID definicaoAlvo) {
        if (paiId == null) return null;
        List<Pai> encontrados = jdbc.query("""
                WITH RECURSIVE ancestral AS (
                    SELECT id, parent_execution_id, definition_id,
                           COALESCE(root_execution_id, id) AS root_id
                      FROM automation_execution
                     WHERE tenant_id = ? AND id = ?
                    UNION ALL
                    SELECT p.id, p.parent_execution_id, p.definition_id, a.root_id
                      FROM automation_execution p
                      JOIN ancestral a ON p.id = a.parent_execution_id
                     WHERE p.tenant_id = ?
                )
                SELECT min(root_id::text)::uuid AS root_id,
                       bool_or(definition_id = ?) AS recursive
                  FROM ancestral
                HAVING count(*) > 0
                """, (rs, row) -> new Pai(
                rs.getObject("root_id", UUID.class), rs.getBoolean("recursive")),
                TenantContext.obrigatorio(), paiId, TenantContext.obrigatorio(), definicaoAlvo);
        if (encontrados.isEmpty()) {
            throw new RequisicaoInvalidaException("Execucao causadora nao encontrada.");
        }
        return encontrados.getFirst();
    }

    private void exigirQuota(UUID tenantId) {
        Long recentes = jdbc.queryForObject("""
                SELECT count(*) FROM automation_execution
                 WHERE tenant_id = ? AND created_at >= now() - interval '1 minute'
                   AND status <> 'REJECTED'
                """, Long.class, tenantId);
        Long pendentes = jdbc.queryForObject("""
                SELECT count(*) FROM automation_execution
                 WHERE tenant_id = ? AND status IN ('QUEUED', 'RUNNING', 'PAUSED')
                """, Long.class, tenantId);
        if ((recentes != null && recentes >= maxPorMinuto)
                || (pendentes != null && pendentes >= maxPendentes)) {
            throw new QuotaAutomacaoExcedidaException();
        }
    }

    private void inserirPasso(UUID executionId, UUID versionId,
                              AutomationModel.PassoSnapshot passo) {
        jdbc.update("""
                INSERT INTO automation_execution_step (
                    id, tenant_id, execution_id, definition_version_id, step_key,
                    action_type, effect_type, retry_safe, max_attempts
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, execution_id, step_key) DO NOTHING
                """, UuidV7.gerar(), TenantContext.obrigatorio(), executionId, versionId,
                passo.chave(), passo.acao(), passo.efeito().name(),
                passo.repeticaoSegura(), passo.maxTentativas());
    }

    private UUID existente(UUID definicaoId, String chave) {
        return jdbc.query("""
                        SELECT id FROM automation_execution
                         WHERE tenant_id = ? AND definition_id = ? AND trigger_key = ?
                        """, (rs, row) -> rs.getObject(1, UUID.class),
                        TenantContext.obrigatorio(), definicaoId, chave)
                .stream().findFirst().orElse(null);
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

    private record Pai(UUID rootId, boolean recursivo) {
    }
}
