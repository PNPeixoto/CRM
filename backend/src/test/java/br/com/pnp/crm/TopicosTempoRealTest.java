package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TopicosTempoReal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A extração de tenant do destino STOMP é o que decide se um usuário do tenant
 * A consegue ouvir o tópico do tenant B.
 *
 * <p>Não precisa de banco nem de Spring: é lógica pura, e por isso é um dos
 * poucos testes de segurança deste projeto que roda em qualquer máquina. Um
 * destino que não case com o padrão precisa ser <b>recusado</b>, nunca
 * liberado por omissão — é essa a assimetria que os casos abaixo protegem.
 */
class TopicosTempoRealTest {

    private static final UUID TENANT = UUID.fromString("019fa91c-0f63-75f7-b4a0-1494c1304c42");
    private static final UUID CONVERSA = UUID.fromString("019fa91c-0f66-7a68-8384-20e85b6c8f5a");

    @Test
    @DisplayName("extrai o tenant do tópico da caixa de entrada")
    void tenantDoInbox() {
        assertThat(TopicosTempoReal.tenantDoDestino(TopicosTempoReal.inbox(TENANT)))
                .contains(TENANT);
    }

    @Test
    @DisplayName("extrai o tenant do tópico de uma conversa")
    void tenantDaConversa() {
        assertThat(TopicosTempoReal.tenantDoDestino(TopicosTempoReal.conversa(TENANT, CONVERSA)))
                .contains(TENANT);
    }

    @Test
    @DisplayName("o tópico da conversa não confunde o id da conversa com o do tenant")
    void naoTrocaConversaPorTenant() {
        // Se a extração pegasse o último uuid em vez do primeiro, um usuário
        // se inscreveria no tópico de outro tenant desde que soubesse um id de
        // conversa do próprio — e a autorização aprovaria.
        assertThat(TopicosTempoReal.tenantDoDestino(TopicosTempoReal.conversa(TENANT, CONVERSA)))
                .isNotEqualTo(java.util.Optional.of(CONVERSA));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "/topic/tenant/",
            "/topic/tenant/nao-e-uuid",
            "/topic/tenant/nao-e-uuid/inbox",
            // Prefixo parecido, destino diferente: precisa falhar.
            "/topic/tenants/019fa91c-0f63-75f7-b4a0-1494c1304c42/inbox",
            "/topic/019fa91c-0f63-75f7-b4a0-1494c1304c42",
            // Tentativa de alcançar o broker por outro caminho.
            "/queue/019fa91c-0f63-75f7-b4a0-1494c1304c42",
            "/topic/**",
            "../topic/tenant/019fa91c-0f63-75f7-b4a0-1494c1304c42/inbox"
    })
    @DisplayName("destino fora do padrão não devolve tenant, e por isso é recusado")
    void destinoInvalidoNaoLibera(String destino) {
        assertThat(TopicosTempoReal.tenantDoDestino(destino)).isEmpty();
    }

    @Test
    @DisplayName("tenants diferentes produzem destinos diferentes")
    void destinosNaoColidem() {
        UUID outro = UUID.randomUUID();
        assertThat(TopicosTempoReal.inbox(TENANT)).isNotEqualTo(TopicosTempoReal.inbox(outro));
        assertThat(TopicosTempoReal.tenantDoDestino(TopicosTempoReal.inbox(outro)))
                .contains(outro);
    }
}
