package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.Permissao;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/empresa")
class TenantPresentationController {

    private final TenantPresentationService presentation;
    private final Autorizacao autorizacao;

    TenantPresentationController(TenantPresentationService presentation,
                                 Autorizacao autorizacao) {
        this.presentation = presentation;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/apresentacao")
    ResponseEntity<TenantProfileDtos.ApresentacaoResponse> obter() {
        // Metadado de bootstrap necessário a todo membro do tenant. Não é
        // permissão de mutação e não concede acesso a nenhuma ação do menu.
        autorizacao.exigirMembershipVigente();
        return ResponseEntity.ok(TenantProfileDtos.ApresentacaoResponse.de(presentation.atual()));
    }

    @PutMapping("/perfil-inicial")
    ResponseEntity<TenantProfileDtos.ApresentacaoResponse> salvar(
            @Valid @RequestBody TenantProfileDtos.PerfilInicialRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        autorizacao.exigirNoTenant(Permissao.ORGANIZATION_MANAGE);
        return ResponseEntity.ok(TenantProfileDtos.ApresentacaoResponse.de(
                presentation.concluirPerfilInicial(
                        request.segmento(), UUID.fromString(jwt.getSubject()))));
    }
}
