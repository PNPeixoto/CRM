package br.com.pnp.crm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifica que nenhum módulo alcança o {@code internal} de outro.
 *
 * <p>É o teste que decide se o monólito continua modular. Sem ele, a primeira
 * injeção de um repositório de outro módulo passa despercebida, a segunda vira
 * precedente, e em poucas semanas o grafo de dependências é uma bola de barro
 * que ninguém consegue mais desfazer — sem que nenhum commit isolado pareça
 * errado.
 *
 * <p>Não sobe contexto do Spring nem precisa de banco: é análise estática do
 * classpath, e por isso roda em segundos.
 */
class FronteiraDeModulosTest {

    private final ApplicationModules modulos = ApplicationModules.of(CrmApplication.class);

    @Test
    @DisplayName("nenhuma fronteira de módulo é violada")
    void fronteirasRespeitadas() {
        modulos.verify();
    }

    @Test
    @DisplayName("imprime o mapa de módulos para leitura humana")
    void descreverModulos() {
        modulos.forEach(System.out::println);
    }
}
