package br.com.pnp.crm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Base dos testes que precisam de Postgres real.
 *
 * <p>Não usa o profile {@code dev}: o seed de {@code db/dev} traria dados que
 * os testes não controlam, e um teste que depende de linha criada por outro
 * arquivo quebra quando aquele arquivo muda por um motivo sem relação. Cada
 * teste cria o que precisa.
 *
 * <p>Os segredos são fixados aqui porque a aplicação recusa subir sem eles —
 * comportamento verificado indiretamente por todo teste que usa esta anotação.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integracao")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // TESTE — valores locais de execução de teste, nunca de ambiente real.
        "app.security.pepper=pepper-de-teste",
        "app.security.jwt-signing-key=chave-de-teste-com-mais-de-32-bytes-ok",
        "app.security.cookie-secure=false",
        "spring.flyway.user=crm_migrator_test",
        "spring.flyway.password=migrator-test-password",
        "app.cors.allowed-origins=http://localhost:5174",
        // O worker de envio fica desligado: rodando, ele consumiria a fila
        // enquanto o teste ainda monta o cenário, e a falha seria
        // intermitente. Quem testar o worker o invoca diretamente.
        "app.fila-de-saida.habilitada=false",
        "app.entrada-de-webhook.habilitada=false",
        "app.retencao-de-webhook.habilitada=false",
        "app.automation.worker-enabled=false",
        "app.report-export.worker-enabled=false",
        "app.report-export.storage-path=${java.io.tmpdir}/crm-pnp-report-exports-test",
        "app.providers.telegram.reconciliation-enabled=false",
        "app.providers.telegram.media-retention-enabled=false",
        // 32 bytes em base64, exigidos pelo AES-256 do cofre de credenciais.
        "app.security.channel-secret-key=dGVzdGUtY2hhbm5lbC1rZXktMzItYnl0ZXMtb2shISE=",
        "app.security.mfa-secret-key=dGVzdC1tZmEta2V5LXNlcGFyYXRlLTMyLWJ5dGVzISE=",
        "app.security.media-signing-key=dGVzdC1tZWRpYS1rZXktc2VwYXJhdGUtMzItYnl0ZXMh",
        "app.security.http-connector-secret-key=dGVzdC1odHRwLWNvbm5lY3Rvci1rZXktMzItYnl0ZXM=",
        "app.security.report-export-encryption-key=dGVzdC1yZXBvcnQtZXhwb3J0LWtleS0zMi1ieXRlcyE=",
        "app.security.report-export-signing-key=dGVzdC1yZXBvcnQtc2lnbmluZy1rZXktMzItYnl0ZSE=",
        "app.security.password-reset-delivery-enabled=false"
})
public @interface TesteDeIntegracao {
}
