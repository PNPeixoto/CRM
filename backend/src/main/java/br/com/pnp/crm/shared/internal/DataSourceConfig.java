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
 * <p>O Flyway usa uma conexão administrativa própria, configurada por
 * {@code spring.flyway.*}. Este bean envolve somente os DataSources da
 * aplicação; assim JPA e JdbcTemplate nunca herdam os privilégios de migration.
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
