package br.com.pnp.crm.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Adapta o CSRF do Spring Security ao padrão que uma SPA consegue usar.
 *
 * <p>O Spring guarda no cookie um token com máscara XOR (defesa contra BREACH).
 * O JavaScript lê esse cookie e o devolve como cabeçalho — mas devolve o valor
 * mascarado, e o resolvedor padrão espera o valor bruto quando vem por
 * cabeçalho. O resultado é 403 em toda mutação.
 *
 * <p>Este handler resolve escolhendo o decodificador conforme a origem: bruto
 * quando o token chega por cabeçalho, XOR quando chega por parâmetro de
 * formulário. Copiado do FinUp sem alteração — o problema é idêntico.
 */
class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler bruto = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // Força a materialização do token para que o cookie seja escrito nesta
        // resposta, e não só na seguinte.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return bruto.resolveCsrfTokenValue(request, csrfToken);
        }
        return xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
