package br.com.pnp.crm.automation.internal;

import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorDeAutomacaoTest {

    private final AutomationActionRegistry actions =
            new AutomationActionRegistry(List.of(new NoopAutomationAction()));

    @Test
    void aceitaArvoreFinitaEContaFanOut() {
        ValidadorDeAutomacao validador = new ValidadorDeAutomacao(actions, 10, 2, 60);
        var resultado = validador.validar(spec(
                passo("inicio", "a", "b"),
                passo("a"),
                passo("b")));

        assertThat(resultado.snapshot().passos()).hasSize(3);
        assertThat(resultado.ramificacoes()).isOne();
    }

    @Test
    void bloqueiaLoopEPassoInalcancavel() {
        ValidadorDeAutomacao validador = new ValidadorDeAutomacao(actions, 10, 5, 60);

        assertThatThrownBy(() -> validador.validar(spec(
                passo("inicio", "a"), passo("a", "inicio"))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("Loops");
        assertThatThrownBy(() -> validador.validar(spec(
                passo("inicio"), passo("orfao"))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("alcancaveis");
    }

    @Test
    void bloqueiaFanOutAcimaDoLimiteEConvergenciaAmbigua() {
        ValidadorDeAutomacao validador = new ValidadorDeAutomacao(actions, 10, 1, 60);

        assertThatThrownBy(() -> validador.validar(spec(
                passo("inicio", "a", "b", "c"),
                passo("a"), passo("b"), passo("c"))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("ramificacoes");

        ValidadorDeAutomacao amplo = new ValidadorDeAutomacao(actions, 10, 5, 60);
        assertThatThrownBy(() -> amplo.validar(spec(
                passo("inicio", "a", "b"),
                passo("a", "fim"), passo("b", "fim"), passo("fim"))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("unico caminho");
    }

    private static AutomationModel.EspecificacaoRequest spec(
            AutomationModel.PassoRequest... passos) {
        return new AutomationModel.EspecificacaoRequest(
                "MESSAGE_RECEIVED", "inicio", 30, List.of(passos));
    }

    private static AutomationModel.PassoRequest passo(String chave, String... proximos) {
        return new AutomationModel.PassoRequest(
                chave, "NOOP_V1", 2, List.of(proximos));
    }
}
