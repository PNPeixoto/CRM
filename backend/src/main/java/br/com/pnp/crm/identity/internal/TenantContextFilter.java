package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Preenche o {@link TenantContext} a partir do token já verificado.
 *
 * <p>Roda depois da autenticação, de propósito: o tenant sai do claim
 * {@code tid} de um JWT cuja assinatura o Spring Security já conferiu. Se o
 * tenant viesse antes da verificação, viria de dado não confiável.
 */
class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private static final String PARAMETRO_TENANT_PROIBIDO = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        registrarTentativaDeInformarTenant(request);

        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString(AccessTokenService.CLAIM_TENANT);
            if (tenantId != null) {
                TenantContext.definir(UUID.fromString(tenantId));
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Obrigatório. A thread volta para o pool do Tomcat e atenderá
            // outro usuário; sem esta linha, ela levaria junto o tenant do
            // usuário anterior, e o TenantAwareDataSource o aplicaria.
            TenantContext.limpar();
        }
    }

    /**
     * O tenant nunca vem do cliente. Se aparecer na query string, é ignorado —
     * o que já acontece por construção, já que ninguém lê esse parâmetro — e o
     * registro fica, porque a tentativa em si é o sinal relevante.
     */
    private void registrarTentativaDeInformarTenant(HttpServletRequest request) {
        if (request.getParameter(PARAMETRO_TENANT_PROIBIDO) != null) {
            log.warn("Tentativa de informar tenant pela requisição. uri={} metodo={} remoto={}",
                    request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        }
    }
}
