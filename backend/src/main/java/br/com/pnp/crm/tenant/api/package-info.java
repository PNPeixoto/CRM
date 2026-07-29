/**
 * Interface pública do módulo tenant.
 *
 * <p>{@code @NamedInterface} é o que de fato torna este pacote visível para
 * outros módulos. Sem ele, o Spring Modulith trata todo subpacote como
 * interno, e {@code ApplicationModules.verify()} reprova qualquer uso daqui de
 * fora — mesmo o pacote se chamando "api".
 */
@org.springframework.modulith.NamedInterface("api")
package br.com.pnp.crm.tenant.api;
