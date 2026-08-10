package br.com.pnp.crm.integration.internal;

import java.net.URI;

interface TransportHttpSeguro {

    Resposta enviar(Requisicao requisicao);

    record Requisicao(
            String metodo,
            URI uri,
            String corpo,
            String cabecalhoIdempotencia,
            String chaveIdempotencia,
            String cabecalhoAutenticacao,
            String valorAutenticacao,
            int timeoutMs,
            int maxRespostaBytes) {
    }

    record Resposta(
            Tipo tipo,
            int statusHttp,
            int respostaBytes,
            long duracaoMs,
            String codigo) {
    }

    enum Tipo { SUCESSO, TRANSITORIA, PERMANENTE }
}
