package br.com.pnp.crm.automation.api;

import java.util.UUID;
import java.util.Map;

/**
 * Acao versionada executavel pelo motor interno.
 *
 * <p>O tipo precisa terminar em {@code _Vn}. Alterar semantica, politica de
 * repeticao ou compensacao exige um novo tipo; uma execucao antiga nunca passa
 * a usar silenciosamente outra semantica.
 *
 * <p>O contexto carrega somente referencias tecnicas. Payload de evento,
 * mensagem e segredo ficam fora do motor e, portanto, fora de sua trilha.
 */
public interface AutomationAction {

    String tipo();

    NaturezaEfeito natureza();

    boolean repeticaoSegura();

    /** Valida a configuração antes de congelá-la na versão da automação. */
    default void validarConfiguracao(Map<String, String> configuracao) {
        if (configuracao != null && !configuracao.isEmpty()) {
            throw new IllegalArgumentException("A acao nao aceita configuracao.");
        }
    }

    Resultado executar(Contexto contexto);

    enum NaturezaEfeito {
        INTERNAL,
        EXTERNAL_REVERSIBLE,
        EXTERNAL_IRREVERSIBLE
    }

    enum Resultado {
        SUCESSO,
        FALHA_TRANSITORIA,
        FALHA_PERMANENTE
    }

    record Contexto(
            UUID tenantId,
            UUID executionId,
            String stepKey,
            String triggerType,
            UUID sourceReferenceId,
            Map<String, String> configuracao) {

        public Contexto {
            configuracao = configuracao == null ? Map.of() : Map.copyOf(configuracao);
        }

        public Contexto(UUID tenantId, UUID executionId, String stepKey,
                        String triggerType, UUID sourceReferenceId) {
            this(tenantId, executionId, stepKey, triggerType, sourceReferenceId, Map.of());
        }
    }
}
