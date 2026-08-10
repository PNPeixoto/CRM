package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class HttpConnectorService {

    private final JdbcTemplate jdbc;
    private final ValidadorConectorHttp validador;
    private final AuditTrail audit;

    HttpConnectorService(JdbcTemplate jdbc, ValidadorConectorHttp validador,
                         AuditTrail audit) {
        this.jdbc = jdbc;
        this.validador = validador;
        this.audit = audit;
    }

    @Transactional
    HttpConnectorModel.Resumo criar(HttpConnectorModel.ConnectorRequest request,
                                     UUID autorId) {
        ValidadorConectorHttp.ConfiguracaoValidada c = validar(request);
        UUID id = UuidV7.gerar();
        try {
            jdbc.update("""
                    INSERT INTO http_connector (
                        id, tenant_id, code, name, http_method, origin,
                        path_template, body_template, content_type,
                        idempotency_header, timeout_ms, max_response_bytes,
                        requests_per_minute, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'application/json', ?, ?, ?, ?, ?, ?)
                    """, id, TenantContext.obrigatorio(), c.codigo(), c.nome(), c.metodo(),
                    c.origem(), c.caminho(), c.corpo(), c.idempotencia(), c.timeoutMs(),
                    c.maxRespostaBytes(), c.requisicoesPorMinuto(), autorId, autorId);
        } catch (DataIntegrityViolationException e) {
            throw new RequisicaoInvalidaException("Ja existe um conector com esse codigo.");
        }
        auditarConfiguracao(id, autorId, AuditTrail.Motivo.CONNECTOR_CREATED);
        return buscar(id);
    }

    @Transactional
    HttpConnectorModel.Resumo atualizar(UUID id,
                                         HttpConnectorModel.ConnectorRequest request,
                                         UUID autorId) {
        ValidadorConectorHttp.ConfiguracaoValidada c = validar(request);
        validarConflitoComCredencial(id, c.idempotencia());
        try {
            int alteradas = jdbc.update("""
                    UPDATE http_connector
                       SET code = ?, name = ?, http_method = ?, origin = ?,
                           path_template = ?, body_template = ?,
                           idempotency_header = ?, timeout_ms = ?,
                           max_response_bytes = ?, requests_per_minute = ?, updated_by = ?
                     WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                       AND status <> 'ARCHIVED'
                    """, c.codigo(), c.nome(), c.metodo(), c.origem(), c.caminho(),
                    c.corpo(), c.idempotencia(), c.timeoutMs(), c.maxRespostaBytes(),
                    c.requisicoesPorMinuto(), autorId, TenantContext.obrigatorio(), id);
            if (alteradas == 0) throw naoEncontrado();
        } catch (DataIntegrityViolationException e) {
            throw new RequisicaoInvalidaException("Ja existe um conector com esse codigo.");
        }
        auditarConfiguracao(id, autorId, AuditTrail.Motivo.CONNECTOR_UPDATED);
        return buscar(id);
    }

    @Transactional
    HttpConnectorModel.Resumo mudarStatus(UUID id, String status, UUID autorId) {
        int alteradas = jdbc.update("""
                UPDATE http_connector SET status = ?, updated_by = ?
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                   AND status <> 'ARCHIVED'
                """, status, autorId, TenantContext.obrigatorio(), id);
        if (alteradas == 0) throw naoEncontrado();
        auditarConfiguracao(id, autorId, AuditTrail.Motivo.CONNECTOR_STATUS_CHANGED);
        return buscar(id);
    }

    @Transactional(readOnly = true)
    List<HttpConnectorModel.Resumo> listar() {
        return jdbc.query(consultaResumo() + " ORDER BY c.name, c.id",
                (rs, row) -> resumo(rs), TenantContext.obrigatorio());
    }

    @Transactional(readOnly = true)
    HttpConnectorModel.Resumo buscar(UUID id) {
        return jdbc.query(consultaResumo() + " AND c.id = ?",
                        (rs, row) -> resumo(rs), TenantContext.obrigatorio(), id)
                .stream().findFirst().orElseThrow(HttpConnectorService::naoEncontrado);
    }

    @Transactional(readOnly = true)
    HttpConnectorModel.Preview preview(UUID id) {
        HttpConnectorModel.Resumo c = buscar(id);
        return new HttpConnectorModel.Preview(
                c.metodo(), c.origem() + c.caminhoTemplate(), c.contentType(),
                c.cabecalhoIdempotencia(), c.credencialConfigurada(),
                c.cabecalhoAutenticacao());
    }

    @Transactional(readOnly = true)
    HttpConnectorModel.Configuracao carregarAtivo(UUID id) {
        return jdbc.query("""
                        SELECT id, tenant_id, status, http_method, origin, path_template,
                               body_template, content_type, idempotency_header, timeout_ms,
                               max_response_bytes, requests_per_minute
                          FROM http_connector
                         WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                        """, (rs, row) -> new HttpConnectorModel.Configuracao(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class), rs.getString("status"),
                        rs.getString("http_method"), rs.getString("origin"),
                        rs.getString("path_template"), rs.getString("body_template"),
                        rs.getString("content_type"), rs.getString("idempotency_header"),
                        rs.getInt("timeout_ms"), rs.getInt("max_response_bytes"),
                        rs.getInt("requests_per_minute")),
                        TenantContext.obrigatorio(), id)
                .stream().findFirst().orElseThrow(HttpConnectorService::naoEncontrado);
    }

    @Transactional(readOnly = true)
    void exigirExistente(UUID id) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM http_connector
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                """, Long.class, TenantContext.obrigatorio(), id);
        if (total == null || total == 0) throw naoEncontrado();
    }

    private void validarConflitoComCredencial(UUID id, String idempotencia) {
        if (idempotencia == null) return;
        Long conflitos = jdbc.queryForObject("""
                SELECT count(*)
                  FROM http_connector_secret
                 WHERE tenant_id = ? AND connector_id = ?
                   AND header_name IS NOT NULL
                   AND lower(header_name) = lower(?)
                """, Long.class, TenantContext.obrigatorio(), id, idempotencia);
        if (conflitos != null && conflitos > 0) {
            throw new RequisicaoInvalidaException(
                    "Cabecalhos de autenticacao e idempotencia precisam ser distintos.");
        }
    }

    private ValidadorConectorHttp.ConfiguracaoValidada validar(
            HttpConnectorModel.ConnectorRequest request) {
        try {
            return validador.validar(request);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException("Configuracao do conector HTTP invalida.");
        }
    }

    private void auditarConfiguracao(UUID id, UUID autorId, AuditTrail.Motivo motivo) {
        audit.registrar(new AuditTrail.Evento(
                AuditTrail.Acao.SENSITIVE_CONFIGURATION_CHANGED,
                AuditTrail.Ator.humano(autorId),
                AuditTrail.Escopo.tenant(TenantContext.obrigatorio()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.HTTP_CONNECTOR, id),
                AuditTrail.Resultado.SUCCEEDED, motivo));
    }

    private static String consultaResumo() {
        return """
                SELECT c.id, c.code, c.name, c.status, c.http_method, c.origin,
                       c.path_template, c.content_type, c.idempotency_header,
                       c.timeout_ms, c.max_response_bytes, c.requests_per_minute,
                       c.created_at, c.updated_at, (s.id IS NOT NULL) AS tem_segredo,
                       CASE WHEN s.auth_type = 'BEARER' THEN 'Authorization'
                            ELSE s.header_name END AS auth_header
                  FROM http_connector c
             LEFT JOIN http_connector_secret s
                    ON s.tenant_id = c.tenant_id AND s.connector_id = c.id
                 WHERE c.tenant_id = ? AND c.deleted_at IS NULL
                """;
    }

    private static HttpConnectorModel.Resumo resumo(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new HttpConnectorModel.Resumo(
                rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("name"), rs.getString("status"),
                rs.getString("http_method"), rs.getString("origin"),
                rs.getString("path_template"), rs.getString("content_type"),
                rs.getString("idempotency_header"), rs.getInt("timeout_ms"),
                rs.getInt("max_response_bytes"), rs.getInt("requests_per_minute"),
                rs.getBoolean("tem_segredo"), rs.getString("auth_header"),
                instante(rs.getTimestamp("created_at")),
                instante(rs.getTimestamp("updated_at")));
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Conector HTTP");
    }
}
