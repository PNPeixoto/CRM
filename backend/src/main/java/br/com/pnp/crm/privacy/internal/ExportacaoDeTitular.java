package br.com.pnp.crm.privacy.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta a um pedido de acesso do titular.
 *
 * <p><b>Cada secao diz de onde veio.</b> Uma exportacao que entrega uma lista
 * de dados sem dizer a origem obriga o titular a confiar; dizer a tabela, a
 * finalidade e o prazo permite conferir. E tambem a unica forma de responder a
 * pergunta seguinte, que e sempre "por que voces tem isso".
 *
 * <p>O documento nao inclui dado de terceiro. Numa conversa, a identidade do
 * atendente e outro titular: aparece como funcao, nunca como pessoa.
 */
record ExportacaoDeTitular(
        UUID contatoId,
        Instant geradaEm,
        UUID solicitadaPor,
        List<Secao> secoes,
        List<String> observacoes) {

    /**
     * Um conjunto de registros com a mesma origem e finalidade.
     *
     * @param origem      tabela de onde o dado saiu, para conferencia
     * @param finalidade  por que o dado e tratado
     * @param retencao    prazo declarado, ou o motivo de nao haver
     */
    record Secao(
            String categoria,
            String origem,
            String finalidade,
            String retencao,
            int quantidade,
            List<Map<String, String>> registros) {
    }
}
