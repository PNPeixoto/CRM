package br.com.pnp.crm;

import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.channel.api.TipoConteudo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O contrato normalizado que separa o domínio dos provedores.
 *
 * <p>As validações do construtor existem para que um adaptador mal escrito
 * falhe na fronteira, e não três camadas adiante com um NullPointerException
 * sem contexto.
 */
class InboundMessageTest {

    private static final UUID CONEXAO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-07-29T12:00:00Z");

    @Test
    @DisplayName("mensagem sem externalId é recusada, porque sem ela não há idempotência")
    void exigeExternalId() {
        // Reenvio de webhook é comportamento normal do provedor. Sem
        // externalId a constraint de idempotência não tem sobre o que agir e a
        // mensagem duplicaria na tela.
        assertThatThrownBy(() -> nova(null, "contato-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalId");

        assertThatThrownBy(() -> nova("   ", "contato-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mensagem sem interlocutor é recusada")
    void exigeExternalContactId() {
        assertThatThrownBy(() -> nova("msg-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalContactId");
    }

    @Test
    @DisplayName("payload nulo vira mapa vazio, nunca null")
    void payloadNuncaEhNulo() {
        InboundMessage mensagem = new InboundMessage(
                CONEXAO, "msg-1", "contato-1", "Fulano",
                TipoConteudo.TEXT, "oi", AGORA, null);

        assertThat(mensagem.payload()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("o payload é copiado, então o adaptador não altera o que já entregou")
    void payloadEhCopiaDefensiva() {
        Map<String, Object> original = new HashMap<>();
        original.put("update_id", 42);

        InboundMessage mensagem = new InboundMessage(
                CONEXAO, "msg-1", "contato-1", "Fulano",
                TipoConteudo.TEXT, "oi", AGORA, original);

        original.put("injetado_depois", "valor");

        assertThat(mensagem.payload()).containsOnlyKeys("update_id");
    }

    @Test
    @DisplayName("texto em branco vira Optional vazio, para mensagem só de mídia")
    void textoOpcional() {
        InboundMessage soMidia = new InboundMessage(
                CONEXAO, "msg-1", "contato-1", "Fulano",
                TipoConteudo.IMAGE, "  ", AGORA, Map.of());

        assertThat(soMidia.textoOpcional()).isEmpty();
        assertThat(soMidia.nomeExibicao()).contains("Fulano");
    }

    private static InboundMessage nova(String externalId, String contatoId) {
        return new InboundMessage(
                CONEXAO, externalId, contatoId, "Fulano",
                TipoConteudo.TEXT, "oi", AGORA, Map.of());
    }
}
