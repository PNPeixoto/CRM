package br.com.pnp.crm.report.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvSeguroTest {

    @Test
    void neutralizaFormulaMesmoDepoisDeEspacosEEscapaAspas() {
        assertThat(CsvSeguro.celula(" =SUM(A1:A2)"))
                .isEqualTo("\"' =SUM(A1:A2)\"");
        assertThat(CsvSeguro.celula("+cmd|' /C calc'!A0"))
                .startsWith("\"'+cmd");
        assertThat(CsvSeguro.celula("valor \"seguro\""))
                .isEqualTo("\"valor \"\"seguro\"\"\"");
    }
}
