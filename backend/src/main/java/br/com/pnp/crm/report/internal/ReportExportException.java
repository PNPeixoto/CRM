package br.com.pnp.crm.report.internal;

import br.com.pnp.crm.shared.api.DomainException;

final class ReportExportException extends DomainException {

    private ReportExportException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }

    static ReportExportException conflitoDeIdempotencia() {
        return new ReportExportException("CHAVE_IDEMPOTENCIA_EM_CONFLITO",
                "A chave de idempotencia ja foi usada com outro conteudo.");
    }

    static ReportExportException indisponivel() {
        return new ReportExportException("EXPORTACAO_INDISPONIVEL",
                "A exportacao nao esta disponivel para download.");
    }
}
