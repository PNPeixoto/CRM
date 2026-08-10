package br.com.pnp.crm.integration.internal;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;

/** Cliente por chamada: sem cookies, redirects, retry automático ou descompressão. */
@Component
class ClienteHttpSeguro implements TransportHttpSeguro {

    private final PoliticaAntiSsrf politica;
    private final HttpHost proxy;

    @Autowired
    ClienteHttpSeguro(
            PoliticaAntiSsrf politica,
            @Value("${app.http-connector.require-egress-proxy:false}") boolean exigirProxy,
            @Value("${app.http-connector.egress-proxy-url:}") String proxyUrl) {
        this.politica = politica;
        this.proxy = validarProxy(proxyUrl);
        if (exigirProxy && this.proxy == null) {
            throw new IllegalStateException(
                    "HTTP_CONNECTOR_EGRESS_PROXY_URL e obrigatoria neste ambiente.");
        }
    }

    ClienteHttpSeguro(PoliticaAntiSsrf politica) {
        this.politica = politica;
        this.proxy = null;
    }

    @Override
    public Resposta enviar(Requisicao requisicao) {
        long inicio = System.nanoTime();
        PoliticaAntiSsrf.DestinoAprovado destino = politica.aprovar(requisicao.uri());
        Timeout timeout = Timeout.ofMilliseconds(requisicao.timeoutMs());
        DnsResolver dnsFixo = resolverDeConexao(destino);
        var conexoes = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dnsFixo)
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(timeout)
                        .setSocketTimeout(timeout)
                        .build())
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setSupportedProtocols("TLSv1.3", "TLSv1.2")
                        .build())
                .build();

        var builder = HttpClients.custom()
                .setConnectionManager(conexoes)
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAutomaticRetries()
                .disableContentCompression()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(timeout)
                        .setResponseTimeout(timeout)
                        .build());
        if (proxy != null) builder.setProxy(proxy);

        HttpUriRequestBase request = new HttpUriRequestBase(
                requisicao.metodo(), requisicao.uri());
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "crm-pnp-http-connector/1");
        if (requisicao.cabecalhoIdempotencia() != null) {
            request.setHeader(requisicao.cabecalhoIdempotencia(),
                    requisicao.chaveIdempotencia());
        }
        if (requisicao.cabecalhoAutenticacao() != null) {
            request.setHeader(requisicao.cabecalhoAutenticacao(),
                    requisicao.valorAutenticacao());
        }
        if (requisicao.corpo() != null) {
            request.setEntity(new StringEntity(
                    requisicao.corpo(), ContentType.APPLICATION_JSON));
        }

        try (CloseableHttpClient client = builder.build()) {
            return client.execute(request, response -> {
                int status = response.getCode();
                int bytes = consumirLimitado(response.getEntity(),
                        requisicao.maxRespostaBytes());
                long duracao = duracaoMs(inicio);
                if (status >= 200 && status < 300) {
                    return new Resposta(Tipo.SUCESSO, status, bytes, duracao, null);
                }
                if (status == 408 || status == 425 || status == 429 || status >= 500) {
                    return new Resposta(Tipo.TRANSITORIA, status, bytes, duracao,
                            "HTTP_REMOTE_TRANSIENT");
                }
                return new Resposta(Tipo.PERMANENTE, status, bytes, duracao,
                        status >= 300 && status < 400
                                ? "HTTP_REDIRECT_BLOCKED" : "HTTP_REMOTE_REJECTED");
            });
        } catch (RespostaMuitoGrandeException e) {
            return new Resposta(Tipo.PERMANENTE, 0, e.bytesLidos,
                    duracaoMs(inicio), "HTTP_RESPONSE_TOO_LARGE");
        } catch (SocketTimeoutException e) {
            return new Resposta(Tipo.TRANSITORIA, 0, 0,
                    duracaoMs(inicio), "HTTP_TIMEOUT");
        } catch (IOException e) {
            return new Resposta(Tipo.TRANSITORIA, 0, 0,
                    duracaoMs(inicio), "HTTP_IO_FAILURE");
        }
    }

    private static int consumirLimitado(HttpEntity entity, int limite) throws IOException {
        if (entity == null) return 0;
        long declarado = entity.getContentLength();
        if (declarado > limite) throw new RespostaMuitoGrandeException(0);
        int total = 0;
        byte[] buffer = new byte[Math.min(8192, limite + 1)];
        try (InputStream input = entity.getContent()) {
            int lidos;
            while ((lidos = input.read(buffer)) >= 0) {
                total += lidos;
                if (total > limite) throw new RespostaMuitoGrandeException(total);
            }
        }
        return total;
    }

    private static HttpHost validarProxy(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            URI uri = URI.create(valor.trim());
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || !(uri.getScheme().equalsIgnoreCase("http")
                    || uri.getScheme().equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException();
            }
            return new HttpHost(uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getHost(), uri.getPort());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Proxy de egress do conector HTTP invalido.");
        }
    }

    private DnsResolver resolverDeConexao(PoliticaAntiSsrf.DestinoAprovado destino) {
        DnsResolver alvo = politica.fixar(destino);
        if (proxy == null) return alvo;
        // Em modo proxy a conexão TCP vai ao egress confiável. O destino
        // continua validado localmente, e o proxy repete a política/pinning.
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                if (host.equalsIgnoreCase(destino.host())) return alvo.resolve(host);
                if (host.equalsIgnoreCase(proxy.getHostName())) {
                    return InetAddress.getAllByName(host);
                }
                throw new UnknownHostException();
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                resolve(host);
                return host;
            }
        };
    }

    private static long duracaoMs(long inicio) {
        return Duration.ofNanos(System.nanoTime() - inicio).toMillis();
    }

    private static final class RespostaMuitoGrandeException extends IOException {
        private final int bytesLidos;

        private RespostaMuitoGrandeException(int bytesLidos) {
            this.bytesLidos = bytesLidos;
        }
    }
}
