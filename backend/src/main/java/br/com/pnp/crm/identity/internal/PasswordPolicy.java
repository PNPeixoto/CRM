package br.com.pnp.crm.identity.internal;

import br.com.pnp.crm.shared.api.DomainException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Política de novas senhas alinhada ao NIST SP 800-63B-4. */
@Component
class PasswordPolicy {

    static final int MINIMUM_LENGTH = 15;
    static final int MAXIMUM_LENGTH = 200;
    private static final Set<String> BLOCKLIST = Set.of(
            "123456789012345", "passwordpassword", "qwertyuiopasdfg",
            "administrador", "senha123456789", "crm-pnp-empresa");

    String validateAndNormalize(String supplied) {
        if (supplied == null) {
            throw new WeakPasswordException();
        }
        String normalized = Normalizer.normalize(supplied, Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MINIMUM_LENGTH || length > MAXIMUM_LENGTH
                || BLOCKLIST.contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new WeakPasswordException();
        }
        return normalized;
    }
}

final class WeakPasswordException extends DomainException {
    WeakPasswordException() {
        super("SENHA_FRACA",
                "Use uma senha longa, com pelo menos 15 caracteres, que não seja comum.");
    }
}

final class InvalidPasswordResetException extends DomainException {
    InvalidPasswordResetException() {
        super("RECUPERACAO_INVALIDA", "O código de recuperação expirou ou é inválido.");
    }
}
