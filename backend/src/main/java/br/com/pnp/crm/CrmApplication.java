package br.com.pnp.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * O módulo {@code shared} é declarado como compartilhado: todo módulo pode
 * depender dele sem que isso conte como violação de fronteira. Sem essa
 * declaração, {@code ApplicationModules.verify()} acusaria uma dependência de
 * cada módulo para o shared, e o grafo de módulos viraria ruído — o que
 * acabaria levando alguém a desligar a verificação.
 */
@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class CrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrmApplication.class, args);
	}

}
