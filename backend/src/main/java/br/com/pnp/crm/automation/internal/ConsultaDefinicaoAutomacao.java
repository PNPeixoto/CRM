package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Le snapshots ja validados; nenhuma consulta usa versao corrente por engano. */
@Component
class ConsultaDefinicaoAutomacao {

    private final JdbcTemplate jdbc;

    ConsultaDefinicaoAutomacao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    List<DefinicaoVersionada> ativasPorGatilho(String gatilho) {
        return jdbc.query("""
                SELECT d.id AS definition_id, v.id AS version_id, v.version_number,
                       v.trigger_type, v.specification::text, v.max_duration_seconds
                  FROM automation_definition d
                  JOIN automation_definition_version v
                    ON v.tenant_id = d.tenant_id AND v.id = d.active_version_id
                 WHERE d.tenant_id = ? AND d.status = 'ACTIVE'
                   AND d.deleted_at IS NULL AND v.trigger_type = ?
                 ORDER BY d.id
                """, (rs, row) -> new DefinicaoVersionada(
                rs.getObject("definition_id", UUID.class),
                rs.getObject("version_id", UUID.class), rs.getInt("version_number"),
                rs.getString("trigger_type"), rs.getString("specification"),
                rs.getInt("max_duration_seconds")), TenantContext.obrigatorio(), gatilho);
    }

    @Transactional(readOnly = true)
    DefinicaoVersionada ativa(UUID definicaoId) {
        return jdbc.query("""
                        SELECT d.id AS definition_id, v.id AS version_id, v.version_number,
                               v.trigger_type, v.specification::text, v.max_duration_seconds
                          FROM automation_definition d
                          JOIN automation_definition_version v
                            ON v.tenant_id = d.tenant_id AND v.id = d.active_version_id
                         WHERE d.tenant_id = ? AND d.id = ? AND d.status = 'ACTIVE'
                           AND d.deleted_at IS NULL
                        """, (rs, row) -> new DefinicaoVersionada(
                        rs.getObject("definition_id", UUID.class),
                        rs.getObject("version_id", UUID.class), rs.getInt("version_number"),
                        rs.getString("trigger_type"), rs.getString("specification"),
                        rs.getInt("max_duration_seconds")),
                        TenantContext.obrigatorio(), definicaoId)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
    }

    @Transactional(readOnly = true)
    DefinicaoVersionada versao(UUID definicaoId, int numero) {
        return jdbc.query("""
                        SELECT d.id AS definition_id, v.id AS version_id, v.version_number,
                               v.trigger_type, v.specification::text, v.max_duration_seconds
                          FROM automation_definition d
                          JOIN automation_definition_version v
                            ON v.tenant_id = d.tenant_id AND v.definition_id = d.id
                         WHERE d.tenant_id = ? AND d.id = ? AND v.version_number = ?
                           AND d.deleted_at IS NULL
                        """, (rs, row) -> new DefinicaoVersionada(
                        rs.getObject("definition_id", UUID.class),
                        rs.getObject("version_id", UUID.class), rs.getInt("version_number"),
                        rs.getString("trigger_type"), rs.getString("specification"),
                        rs.getInt("max_duration_seconds")),
                        TenantContext.obrigatorio(), definicaoId, numero)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
    }

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Automacao");
    }

    record DefinicaoVersionada(
            UUID definicaoId,
            UUID versaoId,
            int numeroVersao,
            String gatilho,
            String especificacaoJson,
            int duracaoMaximaSegundos) {
    }
}
