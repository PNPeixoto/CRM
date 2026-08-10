package br.com.pnp.crm.channel.internal;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Porta estreita para tornar o contrato HTTP do provedor testavel sem rede. */
interface TelegramTransport {

    Resposta enviar(HttpRequest requisicao) throws IOException, InterruptedException;

    default Download baixar(HttpRequest requisicao) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("Download nao implementado.");
    }

    record Resposta(int status, Map<String, List<String>> headers, String corpo) {
        String primeiroHeader(String nome) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(nome))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst().orElse(null);
        }
    }

    record Download(int status, Map<String, List<String>> headers, InputStream corpo)
            implements AutoCloseable {
        String primeiroHeader(String nome) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(nome))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst().orElse(null);
        }

        @Override
        public void close() throws IOException {
            corpo.close();
        }
    }
}

@Component
class TelegramHttpTransport implements TelegramTransport {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Resposta enviar(HttpRequest requisicao) throws IOException, InterruptedException {
        HttpResponse<String> resposta = http.send(
                requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resposta(resposta.statusCode(), resposta.headers().map(), resposta.body());
    }

    @Override
    public Download baixar(HttpRequest requisicao) throws IOException, InterruptedException {
        HttpResponse<InputStream> resposta = http.send(
                requisicao, HttpResponse.BodyHandlers.ofInputStream());
        return new Download(resposta.statusCode(), resposta.headers().map(), resposta.body());
    }
}
