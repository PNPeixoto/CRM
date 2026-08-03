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

/** Preenche o tenant somente a partir de um JWT já verificado. */
class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);
    private static final String PARAMETER_TENANT = "tenantId";
    private static final String PARAMETER_TENANT_SNAKE_CASE = "tenant_id";
    private static final String HEADER_TENANT = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (triedToProvideTenant(request)) {
            log.warn("Tentativa de informar tenant pela requisição. uri={} metodo={} remoto={}",
                    request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "O tenant é determinado pela sessão autenticada.");
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString(AccessTokenService.CLAIM_TENANT);
            if (tenantId != null) {
                TenantContext.definir(UUID.fromString(tenantId));
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // A thread volta ao pool do servidor e não pode carregar o tenant
            // da requisição anterior.
            TenantContext.limpar();
        }
    }

    private boolean triedToProvideTenant(HttpServletRequest request) {
        return request.getParameter(PARAMETER_TENANT) != null
                || request.getParameter(PARAMETER_TENANT_SNAKE_CASE) != null
                || request.getHeader(HEADER_TENANT) != null
                || request.getHeader(PARAMETER_TENANT) != null
                || request.getHeader(PARAMETER_TENANT_SNAKE_CASE) != null;
    }
}
