package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.shared.api.RequisicaoInvalidaException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/** Catalogo do release; definicao antiga permanece consultavel por versao. */
@Service
class ReportMetricCatalog {

    static final String OVERVIEW_V1 = "OVERVIEW_V1";
    private final JdbcTemplate jdbc;

    ReportMetricCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    List<Definition> listarAtivas() {
        return carregar(null);
    }

    @Transactional(readOnly = true)
    List<Definition> paraRelatorio(String reportCode) {
        if (!OVERVIEW_V1.equals(reportCode)) {
            throw new RequisicaoInvalidaException("Relatorio nao suportado.");
        }
        List<Definition> definicoes = carregar(reportCode);
        if (definicoes.isEmpty()) {
            throw new IllegalStateException("Catalogo de metricas indisponivel.");
        }
        return definicoes;
    }

    private List<Definition> carregar(String reportCode) {
        String filtro = reportCode == null ? "" : " AND report_code = ?";
        Object[] parametros = reportCode == null ? new Object[0] : new Object[]{reportCode};
        return jdbc.query("""
                SELECT code, version_number, report_code, display_name, formula,
                       granularity, time_zone, currency_code, unit, source_modules
                  FROM report_metric_definition
                 WHERE active
                """ + filtro + " ORDER BY code, version_number",
                (rs, row) -> new Definition(
                        rs.getString("code"), rs.getInt("version_number"),
                        rs.getString("report_code"), rs.getString("display_name"),
                        rs.getString("formula"), rs.getString("granularity"),
                        rs.getString("time_zone"), rs.getString("currency_code"),
                        rs.getString("unit"),
                        List.copyOf(Arrays.asList((String[]) rs.getArray("source_modules").getArray()))),
                parametros);
    }

    record Definition(String code, int versionNumber, String reportCode,
                      String displayName, String formula, String granularity,
                      String timeZone, String currencyCode, String unit,
                      List<String> sourceModules) {
    }
}
