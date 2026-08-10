package br.com.pnp.crm.audit.internal;

/** Falha fechada para operacoes criticas cuja auditoria nao foi persistida. */
final class AuditUnavailableException extends RuntimeException {
    AuditUnavailableException(Throwable cause) {
        super("Auditoria indisponivel.", cause);
    }
}

