package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/relatorios")
class ReportExportController {

    private final ReportMetricCatalog catalogo;
    private final ReportExportService exportacoes;
    private final Autorizacao autorizacao;

    ReportExportController(ReportMetricCatalog catalogo,
                           ReportExportService exportacoes,
                           Autorizacao autorizacao) {
        this.catalogo = catalogo;
        this.exportacoes = exportacoes;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/metricas")
    ResponseEntity<List<ReportMetricCatalog.Definition>> metricas() {
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);
        return ResponseEntity.ok(catalogo.listarAtivas());
    }

    @PostMapping("/exportacoes")
    ResponseEntity<JobResponse> solicitar(
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 120)
            @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String idempotencyKey,
            @Valid @RequestBody SolicitarRequest request) {
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);
        ReportExportService.Job job = exportacoes.solicitar(
                request.reportCode(), request.format(), idempotencyKey,
                autorizacao.usuarioCorrente());
        return ResponseEntity.accepted().body(JobResponse.de(job));
    }

    @GetMapping("/exportacoes/{id}")
    ResponseEntity<JobResponse> consultar(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);
        return ResponseEntity.ok(JobResponse.de(
                exportacoes.consultar(id, autorizacao.usuarioCorrente())));
    }

    @PostMapping("/exportacoes/{id}/cancelamento")
    ResponseEntity<JobResponse> cancelar(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);
        return ResponseEntity.ok(JobResponse.de(
                exportacoes.cancelar(id, autorizacao.usuarioCorrente())));
    }

    @PostMapping("/exportacoes/{id}/url")
    ResponseEntity<ReportExportService.UrlTemporaria> criarUrl(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);
        return ResponseEntity.ok(exportacoes.criarUrl(id, autorizacao.usuarioCorrente()));
    }

    @GetMapping("/exportacoes/{id}/conteudo")
    ResponseEntity<Resource> baixar(
            @PathVariable UUID id,
            @RequestParam long exp,
            @RequestParam @Size(min = 64, max = 64) String sig) {
        autorizacao.exigirNoTenant(Permissao.REPORTS_READ);
        ReportExportService.Download download = exportacoes.baixar(
                id, autorizacao.usuarioCorrente(), exp, sig);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.nomeArquivo() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentLength(download.conteudo().length)
                .body(new ByteArrayResource(download.conteudo()));
    }

    record SolicitarRequest(
            @NotBlank @Pattern(regexp = "^OVERVIEW_V1$") String reportCode,
            @NotBlank @Pattern(regexp = "^CSV$") String format) {
    }

    record JobResponse(UUID id, String reportCode, String format, String status,
                       String metricCatalogVersion, Long byteSize, String failureCode,
                       Instant requestedAt, Instant snapshotAt, Instant completedAt,
                       Instant expiresAt, Instant canceledAt, Instant purgedAt) {
        static JobResponse de(ReportExportService.Job job) {
            return new JobResponse(job.id(), job.reportCode(), job.format(), job.status(),
                    job.metricCatalogVersion(), job.byteSize(), job.failureCode(),
                    job.requestedAt(), job.snapshotAt(), job.completedAt(), job.expiresAt(),
                    job.canceledAt(), job.purgedAt());
        }
    }
}
