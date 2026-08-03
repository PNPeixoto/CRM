package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.DomainException;

final class MfaRequiredException extends DomainException {
    MfaRequiredException() {
        super("MFA_NECESSARIO", "Informe o código de autenticação.");
    }
}

final class MfaEnrollmentRequiredException extends DomainException {
    MfaEnrollmentRequiredException() {
        super("MFA_CADASTRO_NECESSARIO", "Cadastre o segundo fator para continuar.");
    }
}

final class MfaInvalidException extends DomainException {
    MfaInvalidException() {
        super("MFA_INVALIDO", "Código de autenticação inválido.");
    }
}

final class MfaEnrollmentInvalidException extends DomainException {
    MfaEnrollmentInvalidException() {
        super("MFA_CADASTRO_INVALIDO", "O cadastro de autenticação expirou ou é inválido.");
    }
}
