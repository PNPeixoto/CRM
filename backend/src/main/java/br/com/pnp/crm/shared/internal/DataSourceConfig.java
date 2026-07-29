package br.com.pnp.crm.shared.internal;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Envolve o {@link DataSource} criado pelo Spring Boot no
 * {@link TenantAwareDataSource}.
 *
 * <p>Usa um {@link BeanPostProcessor} em vez de declarar um {@code @Bean}
 * próprio para não substituir a autoconfiguração: o pool continua sendo o
 * HikariCP montado pelo Boot, com todas as propriedades de
 * {@code spring.datasource.*} respeitadas. Declarar o DataSource à mão
 * desligaria a autoconfiguração e transferiria para este arquivo a
 * responsabilidade de reconfigurar o pool inteiro — trabalho recorrente a cada
 * upgrade, em troca de nada.
 *
 * <p>Como o Flyway também recebe este bean, as migrations rodam sob o mesmo
 * regime. É proposital: se uma migration de dados só funciona com o RLS
 * desligado, ela é a que está errada.
 */
@Configuration(proxyBeanMethods = false)
class DataSourceConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(dataSource);
        }
        return bean;
    }
}
