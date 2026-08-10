package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Superficie administrativa; automacao e recurso coletivo do tenant. */
@RestController
@RequestMapping("/api/automacoes")
class AutomationController {

    private final DefinicaoAutomacaoService definicoes;
    private final MotorAutomacaoService motor;
    private final ControleExecucaoAutomacao execucoes;
    private final Autorizacao autorizacao;

    AutomationController(DefinicaoAutomacaoService definicoes,
                         MotorAutomacaoService motor,
                         ControleExecucaoAutomacao execucoes,
                         Autorizacao autorizacao) {
        this.definicoes = definicoes;
        this.motor = motor;
        this.execucoes = execucoes;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    ResponseEntity<List<AutomationModel.DefinicaoResumo>> listar() {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_READ);
        return ResponseEntity.ok(definicoes.listar());
    }

    @PostMapping
    ResponseEntity<AutomationModel.DefinicaoResumo> criar(
            @Valid @RequestBody AutomationModel.DefinicaoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        AutomationModel.DefinicaoResumo criada = definicoes.criar(request, usuario(jwt));
        return ResponseEntity.created(URI.create("/api/automacoes/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    ResponseEntity<AutomationModel.VersaoResumo> criarVersao(
            @PathVariable UUID id,
            @Valid @RequestBody AutomationModel.NovaVersaoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        return ResponseEntity.ok(definicoes.criarVersao(id, request, usuario(jwt)));
    }

    @GetMapping("/{id}/versoes")
    ResponseEntity<List<AutomationModel.VersaoResumo>> versoes(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_READ);
        return ResponseEntity.ok(definicoes.listarVersoes(id));
    }

    @PostMapping("/{id}/versoes/{versao}/ativar")
    ResponseEntity<AutomationModel.DefinicaoResumo> ativar(
            @PathVariable UUID id, @PathVariable int versao,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        return ResponseEntity.ok(definicoes.ativar(id, versao, usuario(jwt)));
    }

    @PostMapping("/{id}/pausar")
    ResponseEntity<AutomationModel.DefinicaoResumo> pausarDefinicao(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        return ResponseEntity.ok(definicoes.pausar(id, usuario(jwt)));
    }

    @PostMapping("/{id}/executar")
    ResponseEntity<AutomationModel.ExecucaoResumo> executar(
            @PathVariable UUID id,
            @Valid @RequestBody AutomationModel.DisparoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        UUID executionId = motor.dispararManual(id, request.chaveIdempotencia(),
                request.referenciaOrigem(), false, null, usuario(jwt));
        return ResponseEntity.accepted().body(execucoes.buscar(executionId));
    }

    @PostMapping("/{id}/versoes/{versao}/dry-run")
    ResponseEntity<AutomationModel.ExecucaoResumo> simular(
            @PathVariable UUID id, @PathVariable int versao,
            @Valid @RequestBody AutomationModel.DisparoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        UUID executionId = motor.dispararManual(id, request.chaveIdempotencia(),
                request.referenciaOrigem(), true, versao, usuario(jwt));
        return ResponseEntity.accepted().body(execucoes.buscar(executionId));
    }

    @GetMapping("/execucoes")
    ResponseEntity<List<AutomationModel.ExecucaoResumo>> listarExecucoes(
            @RequestParam(required = false) UUID definicaoId) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_READ);
        return ResponseEntity.ok(execucoes.listar(definicaoId));
    }

    @GetMapping("/execucoes/{id}")
    ResponseEntity<AutomationModel.ExecucaoResumo> buscarExecucao(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_READ);
        return ResponseEntity.ok(execucoes.buscar(id));
    }

    @GetMapping("/execucoes/{id}/transicoes")
    ResponseEntity<List<AutomationModel.TransicaoResumo>> transicoes(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_READ);
        return ResponseEntity.ok(execucoes.transicoes(id));
    }

    @PostMapping("/execucoes/{id}/pausar")
    ResponseEntity<AutomationModel.ExecucaoResumo> pausarExecucao(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        return ResponseEntity.ok(execucoes.pausar(id, usuario(jwt)));
    }

    @PostMapping("/execucoes/{id}/retomar")
    ResponseEntity<AutomationModel.ExecucaoResumo> retomar(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        return ResponseEntity.ok(execucoes.retomar(id, usuario(jwt)));
    }

    @PostMapping("/execucoes/{id}/cancelar")
    ResponseEntity<AutomationModel.ExecucaoResumo> cancelar(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUTOMATIONS_WRITE);
        return ResponseEntity.ok(execucoes.cancelar(id, usuario(jwt)));
    }

    private UUID usuario(Jwt jwt) {
        UUID usuario = UUID.fromString(jwt.getSubject());
        if (!TenantContext.atual().isPresent()) {
            throw new IllegalStateException("Tenant ausente em endpoint protegido.");
        }
        return usuario;
    }
}
