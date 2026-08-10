package br.com.pnp.crm.integration.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteHttpSeguroTest {

    private HttpServer servidor;
    private ClienteHttpSeguro cliente;
    private AtomicInteger alvoDoRedirect;
    private String origem;

    @BeforeEach
    void iniciarServidor() throws Exception {
        alvoDoRedirect = new AtomicInteger();
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", origem + "/alvo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        servidor.createContext("/alvo", exchange -> {
            alvoDoRedirect.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        servidor.createContext("/grande", exchange -> {
            byte[] corpo = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        servidor.createContext("/lento", exchange -> {
            try {
                Thread.sleep(400);
                byte[] corpo = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, corpo.length);
                exchange.getResponseBody().write(corpo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        servidor.start();
        origem = "http://localhost:" + servidor.getAddress().getPort();
        InetAddress loopback = InetAddress.getLoopbackAddress();
        var politicaDeTeste = new PoliticaAntiSsrf(
                host -> new InetAddress[]{loopback}, true, true);
        cliente = new ClienteHttpSeguro(politicaDeTeste);
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
    }

    @Test
    void naoSegueRedirect() {
        var resposta = cliente.enviar(requisicao("/redirect", 1000, 1024));

        assertThat(resposta.tipo()).isEqualTo(TransportHttpSeguro.Tipo.PERMANENTE);
        assertThat(resposta.codigo()).isEqualTo("HTTP_REDIRECT_BLOCKED");
        assertThat(alvoDoRedirect).hasValue(0);
    }

    @Test
    void interrompeRespostaAcimaDoLimite() {
        var resposta = cliente.enviar(requisicao("/grande", 1000, 1024));

        assertThat(resposta.tipo()).isEqualTo(TransportHttpSeguro.Tipo.PERMANENTE);
        assertThat(resposta.codigo()).isEqualTo("HTTP_RESPONSE_TOO_LARGE");
    }

    @Test
    void limitaTempoDeRespostaLenta() {
        var resposta = cliente.enviar(requisicao("/lento", 100, 1024));

        assertThat(resposta.tipo()).isEqualTo(TransportHttpSeguro.Tipo.TRANSITORIA);
        assertThat(resposta.codigo()).isIn("HTTP_TIMEOUT", "HTTP_IO_FAILURE");
        assertThat(resposta.duracaoMs()).isLessThan(1500);
    }

    private TransportHttpSeguro.Requisicao requisicao(
            String caminho, int timeoutMs, int maxBytes) {
        return new TransportHttpSeguro.Requisicao(
                "GET", URI.create(origem + caminho), null, null, "idem",
                null, null, timeoutMs, maxBytes);
    }
}
