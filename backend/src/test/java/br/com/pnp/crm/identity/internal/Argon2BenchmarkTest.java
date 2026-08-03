package br.com.pnp.crm.identity.internal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("carga")
class Argon2BenchmarkTest {

    @Test
    void measuresCurrentEnvironmentCost() {
        PasswordEncoder encoder =
                new PasswordEncoderConfig().passwordEncoder("pepper-for-benchmark");
        encoder.encode("aquecimento do benchmark argon2");

        long started = System.nanoTime();
        int samples = 5;
        for (int sample = 0; sample < samples; sample++) {
            encoder.encode("amostra longa do benchmark argon2 " + sample);
        }
        Duration average = Duration.ofNanos((System.nanoTime() - started) / samples);
        System.out.printf("ARGON2_BENCHMARK samples=%d average_ms=%d params=m32768,t3,p1%n",
                samples, average.toMillis());

        assertThat(average).isBetween(Duration.ofMillis(5), Duration.ofSeconds(2));
    }
}
