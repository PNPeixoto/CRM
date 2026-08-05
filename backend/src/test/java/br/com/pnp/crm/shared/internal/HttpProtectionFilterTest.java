package br.com.pnp.crm.shared.internal;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpProtectionFilterTest {

    @Test
    void rejectsDeclaredAndChunkedBodiesBeyondTheLimit() throws Exception {
        HttpProtectionFilter filter = filter(4, 10, 10);
        MockHttpServletRequest declared = request("/api/webhooks/telegram/channel");
        declared.setContent("12345".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse declaredResponse = new MockHttpServletResponse();

        filter.doFilter(declared, declaredResponse, (request, response) -> {
            throw new AssertionError("A cadeia não pode receber corpo excessivo.");
        });
        assertThat(declaredResponse.getStatus()).isEqualTo(413);
        assertThat(declaredResponse.getContentAsString()).contains("PAYLOAD_EXCESSIVO");

        MockHttpServletRequest source = request("/api/webhooks/telegram/channel");
        source.setContent("12345".getBytes(StandardCharsets.UTF_8));
        var chunked = new HttpServletRequestWrapper(source) {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        MockHttpServletResponse chunkedResponse = new MockHttpServletResponse();
        filter.doFilter(chunked, chunkedResponse, (request, response) ->
                request.getReader().readLine());

        assertThat(chunkedResponse.getStatus()).isEqualTo(413);
        assertThat(chunkedResponse.getContentAsString()).contains("PAYLOAD_EXCESSIVO");
    }

    @Test
    void limitsPublicRoutesByOriginAndReturnsRetryAfter() throws Exception {
        HttpProtectionFilter filter = filter(1024, 1, 1);
        AtomicInteger calls = new AtomicInteger();
        FilterChain chain = (request, response) -> calls.incrementAndGet();

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/password-reset/request"), first, chain);
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/password-reset/request"), second, chain);

        assertThat(calls).hasValue(1);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
        assertThat(second.getContentAsString()).contains("LIMITE_DE_TAXA");
    }

    @Test
    void normalizesCorrelationBeforeLoggingAndReturnsItOnSuccess() throws Exception {
        HttpProtectionFilter filter = filter(1024, 10, 10);
        MockHttpServletRequest request = request("/api/contatos");
        request.addHeader("X-Correlation-ID", "entrada\r\nforjada");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            String assigned = (String) req.getAttribute(HttpProtectionFilter.CORRELATION_ATTRIBUTE);
            assertThat(MDC.get("correlationId")).isEqualTo(assigned);
            assertThat(UUID.fromString(assigned)).isNotNull();
        });

        assertThat(UUID.fromString(response.getHeader("X-Correlation-ID"))).isNotNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    private static HttpProtectionFilter filter(long bytes, int publicRate, int webhookRate) {
        return new HttpProtectionFilter(
                JsonMapper.builder().findAndAddModules().build(), bytes, publicRate, webhookRate);
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
