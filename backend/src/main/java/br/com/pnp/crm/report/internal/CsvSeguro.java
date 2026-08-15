package br.com.pnp.crm.report.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** CSV com limites fixos e neutralizacao de formulas em qualquer celula. */
final class CsvSeguro {

    static final int MAX_ROWS = 1_000;
    static final int MAX_COLUMNS = 8;
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String HEADER =
            "metric_code,metric_version,value,unit,currency_code,time_zone,snapshot_at\r\n";

    private CsvSeguro() {
    }

    static byte[] gerar(List<ReportMetricCatalog.Definition> definicoes,
                        ReportSnapshotService.Snapshot snapshot) {
        if (definicoes.size() > MAX_ROWS) {
            throw new IllegalStateException("Relatorio excedeu o limite de linhas.");
        }
        Map<String, String> valores = snapshot.valoresCanonicos();
        StringBuilder csv = new StringBuilder(HEADER);
        for (ReportMetricCatalog.Definition definicao : definicoes) {
            String valor = valores.get(definicao.code());
            if (valor == null) {
                throw new IllegalStateException("Metrica sem valor na versao solicitada.");
            }
            linha(csv, definicao.code(), Integer.toString(definicao.versionNumber()), valor,
                    definicao.unit(), definicao.currencyCode(), definicao.timeZone(),
                    snapshot.capturadoEm().toString());
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalStateException("Relatorio excedeu o limite de tamanho.");
        }
        return bytes;
    }

    static String celula(String valor) {
        String seguro = valor == null ? "" : valor;
        int primeiro = 0;
        while (primeiro < seguro.length() && Character.isWhitespace(seguro.charAt(primeiro))) {
            primeiro++;
        }
        if (primeiro < seguro.length() && "=+-@".indexOf(seguro.charAt(primeiro)) >= 0) {
            seguro = "'" + seguro;
        }
        return "\"" + seguro.replace("\"", "\"\"") + "\"";
    }

    private static void linha(StringBuilder csv, String... colunas) {
        if (colunas.length > MAX_COLUMNS) {
            throw new IllegalStateException("Relatorio excedeu o limite de colunas.");
        }
        for (int i = 0; i < colunas.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(celula(colunas[i]));
        }
        csv.append("\r\n");
    }
}
