package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/integracoes/http")
class HttpConnectorController {

    private final HttpConnectorService conectores;
    private final HttpConnectorSecretService segredos;
    private final Autorizacao autorizacao;

    HttpConnectorController(HttpConnectorService conectores,
                            HttpConnectorSecretService segredos,
                            Autorizacao autorizacao) {
        this.conectores = conectores;
        this.segredos = segredos;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    ResponseEntity<List<HttpConnectorModel.Resumo>> listar() {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_READ);
        return ResponseEntity.ok(conectores.listar());
    }

    @PostMapping
    ResponseEntity<HttpConnectorModel.Resumo> criar(
            @Valid @RequestBody HttpConnectorModel.ConnectorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_WRITE);
        var criado = conectores.criar(request, usuario(jwt));
        return ResponseEntity.created(
                URI.create("/api/integracoes/http/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    ResponseEntity<HttpConnectorModel.Resumo> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody HttpConnectorModel.ConnectorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_WRITE);
        return ResponseEntity.ok(conectores.atualizar(id, request, usuario(jwt)));
    }

    @PutMapping("/{id}/segredo")
    ResponseEntity<Void> configurarSegredo(
            @PathVariable UUID id,
            @Valid @RequestBody HttpConnectorModel.SecretRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_WRITE);
        segredos.configurar(id, request, usuario(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ativar")
    ResponseEntity<HttpConnectorModel.Resumo> ativar(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_WRITE);
        return ResponseEntity.ok(conectores.mudarStatus(id, "ACTIVE", usuario(jwt)));
    }

    @PostMapping("/{id}/pausar")
    ResponseEntity<HttpConnectorModel.Resumo> pausar(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_WRITE);
        return ResponseEntity.ok(conectores.mudarStatus(id, "PAUSED", usuario(jwt)));
    }

    @GetMapping("/{id}/preview")
    ResponseEntity<HttpConnectorModel.Preview> preview(@PathVariable UUID id) {
        autorizacao.exigirNoTenant(Permissao.INTEGRATIONS_READ);
        return ResponseEntity.ok(conectores.preview(id));
    }

    private static UUID usuario(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
