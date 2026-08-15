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

/** Porta HTTP estreita para testar o contrato da Graph API sem rede. */
interface MetaTransport {

    Resposta enviar(HttpRequest requisicao) throws IOException, InterruptedException;

    record Resposta(int status, Map<String, List<String>> headers, String corpo) {
        String primeiroHeader(String nome) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(nome))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst().orElse(null);
        }
    }
}

@Component
class MetaHttpTransport implements MetaTransport {

    private static final int LIMITE_RESPOSTA_BYTES = 1_048_576;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Resposta enviar(HttpRequest requisicao) throws IOException, InterruptedException {
        HttpResponse<InputStream> resposta = http.send(
                requisicao, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream corpo = resposta.body()) {
            byte[] bytes = corpo.readNBytes(LIMITE_RESPOSTA_BYTES + 1);
            if (bytes.length > LIMITE_RESPOSTA_BYTES) {
                throw new IOException("Resposta da Meta excede o limite permitido.");
            }
            return new Resposta(resposta.statusCode(), resposta.headers().map(),
                    new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
