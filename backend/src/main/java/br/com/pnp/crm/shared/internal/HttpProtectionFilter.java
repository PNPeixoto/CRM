package br.com.pnp.crm.shared.internal;

import br.com.pnp.crm.shared.api.ErroResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Proteções HTTP que precisam valer antes da autenticação e dos controllers:
 * correlação, limite de corpo e taxa por origem nas portas públicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class HttpProtectionFilter extends OncePerRequestFilter {

    static final String CORRELATION_HEADER = "X-Correlation-ID";
    static final String CORRELATION_ATTRIBUTE = HttpProtectionFilter.class.getName() + ".correlationId";
    private static final long JANELA_MILLIS = 60_000L;
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/mfa/enrollment",
            "/api/auth/mfa/activation",
            "/api/auth/password-reset/request",
            "/api/auth/password-reset/confirm");

    private final ObjectMapper json;
    private final long maxRequestBytes;
    private final int publicRequestsPerMinute;
    private final int webhookRequestsPerMinute;
    private final ConcurrentHashMap<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final AtomicLong rateChecks = new AtomicLong();

    HttpProtectionFilter(
            ObjectMapper json,
            @Value("${app.http.max-request-bytes:1048576}") long maxRequestBytes,
            @Value("${app.http.public-requests-per-minute:120}") int publicRequestsPerMinute,
            @Value("${app.http.webhook-requests-per-minute:60}") int webhookRequestsPerMinute) {
        if (maxRequestBytes < 1 || publicRequestsPerMinute < 1 || webhookRequestsPerMinute < 1) {
            throw new IllegalArgumentException("Limites HTTP devem ser positivos.");
        }
        this.json = json;
        this.maxRequestBytes = maxRequestBytes;
        this.publicRequestsPerMinute = publicRequestsPerMinute;
        this.webhookRequestsPerMinute = webhookRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = normalizedCorrelation(request.getHeader(CORRELATION_HEADER));
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        MDC.put("correlationId", correlationId);

        try {
            RatePolicy ratePolicy = ratePolicy(request);
            if (ratePolicy != null && !consume(request.getRemoteAddr(), ratePolicy)) {
                response.setHeader("Retry-After", "60");
                writeProblem(request, response, 429, "Muitas requisições",
                        "LIMITE_DE_TAXA", "Tente novamente mais tarde.", correlationId);
                return;
            }

            if (request.getContentLengthLong() > maxRequestBytes) {
                writePayloadTooLarge(request, response, correlationId);
                return;
            }

            HttpServletRequest limited = new LimitedRequest(request, maxRequestBytes);
            try {
                chain.doFilter(limited, response);
            } catch (Exception exception) {
                if (containsPayloadLimit(exception) && !response.isCommitted()) {
                    response.resetBuffer();
                    response.setHeader(CORRELATION_HEADER, correlationId);
                    writePayloadTooLarge(request, response, correlationId);
                    return;
                }
                throw exception;
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void writePayloadTooLarge(HttpServletRequest request, HttpServletResponse response,
                                      String correlationId) throws IOException {
        writeProblem(request, response, 413, "Corpo muito grande", "PAYLOAD_EXCESSIVO",
                "O corpo da requisição excede o limite permitido.", correlationId);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, int status,
                              String title, String code, String detail, String correlationId)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getWriter(), ErroResponse.de(
                status, title, code, detail, correlationId, URI.create(request.getRequestURI())));
    }

    private RatePolicy ratePolicy(HttpServletRequest request) {
        if ("OPTIONS".equals(request.getMethod())) return null;
        String path = request.getRequestURI();
        if (path.startsWith("/api/webhooks/")) {
            return new RatePolicy("webhook", webhookRequestsPerMinute);
        }
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            return new RatePolicy("public-auth", publicRequestsPerMinute);
        }
        return null;
    }

    private boolean consume(String origin, RatePolicy policy) {
        long now = System.currentTimeMillis();
        String key = policy.category() + ':' + (origin == null ? "unknown" : origin);
        AtomicBoolean allowed = new AtomicBoolean();
        rateWindows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAt() >= JANELA_MILLIS) {
                allowed.set(true);
                return new RateWindow(now, 1);
            }
            int next = current.count() + 1;
            allowed.set(next <= policy.limit());
            return new RateWindow(current.startedAt(), next);
        });

        if (rateChecks.incrementAndGet() % 1_000 == 0) {
            rateWindows.entrySet().removeIf(
                    entry -> now - entry.getValue().startedAt() >= 2 * JANELA_MILLIS);
        }
        return allowed.get();
    }

    static String correlation(HttpServletRequest request) {
        Object assigned = request.getAttribute(CORRELATION_ATTRIBUTE);
        return assigned instanceof String value ? value
                : normalizedCorrelation(request.getHeader(CORRELATION_HEADER));
    }

    private static String normalizedCorrelation(String received) {
        if (received != null) {
            try {
                return UUID.fromString(received).toString();
            } catch (IllegalArgumentException ignored) {
                // Entrada não confiável: substitui sem refletir nem registrar.
            }
        }
        return UUID.randomUUID().toString();
    }

    private static boolean containsPayloadLimit(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PayloadLimitException) return true;
            current = current.getCause();
        }
        return false;
    }

    private record RatePolicy(String category, int limit) { }
    private record RateWindow(long startedAt, int count) { }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;

        private LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedInputStream(super.getInputStream(), limit);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long bytesRead;

        private LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) count(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int amount = delegate.read(bytes, offset, length);
            if (amount > 0) count(amount);
            return amount;
        }

        private void count(int amount) throws PayloadLimitException {
            bytesRead += amount;
            if (bytesRead > limit) throw new PayloadLimitException();
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }

    private static final class PayloadLimitException extends IOException { }
}
