package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Cria snapshots imutaveis e controla qual versao recebe novos gatilhos. */
@Service
class DefinicaoAutomacaoService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ValidadorDeAutomacao validador;

    DefinicaoAutomacaoService(JdbcTemplate jdbc, ObjectMapper json,
                              ValidadorDeAutomacao validador) {
        this.jdbc = jdbc;
        this.json = json;
        this.validador = validador;
    }

    @Transactional
    AutomationModel.DefinicaoResumo criar(AutomationModel.DefinicaoRequest request,
                                           UUID autorId) {
        ValidadorDeAutomacao.Resultado validada = validador.validar(request.especificacao());
        SnapshotSerializado serializado = serializar(validada.snapshot());
        UUID tenantId = TenantContext.obrigatorio();
        UUID definicaoId = UuidV7.gerar();
        UUID versaoId = UuidV7.gerar();
        try {
            jdbc.update("""
                    INSERT INTO automation_definition (
                        id, tenant_id, code, name, status, current_version,
                        concurrency_limit, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, 'DRAFT', 1, ?, ?, ?)
                    """, definicaoId, tenantId, request.codigo(), request.nome().trim(),
                    request.limiteConcorrencia(), autorId, autorId);
            inserirVersao(tenantId, definicaoId, versaoId, 1, validada,
                    serializado, autorId);
        } catch (DataIntegrityViolationException e) {
            throw new RequisicaoInvalidaException("Ja existe uma automacao com esse codigo.");
        }
        return buscar(definicaoId);
    }

    @Transactional
    AutomationModel.VersaoResumo criarVersao(UUID definicaoId,
                                              AutomationModel.NovaVersaoRequest request,
                                              UUID autorId) {
        ValidadorDeAutomacao.Resultado validada = validador.validar(request.especificacao());
        SnapshotSerializado serializado = serializar(validada.snapshot());
        UUID tenantId = TenantContext.obrigatorio();
        Integer atual = jdbc.query("""
                        SELECT current_version FROM automation_definition
                         WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                         FOR UPDATE
                        """, (rs, row) -> rs.getInt(1), tenantId, definicaoId)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
        int proxima = atual + 1;
        UUID versaoId = UuidV7.gerar();
        inserirVersao(tenantId, definicaoId, versaoId, proxima, validada,
                serializado, autorId);
        jdbc.update("""
                UPDATE automation_definition
                   SET name = ?, concurrency_limit = ?, current_version = ?, updated_by = ?
                 WHERE tenant_id = ? AND id = ?
                """, request.nome().trim(), request.limiteConcorrencia(), proxima,
                autorId, tenantId, definicaoId);
        return buscarVersao(definicaoId, proxima);
    }

    @Transactional
    AutomationModel.DefinicaoResumo ativar(UUID definicaoId, int numeroVersao,
                                           UUID autorId) {
        UUID tenantId = TenantContext.obrigatorio();
        UUID versaoId = jdbc.query("""
                        SELECT id FROM automation_definition_version
                         WHERE tenant_id = ? AND definition_id = ? AND version_number = ?
                        """, (rs, row) -> rs.getObject(1, UUID.class),
                        tenantId, definicaoId, numeroVersao)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
        int alteradas = jdbc.update("""
                UPDATE automation_definition
                   SET status = 'ACTIVE', active_version_id = ?, updated_by = ?
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                """, versaoId, autorId, tenantId, definicaoId);
        if (alteradas == 0) throw naoEncontrada();
        return buscar(definicaoId);
    }

    @Transactional
    AutomationModel.DefinicaoResumo pausar(UUID definicaoId, UUID autorId) {
        int alteradas = jdbc.update("""
                UPDATE automation_definition
                   SET status = CASE WHEN status = 'ARCHIVED' THEN status ELSE 'PAUSED' END,
                       updated_by = ?
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                """, autorId, TenantContext.obrigatorio(), definicaoId);
        if (alteradas == 0) throw naoEncontrada();
        return buscar(definicaoId);
    }

    @Transactional(readOnly = true)
    List<AutomationModel.DefinicaoResumo> listar() {
        return jdbc.query("""
                SELECT d.id, d.code, d.name, d.status, d.current_version,
                       av.version_number, d.concurrency_limit, d.created_at, d.updated_at
                  FROM automation_definition d
             LEFT JOIN automation_definition_version av
                    ON av.tenant_id = d.tenant_id AND av.id = d.active_version_id
                 WHERE d.tenant_id = ? AND d.deleted_at IS NULL
                 ORDER BY d.name, d.id
                """, (rs, row) -> new AutomationModel.DefinicaoResumo(
                rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getString("status"),
                rs.getInt("current_version"),
                rs.getObject("version_number", Integer.class),
                rs.getInt("concurrency_limit"), instante(rs.getTimestamp("created_at")),
                instante(rs.getTimestamp("updated_at"))), TenantContext.obrigatorio());
    }

    @Transactional(readOnly = true)
    AutomationModel.DefinicaoResumo buscar(UUID definicaoId) {
        return jdbc.query("""
                        SELECT d.id, d.code, d.name, d.status, d.current_version,
                               av.version_number, d.concurrency_limit,
                               d.created_at, d.updated_at
                          FROM automation_definition d
                     LEFT JOIN automation_definition_version av
                            ON av.tenant_id = d.tenant_id AND av.id = d.active_version_id
                         WHERE d.tenant_id = ? AND d.id = ? AND d.deleted_at IS NULL
                        """, (rs, row) -> new AutomationModel.DefinicaoResumo(
                        rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("status"),
                        rs.getInt("current_version"),
                        rs.getObject("version_number", Integer.class),
                        rs.getInt("concurrency_limit"), instante(rs.getTimestamp("created_at")),
                        instante(rs.getTimestamp("updated_at"))),
                        TenantContext.obrigatorio(), definicaoId)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
    }

    @Transactional(readOnly = true)
    List<AutomationModel.VersaoResumo> listarVersoes(UUID definicaoId) {
        exigirDefinicao(definicaoId);
        return jdbc.query("""
                SELECT id, version_number, trigger_type, specification_sha256,
                       max_duration_seconds, step_count, branch_count, created_at
                  FROM automation_definition_version
                 WHERE tenant_id = ? AND definition_id = ?
                 ORDER BY version_number DESC
                """, (rs, row) -> versao(rs.getObject("id", UUID.class),
                rs.getInt("version_number"), rs.getString("trigger_type"),
                rs.getString("specification_sha256"), rs.getInt("max_duration_seconds"),
                rs.getInt("step_count"), rs.getInt("branch_count"),
                instante(rs.getTimestamp("created_at"))),
                TenantContext.obrigatorio(), definicaoId);
    }

    @Transactional(readOnly = true)
    AutomationModel.VersaoResumo buscarVersao(UUID definicaoId, int numero) {
        return jdbc.query("""
                        SELECT id, version_number, trigger_type, specification_sha256,
                               max_duration_seconds, step_count, branch_count, created_at
                          FROM automation_definition_version
                         WHERE tenant_id = ? AND definition_id = ? AND version_number = ?
                        """, (rs, row) -> versao(rs.getObject("id", UUID.class),
                        rs.getInt("version_number"), rs.getString("trigger_type"),
                        rs.getString("specification_sha256"),
                        rs.getInt("max_duration_seconds"), rs.getInt("step_count"),
                        rs.getInt("branch_count"), instante(rs.getTimestamp("created_at"))),
                        TenantContext.obrigatorio(), definicaoId, numero)
                .stream().findFirst().orElseThrow(() -> naoEncontrada());
    }

    AutomationModel.EspecificacaoSnapshot lerSnapshot(String valor) {
        try {
            return json.readValue(valor, AutomationModel.EspecificacaoSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Snapshot de automacao invalido.", e);
        }
    }

    private void inserirVersao(UUID tenantId, UUID definicaoId, UUID versaoId, int numero,
                               ValidadorDeAutomacao.Resultado validada,
                               SnapshotSerializado serializado, UUID autorId) {
        jdbc.update("""
                INSERT INTO automation_definition_version (
                    id, tenant_id, definition_id, version_number, trigger_type,
                    specification, specification_sha256, max_duration_seconds,
                    step_count, branch_count, created_by
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                """, versaoId, tenantId, definicaoId, numero,
                validada.snapshot().gatilho(), serializado.json(), serializado.sha256(),
                validada.snapshot().duracaoMaximaSegundos(),
                validada.snapshot().passos().size(), validada.ramificacoes(), autorId);
    }

    private SnapshotSerializado serializar(AutomationModel.EspecificacaoSnapshot snapshot) {
        try {
            String valor = json.writeValueAsString(snapshot);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(
                    digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
            return new SnapshotSerializado(valor, hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel serializar a automacao.", e);
        }
    }

    private void exigirDefinicao(UUID definicaoId) {
        Long quantidade = jdbc.queryForObject("""
                SELECT count(*) FROM automation_definition
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                """, Long.class, TenantContext.obrigatorio(), definicaoId);
        if (quantidade == null || quantidade == 0) throw naoEncontrada();
    }

    private static AutomationModel.VersaoResumo versao(
            UUID id, int numero, String gatilho, String hash, int duracao,
            int passos, int ramos, Instant criadaEm) {
        return new AutomationModel.VersaoResumo(
                id, numero, gatilho, hash, duracao, passos, ramos, criadaEm);
    }

    private static Instant instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Automacao");
    }

    private record SnapshotSerializado(String json, String sha256) {
    }
}
