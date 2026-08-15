package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ReportExportService {

    private final JdbcTemplate jdbc;
    private final ReportMetricCatalog catalogo;
    private final ReportSnapshotService snapshots;
    private final ReportExportStorage storage;
    private final ReportExportSigner assinatura;
    private final OrganizationAccess acessos;
    private final AuditTrail audit;
    private final Duration retencao;
    private final Duration validadeLink;

    ReportExportService(
            JdbcTemplate jdbc, ReportMetricCatalog catalogo,
            ReportSnapshotService snapshots, ReportExportStorage storage,
            ReportExportSigner assinatura, OrganizationAccess acessos, AuditTrail audit,
            @Value("${app.report-export.retention-hours:24}") long retencaoHoras,
            @Value("${app.report-export.signed-url-seconds:300}") long validadeLinkSegundos) {
        this.jdbc = jdbc;
        this.catalogo = catalogo;
        this.snapshots = snapshots;
        this.storage = storage;
        this.assinatura = assinatura;
        this.acessos = acessos;
        this.audit = audit;
        this.retencao = Duration.ofHours(Math.clamp(retencaoHoras, 1, 168));
        this.validadeLink = Duration.ofSeconds(Math.clamp(validadeLinkSegundos, 60, 300));
    }

    @Transactional
    public Job solicitar(String reportCode, String format, String idempotencyKey, UUID userId) {
        validarPedido(reportCode, format, idempotencyKey);
        UUID tenantId = TenantContext.obrigatorio();
        String requestHash = sha256(reportCode + "\n" + format);
        UUID novoId = UuidV7.gerar();
        int inseridos = jdbc.update("""
                INSERT INTO report_export_job (
                    id, tenant_id, requested_by, report_code, format,
                    idempotency_key, request_hash, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
                ON CONFLICT (tenant_id, requested_by, idempotency_key) DO NOTHING
                """, novoId, tenantId, userId, reportCode, format,
                idempotencyKey, requestHash);

        Job job = inseridos == 1 ? buscar(novoId, userId, false)
                : buscarPorChave(userId, idempotencyKey);
        if (!requestHash.equals(job.requestHash())) {
            throw ReportExportException.conflitoDeIdempotencia();
        }
        if (inseridos == 1) {
            auditar(AuditTrail.Acao.EXPORT_REQUESTED, AuditTrail.Ator.humano(userId),
                    novoId, AuditTrail.Resultado.SUCCEEDED, AuditTrail.Motivo.EXPORT_REQUESTED);
        }
        return job;
    }

    @Transactional(readOnly = true)
    Job consultar(UUID id, UUID userId) {
        return buscar(id, userId, false);
    }

    @Transactional
    Job cancelar(UUID id, UUID userId) {
        Job atual = buscar(id, userId, true);
        if (List.of("DENIED", "FAILED", "CANCELED", "EXPIRED").contains(atual.status())) {
            return atual;
        }
        int alterados = jdbc.update("""
                UPDATE report_export_job
                   SET status = 'CANCELED', canceled_at = now(),
                       lease_owner = NULL, lease_until = NULL
                 WHERE tenant_id = ? AND id = ? AND requested_by = ?
                   AND status IN ('PENDING', 'PROCESSING', 'COMPLETED')
                """, TenantContext.obrigatorio(), id, userId);
        if (alterados == 1) {
            auditar(AuditTrail.Acao.EXPORT_CANCELED, AuditTrail.Ator.humano(userId), id,
                    AuditTrail.Resultado.SUCCEEDED, AuditTrail.Motivo.EXPORT_CANCELED);
        }
        return buscar(id, userId, false);
    }

    @Transactional(readOnly = true)
    UrlTemporaria criarUrl(UUID id, UUID userId) {
        Job job = buscar(id, userId, false);
        Instant agora = Instant.now();
        exigirDisponivel(job, agora);
        long expira = Math.min(agora.plus(validadeLink).getEpochSecond(),
                job.expiresAt().getEpochSecond());
        if (expira <= agora.getEpochSecond()) throw ReportExportException.indisponivel();
        String sig = assinatura.assinar(TenantContext.obrigatorio(), id, userId, expira);
        return new UrlTemporaria(
                "/api/relatorios/exportacoes/" + id + "/conteudo?exp=" + expira
                        + "&sig=" + sig,
                Instant.ofEpochSecond(expira));
    }

    @Transactional
    Download baixar(UUID id, UUID userId, long expiraEpoch, String sig) {
        UUID tenantId = TenantContext.obrigatorio();
        if (!assinatura.conferir(tenantId, id, userId, expiraEpoch, sig)) {
            throw new AcessoNegadoException();
        }
        Job job = buscar(id, userId, false);
        exigirDisponivel(job, Instant.now());
        byte[] conteudo = storage.ler(job.storageKey(), tenantId, id);
        if (!sha256(conteudo).equals(job.contentSha256())) {
            throw new IllegalStateException("Integridade da exportacao invalida.");
        }
        auditar(AuditTrail.Acao.EXPORT_DOWNLOADED, AuditTrail.Ator.humano(userId), id,
                AuditTrail.Resultado.SUCCEEDED, AuditTrail.Motivo.EXPORT_DOWNLOADED);
        return new Download(conteudo, "visao-geral-" + id + ".csv", "text/csv");
    }

    @Transactional
    public void processar(UUID id) {
        UUID tenantId = TenantContext.obrigatorio();
        Job job = buscarParaWorker(id);
        if (!"PROCESSING".equals(job.status())) return;
        if (!autorizadoNoTenant(tenantId, job.requestedBy())) {
            jdbc.update("""
                    UPDATE report_export_job
                       SET status = 'DENIED', failure_code = 'PERMISSION_REVOKED',
                           lease_owner = NULL, lease_until = NULL
                     WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'
                    """, tenantId, id);
            auditar(AuditTrail.Acao.EXPORT_COMPLETED, AuditTrail.Ator.sistema(), id,
                    AuditTrail.Resultado.DENIED, AuditTrail.Motivo.EXPORT_PERMISSION_REVOKED);
            return;
        }

        ReportExportStorage.Arquivo arquivo = null;
        try {
            List<ReportMetricCatalog.Definition> definicoes = catalogo.paraRelatorio(job.reportCode());
            ReportSnapshotService.Snapshot snapshot = snapshots.capturar();
            byte[] csv = CsvSeguro.gerar(definicoes, snapshot);
            arquivo = storage.armazenar(tenantId, id, csv);
            int alterados = jdbc.update("""
                    UPDATE report_export_job
                       SET status = 'COMPLETED', snapshot_at = ?,
                           metric_catalog_version = ?, storage_key = ?, byte_size = ?,
                           content_sha256 = ?, completed_at = now(), expires_at = ?,
                           lease_owner = NULL, lease_until = NULL, failure_code = NULL
                     WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'
                    """, Timestamp.from(snapshot.capturadoEm()), job.reportCode(),
                    arquivo.storageKey(), arquivo.byteSize(), arquivo.sha256(),
                    Timestamp.from(Instant.now().plus(retencao)), tenantId, id);
            if (alterados != 1) {
                throw new IllegalStateException("Job de exportacao mudou durante a execucao.");
            }
            auditar(AuditTrail.Acao.EXPORT_COMPLETED, AuditTrail.Ator.sistema(), id,
                    AuditTrail.Resultado.SUCCEEDED, AuditTrail.Motivo.EXPORT_COMPLETED);
        } catch (RuntimeException e) {
            if (arquivo != null) storage.remover(arquivo.storageKey());
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(UUID id) {
        int alterados = jdbc.update("""
                UPDATE report_export_job
                   SET status = 'FAILED', failure_code = 'GENERATION_FAILED',
                       lease_owner = NULL, lease_until = NULL
                 WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'
                """, TenantContext.obrigatorio(), id);
        if (alterados == 1) {
            auditar(AuditTrail.Acao.EXPORT_COMPLETED, AuditTrail.Ator.sistema(), id,
                    AuditTrail.Resultado.FAILED, AuditTrail.Motivo.EXPORT_GENERATION_FAILED);
        }
    }

    @Transactional
    public void expurgar(UUID id) {
        Job job = buscarParaWorker(id);
        if (job.storageKey() == null || job.purgedAt() != null) return;
        Instant agora = Instant.now();
        boolean cancelado = "CANCELED".equals(job.status());
        boolean expirado = job.expiresAt() != null && !job.expiresAt().isAfter(agora);
        if (!cancelado && !expirado) return;
        Boolean hold = jdbc.queryForObject("SELECT ha_legal_hold('REPORT_EXPORT', ?, ?)",
                Boolean.class, id, Timestamp.from(agora));
        if (Boolean.TRUE.equals(hold)) {
            if (expirado && !cancelado) {
                jdbc.update("UPDATE report_export_job SET status = 'EXPIRED' WHERE id = ?", id);
            }
            return;
        }
        storage.remover(job.storageKey());
        jdbc.update("""
                UPDATE report_export_job
                   SET status = CASE WHEN status = 'CANCELED' THEN status ELSE 'EXPIRED' END,
                       storage_key = NULL, purged_at = now()
                 WHERE tenant_id = ? AND id = ?
                """, TenantContext.obrigatorio(), id);
    }

    private boolean autorizadoNoTenant(UUID tenantId, UUID userId) {
        try {
            return acessos.permissionScopes(tenantId, userId)
                    .get(Permissao.REPORTS_READ.codigo()) == OrganizationAccess.ScopeType.TENANT;
        } catch (AcessoNegadoException e) {
            return false;
        }
    }

    private Job buscar(UUID id, UUID userId, boolean bloquear) {
        String sufixo = bloquear ? " FOR UPDATE" : "";
        return jdbc.query(SQL_JOB + " AND requested_by = ?" + sufixo,
                        (rs, row) -> mapear(rs), TenantContext.obrigatorio(), id, userId)
                .stream().findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Exportacao"));
    }

    private Job buscarPorChave(UUID userId, String idempotencyKey) {
        return jdbc.query("""
                        SELECT id, tenant_id, requested_by, report_code, format,
                               idempotency_key, request_hash, status, snapshot_at,
                               metric_catalog_version, storage_key, byte_size,
                               content_sha256, failure_code, requested_at, completed_at,
                               expires_at, canceled_at, purged_at
                          FROM report_export_job
                         WHERE tenant_id = ? AND requested_by = ? AND idempotency_key = ?
                        """, (rs, row) -> mapear(rs), TenantContext.obrigatorio(),
                        userId, idempotencyKey)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Replay de exportacao ausente."));
    }

    private Job buscarParaWorker(UUID id) {
        return jdbc.query(SQL_JOB + " FOR UPDATE", (rs, row) -> mapear(rs),
                        TenantContext.obrigatorio(), id)
                .stream().findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Exportacao"));
    }

    private static final String SQL_JOB = """
            SELECT id, tenant_id, requested_by, report_code, format,
                   idempotency_key, request_hash, status, snapshot_at,
                   metric_catalog_version, storage_key, byte_size,
                   content_sha256, failure_code, requested_at, completed_at,
                   expires_at, canceled_at, purged_at
              FROM report_export_job
             WHERE tenant_id = ? AND id = ?
            """;

    private static Job mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Job(
                rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("requested_by", UUID.class), rs.getString("report_code"),
                rs.getString("format"), rs.getString("idempotency_key"),
                rs.getString("request_hash"), rs.getString("status"),
                instante(rs.getTimestamp("snapshot_at")),
                rs.getString("metric_catalog_version"), rs.getString("storage_key"),
                rs.getObject("byte_size", Long.class), rs.getString("content_sha256"),
                rs.getString("failure_code"), instante(rs.getTimestamp("requested_at")),
                instante(rs.getTimestamp("completed_at")), instante(rs.getTimestamp("expires_at")),
                instante(rs.getTimestamp("canceled_at")), instante(rs.getTimestamp("purged_at")));
    }

    private static void validarPedido(String reportCode, String format, String key) {
        if (!ReportMetricCatalog.OVERVIEW_V1.equals(reportCode) || !"CSV".equals(format)) {
            throw new RequisicaoInvalidaException("Relatorio ou formato nao suportado.");
        }
        if (key == null || !key.matches("^[A-Za-z0-9._:-]{8,120}$")) {
            throw new RequisicaoInvalidaException("Chave de idempotencia invalida.");
        }
    }

    private static void exigirDisponivel(Job job, Instant agora) {
        if (!"COMPLETED".equals(job.status()) || job.storageKey() == null
                || job.expiresAt() == null || !job.expiresAt().isAfter(agora)
                || job.purgedAt() != null) {
            throw ReportExportException.indisponivel();
        }
    }

    private void auditar(AuditTrail.Acao acao, AuditTrail.Ator ator, UUID id,
                         AuditTrail.Resultado resultado, AuditTrail.Motivo motivo) {
        audit.registrar(new AuditTrail.Evento(
                acao, ator, AuditTrail.Escopo.tenant(TenantContext.obrigatorio()),
                new AuditTrail.Alvo(AuditTrail.TipoAlvo.EXPORT, id), resultado, motivo));
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel.", e);
        }
    }

    private static Instant instante(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record Job(
            UUID id, UUID tenantId, UUID requestedBy, String reportCode, String format,
            String idempotencyKey, String requestHash, String status, Instant snapshotAt,
            String metricCatalogVersion, String storageKey, Long byteSize,
            String contentSha256, String failureCode, Instant requestedAt,
            Instant completedAt, Instant expiresAt, Instant canceledAt, Instant purgedAt) {
    }

    record UrlTemporaria(String url, Instant expiraEm) {
    }

    record Download(byte[] conteudo, String nomeArquivo, String tipo) {
    }
}
