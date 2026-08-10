package br.com.pnp.crm.channel.internal;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Porta HTTP estreita para testar o contrato da Evolution sem rede real. */
interface EvolutionTransport {

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
class EvolutionHttpTransport implements EvolutionTransport {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Resposta enviar(HttpRequest requisicao) throws IOException, InterruptedException {
        HttpResponse<String> resposta = http.send(
                requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resposta(resposta.statusCode(), resposta.headers().map(), resposta.body());
    }
}
