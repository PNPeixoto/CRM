package br.com.pnp.crm.identity.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordSecurityTest {

    private final PasswordEncoder encoder =
            new PasswordEncoderConfig().passwordEncoder("pepper-for-unit-test");

    @Test
    void argon2idUsesAutomaticUniqueSaltAndPepper() {
        String first = encoder.encode("uma senha longa de teste");
        String second = encoder.encode("uma senha longa de teste");

        assertThat(first).startsWith("$argon2id$v=19$m=32768,t=3,p=1$")
                .isNotEqualTo(second);
        assertThat(encoder.matches("uma senha longa de teste", first)).isTrue();
        assertThat(new PasswordEncoderConfig().passwordEncoder("another-pepper")
                .matches("uma senha longa de teste", first)).isFalse();
    }

    @Test
    void unicodeIsNormalizedAndNewPasswordsFollowLengthAndBlocklist() {
        String decomposed = "senha muito longa cafe\u0301";
        String hash = encoder.encode(decomposed);
        assertThat(encoder.matches("senha muito longa café", hash)).isTrue();

        PasswordPolicy policy = new PasswordPolicy();
        assertThat(policy.validateAndNormalize("frase secreta longa e única"))
                .isEqualTo("frase secreta longa e única");
        assertThatThrownBy(() -> policy.validateAndNormalize("curta"))
                .isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validateAndNormalize("passwordpassword"))
                .isInstanceOf(WeakPasswordException.class);
    }
}
