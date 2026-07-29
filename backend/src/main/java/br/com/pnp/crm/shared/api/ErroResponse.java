package br.com.pnp.crm.shared.api;

import java.time.Instant;
import java.util.List;

/**
 * Corpo único de toda resposta de erro da API.
 *
 * @param codigo         identificador estável para o cliente decidir o que fazer
 * @param mensagem       texto genérico, seguro para exibir ao usuário final
 * @param correlacaoId   liga esta resposta à entrada correspondente no log,
 *                       onde está o detalhe. É o que permite ao usuário
 *                       relatar um erro sem que a API precise revelar nada
 *                       sobre a causa
 * @param campos         erros de validação por campo; vazio quando não se aplica
 * @param momento        instante em UTC
 */
public record ErroResponse(
        String codigo,
        String mensagem,
        String correlacaoId,
        List<ErroCampo> campos,
        Instant momento) {

    public ErroResponse {
        campos = campos == null ? List.of() : List.copyOf(campos);
    }

    public static ErroResponse de(String codigo, String mensagem, String correlacaoId) {
        return new ErroResponse(codigo, mensagem, correlacaoId, List.of(), Instant.now());
    }

    public record ErroCampo(String campo, String mensagem) {
    }
}
