package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizacao")
class OrganizationController {

    private final OrganizationAccess access;

    OrganizationController(OrganizationAccess access) {
        this.access = access;
    }

    @GetMapping("/contextos")
    ResponseEntity<OrganizationAccess.AccessSummary> contexts(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(access.summarize(
                TenantContext.obrigatorio(), UUID.fromString(jwt.getSubject())));
    }

    /**
     * Permissões do usuário corrente e o alcance de cada uma.
     *
     * <p>Existe para que o menu esconda o que não se pode usar <b>sem</b> que
     * esconder vire a proteção. A decisão continua no backend, em cada
     * endpoint; isto é só a mesma resposta antecipada, para a interface não ter
     * de descobrir por tentativa e erro nem manter uma cópia da regra.
     *
     * <p>Só chegam aqui os códigos que este backend conhece. Devolver o texto
     * cru do banco exportaria como catálogo qualquer permissão inventada em
     * {@code role_permission}, inclusive de módulo que ainda não existe.
     */
    @GetMapping("/permissoes")
    ResponseEntity<Map<String, String>> permissoes(@AuthenticationPrincipal Jwt jwt) {
        Map<String, OrganizationAccess.ScopeType> concedidas = access.permissionScopes(
                TenantContext.obrigatorio(), UUID.fromString(jwt.getSubject()));

        Map<String, String> resposta = new LinkedHashMap<>();
        for (Permissao permissao : Permissao.values()) {
            OrganizationAccess.ScopeType escopo = concedidas.get(permissao.codigo());
            if (escopo != null) {
                resposta.put(permissao.codigo(), escopo.name());
            }
        }
        return ResponseEntity.ok(resposta);
    }
}
