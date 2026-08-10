package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Fronteira transacional curta; nenhuma conexão fica aberta durante a rede. */
@Component
class HttpConnectorAttemptService {

    private final JdbcTemplate jdbc;

    HttpConnectorAttemptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Estado iniciar(UUID connectorId, UUID executionId, String stepKey,
                   String idempotencyHash, String targetHash) {
        UUID tenantId = TenantContext.obrigatorio();
        jdbc.update("""
                INSERT INTO http_connector_attempt (
                    id, tenant_id, connector_id, automation_execution_id, step_key,
                    idempotency_key_hash, target_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, automation_execution_id, step_key) DO NOTHING
                """, UuidV7.gerar(), tenantId, connectorId, executionId, stepKey,
                idempotencyHash, targetHash);
        Estado atual = jdbc.query("""
                        SELECT connector_id, idempotency_key_hash, target_hash, status
                          FROM http_connector_attempt
                         WHERE tenant_id = ? AND automation_execution_id = ? AND step_key = ?
                         FOR UPDATE
                        """, (rs, row) -> new Estado(
                        rs.getObject("connector_id", UUID.class),
                        rs.getString("idempotency_key_hash"), rs.getString("target_hash"),
                        rs.getString("status")), tenantId, executionId, stepKey)
                .stream().findFirst().orElseThrow();
        if (!atual.connectorId().equals(connectorId)
                || !atual.idempotencyHash().equals(idempotencyHash)
                || !atual.targetHash().equals(targetHash)) {
            throw new IllegalStateException("Tentativa HTTP divergiu da versao da automacao.");
        }
        if (!atual.status().equals("SUCCEEDED")) {
            jdbc.update("""
                    UPDATE http_connector_attempt
                       SET status = 'STARTED', http_status = NULL, failure_code = NULL,
                           response_bytes = NULL, duration_ms = NULL, completed_at = NULL
                     WHERE tenant_id = ? AND automation_execution_id = ? AND step_key = ?
                    """, tenantId, executionId, stepKey);
        }
        return atual;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void concluir(UUID executionId, String stepKey, TransportHttpSeguro.Resposta resposta) {
        String status = switch (resposta.tipo()) {
            case SUCESSO -> "SUCCEEDED";
            case TRANSITORIA -> "TRANSIENT_FAILURE";
            case PERMANENTE -> "PERMANENT_FAILURE";
        };
        jdbc.update("""
                UPDATE http_connector_attempt
                   SET status = ?, http_status = NULLIF(?, 0), failure_code = ?,
                       response_bytes = ?, duration_ms = ?, completed_at = now()
                 WHERE tenant_id = ? AND automation_execution_id = ? AND step_key = ?
                """, status, resposta.statusHttp(), resposta.codigo(),
                resposta.respostaBytes(), resposta.duracaoMs(), TenantContext.obrigatorio(),
                executionId, stepKey);
    }

    record Estado(UUID connectorId, String idempotencyHash,
                  String targetHash, String status) {
    }
}
