package br.com.pnp.crm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // TESTE — valores locais de execução de teste, nunca de ambiente real.
        "app.security.pepper=pepper-de-teste",
        "app.security.jwt-signing-key=chave-de-teste-com-mais-de-32-bytes-ok",
        "app.security.cookie-secure=false",
        "app.cors.allowed-origins=http://localhost:5173",
        // O worker de envio fica desligado: rodando, ele consumiria a fila
        // enquanto o teste ainda monta o cenário, e a falha seria
        // intermitente. Quem testar o worker o invoca diretamente.
        "app.fila-de-saida.habilitada=false"
})
public @interface TesteDeIntegracao {
}
