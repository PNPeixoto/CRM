package br.com.pnp.crm.identity.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Garante que o cookie de CSRF exista antes da primeira mutação.
 *
 * <p>O {@code CsrfToken} do Spring é preguiçoso: o cookie só é escrito quando
 * alguém o consulta. Numa SPA, a primeira requisição costuma ser um GET, e sem
 * este filtro o cookie só apareceria depois do primeiro POST — que já teria
 * falhado com 403.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    private final CookieCsrfTokenRepository repository;

    CsrfCookieFilter(CookieCsrfTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        if (token != null) {
            token.getToken();
        }
        if ("GET".equals(request.getMethod()) && repository.loadToken(request) == null) {
            repository.saveToken(repository.generateToken(request), request, response);
        }
        filterChain.doFilter(request, response);
    }
}
