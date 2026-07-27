package br.com.pnp.crm;

import org.springframework.boot.SpringApplication;

public class TestCrmApplication {

	public static void main(String[] args) {
		SpringApplication.from(CrmApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
