package br.com.pnp.crm;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

final class ValidaQuarentenaExtension implements BeforeAllCallback, BeforeEachCallback {

    private static final Clock RELOGIO = Clock.systemUTC();

    @Override
    public void beforeAll(ExtensionContext context) {
        validar(context.getRequiredTestClass());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getTestMethod().ifPresent(ValidaQuarentenaExtension::validar);
    }

    static void validar(AnnotatedElement elemento) {
        Quarentena quarentena = elemento.getAnnotation(Quarentena.class);
        if (quarentena == null) {
            return;
        }
        exigirTexto(quarentena.issue(), "issue");
        exigirTexto(quarentena.responsavel(), "responsavel");
        exigirTexto(quarentena.justificativa(), "justificativa");
        if (quarentena.categoria().proibida()) {
            throw new AssertionError("Categoria " + quarentena.categoria()
                    + " não pode ser colocada em quarentena");
        }

        final LocalDate expiraEm;
        try {
            expiraEm = LocalDate.parse(quarentena.expiraEm());
        } catch (DateTimeParseException exception) {
            throw new AssertionError("expiraEm deve usar ISO-8601 (AAAA-MM-DD)", exception);
        }
        if (!expiraEm.isAfter(LocalDate.now(RELOGIO))) {
            throw new AssertionError("Quarentena expirada em " + expiraEm);
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new AssertionError("Quarentena exige " + campo);
        }
    }
}
