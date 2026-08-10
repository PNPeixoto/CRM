package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auditoria")
class AuditController {

    private final AuditQueryService queries;
    private final Autorizacao autorizacao;

    AuditController(AuditQueryService queries, Autorizacao autorizacao) {
        this.queries = queries;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    ResponseEntity<AuditQueryService.Pagina> listar(
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) String resultado,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") int limite,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.AUDIT_READ);
        return ResponseEntity.ok(queries.listar(
                UUID.fromString(jwt.getSubject()), acao, resultado, cursor, limite));
    }
}
