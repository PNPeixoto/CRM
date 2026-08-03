package br.com.pnp.crm.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.text.Normalizer;

/**
 * Argon2id com pepper.
 *
 * <p><b>Por que Argon2id:</b> é memory-hard. BCrypt e PBKDF2 custam CPU, que
 * é justamente o recurso que uma GPU tem de sobra; o Argon2 custa memória, que
 * não escala barato em paralelo. Para um CRM que guarda a base de contatos de
 * franqueadoras, o cenário a encarecer é o dump vazado sendo quebrado offline.
 *
 * <p><b>Por que pepper:</b> o salt vive no banco, ao lado do hash — quem leva
 * o dump leva os dois. O pepper vive em variável de ambiente e nunca esteve no
 * banco. Sem ele, nenhuma tentativa offline produz o hash correto, por mais
 * fraca que seja a senha do usuário. O ganho é assimétrico e o custo é uma
 * concatenação.
 *
 * <p><b>O que o pepper não resolve:</b> se o atacante tiver execução no
 * servidor, ele lê a variável de ambiente. Pepper protege contra vazamento de
 * banco, que é o incidente comum — não contra comprometimento total.
 */
@Configuration(proxyBeanMethods = false)
class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder(@Value("${app.security.pepper}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            // Falhar na subida é obrigatório. Um pepper vazio produz hashes
            // válidos e verificáveis — a aplicação funcionaria perfeitamente,
            // sem pepper, e ninguém perceberia até o vazamento.
            throw new IllegalStateException(
                    "APP_PEPPER não definido. A aplicação não sobe sem pepper de senha.");
        }
        return new PepperedArgon2PasswordEncoder(pepper);
    }

    /**
     * Envolve o encoder do Spring Security em vez de estendê-lo: os parâmetros
     * do Argon2 ficam onde estão, e a única coisa que este código faz é mudar
     * a entrada.
     *
     * <p>Os parâmetros m=32768, t=3, p=1 foram fixados depois de benchmark no
     * ambiente alvo. O {@code Argon2PasswordEncoder} lê os parâmetros da
     * própria string PHC ao verificar, portanto hashes antigos continuam
     * validando durante uma futura elevação gradual de custo.
     */
    private static final class PepperedArgon2PasswordEncoder implements PasswordEncoder {

        // Benchmark 2026-08-01 no ambiente local: m=32768, t=3, p=1 mantém o
        // custo em torno de 100 ms, sem tornar o login uma via fácil de DoS.
        private final Argon2PasswordEncoder argon2 =
                new Argon2PasswordEncoder(16, 32, 1, 32_768, 3);
        private final String pepper;

        private PepperedArgon2PasswordEncoder(String pepper) {
            this.pepper = pepper;
        }

        @Override
        public String encode(CharSequence senha) {
            return argon2.encode(normalizar(senha) + pepper);
        }

        @Override
        public boolean matches(CharSequence senha, String hashArmazenado) {
            return argon2.matches(normalizar(senha) + pepper, hashArmazenado);
        }

        private String normalizar(CharSequence senha) {
            return Normalizer.normalize(senha.toString(), Normalizer.Form.NFC);
        }
    }
}
