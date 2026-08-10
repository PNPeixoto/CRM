package br.com.pnp.crm;

import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.channel.api.TipoConteudo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tradução do formato do Telegram para o contrato normalizado.
 *
 * <p>Roda sem banco e sem Spring: é transformação pura de JSON, e por isso é
 * um dos poucos pontos da Fase 3 verificáveis em qualquer máquina.
 *
 * <p>O caso que mais importa aqui é o do grupo — trocar {@code chat.id} por
 * {@code from.id} faria cada participante virar uma conversa separada, e o
 * bug só apareceria em produção, no primeiro cliente que usasse grupo.
 */
class TradutorDeUpdateTelegramTest {

    private static final UUID CONEXAO = UUID.randomUUID();

    private final Object tradutor = instanciarTradutor();

    @Test
    @DisplayName("fixture versionada do Bot API 10.2 respeita o contrato normalizado")
    void fixtureOficialVersionada() throws Exception {
        String payload;
        try (var stream = getClass().getResourceAsStream(
                "/fixtures/telegram/bot-api-10.2/update-message.json")) {
            assertThat(stream).isNotNull();
            payload = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        InboundMessage mensagem = traduzir(payload).orElseThrow();
        assertThat(mensagem.externalId()).isEqualTo("501");
        assertThat(mensagem.externalContactId()).isEqualTo("123456789");
        assertThat(mensagem.texto()).isEqualTo("Preciso de atendimento");
    }

    @Test
    @DisplayName("mensagem de texto vira InboundMessage completo")
    void traduzTextoSimples() {
        InboundMessage mensagem = traduzir("""
                {
                  "update_id": 900001,
                  "message": {
                    "message_id": 42,
                    "date": 1785000000,
                    "chat": {"id": 12345, "first_name": "Mariana", "last_name": "Alves"},
                    "from": {"id": 12345, "first_name": "Mariana"},
                    "text": "Boa tarde!"
                  }
                }
                """).orElseThrow();

        assertThat(mensagem.externalId()).isEqualTo("42");
        assertThat(mensagem.externalContactId()).isEqualTo("12345");
        assertThat(mensagem.contactDisplayName()).isEqualTo("Mariana Alves");
        assertThat(mensagem.tipoConteudo()).isEqualTo(TipoConteudo.TEXT);
        assertThat(mensagem.texto()).isEqualTo("Boa tarde!");
        // O instante é o do provedor, não o nosso: a diferença entre os dois é
        // a latência real da entrega, e ela some se carimbarmos com now().
        assertThat(mensagem.ocorridoEm()).isEqualTo(Instant.ofEpochSecond(1785000000L));
        assertThat(mensagem.payload()).containsEntry("provedor", "telegram");
    }

    @Test
    @DisplayName("em grupo, o interlocutor é o chat e não quem falou")
    void grupoUsaChatIdENaoFromId() {
        InboundMessage mensagem = traduzir("""
                {
                  "update_id": 900002,
                  "message": {
                    "message_id": 43,
                    "date": 1785000100,
                    "chat": {"id": -100999, "title": "Unidade Centro"},
                    "from": {"id": 777, "first_name": "Rogerio"},
                    "text": "alguem atende?"
                  }
                }
                """).orElseThrow();

        assertThat(mensagem.externalContactId())
                .as("usar from.id faria cada participante virar uma conversa")
                .isEqualTo("-100999");
        assertThat(mensagem.contactDisplayName()).isEqualTo("Unidade Centro");
    }

    @Test
    @DisplayName("legenda de foto é preservada como texto da mensagem")
    void legendaVirarTexto() {
        InboundMessage mensagem = traduzir("""
                {
                  "update_id": 900003,
                  "message": {
                    "message_id": 44,
                    "date": 1785000200,
                    "chat": {"id": 12345, "first_name": "Mariana"},
                    "photo": [{"file_id": "abc"}],
                    "caption": "segue a foto do espaço"
                  }
                }
                """).orElseThrow();

        assertThat(mensagem.tipoConteudo()).isEqualTo(TipoConteudo.IMAGE);
        // Descartar a legenda perderia conteúdo escrito pelo cliente.
        assertThat(mensagem.texto()).isEqualTo("segue a foto do espaço");
    }

    @Test
    @DisplayName("update que não é mensagem é ignorado sem virar erro")
    void updateSemMensagemEhIgnorado() {
        // Edição, entrada em grupo, resultado de enquete. Tratar como falha
        // encheria o log de ruído e consumiria tentativas por algo que nunca
        // vai mudar.
        assertThat(traduzir("""
                {"update_id": 900004, "edited_message": {"message_id": 45}}
                """)).isEmpty();

        assertThat(traduzir("""
                {"update_id": 900005}
                """)).isEmpty();
    }

    @Test
    @DisplayName("tipo desconhecido vira OTHER em vez de ser descartado")
    void tipoDesconhecidoVirarOther() {
        InboundMessage mensagem = traduzir("""
                {
                  "update_id": 900006,
                  "message": {
                    "message_id": 46,
                    "date": 1785000300,
                    "chat": {"id": 12345, "first_name": "Mariana"},
                    "sticker": {"file_id": "xyz"}
                  }
                }
                """).orElseThrow();

        assertThat(mensagem.tipoConteudo()).isEqualTo(TipoConteudo.OTHER);
        assertThat(mensagem.textoOpcional()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Optional<InboundMessage> traduzir(String payload) {
        try {
            var metodo = tradutor.getClass()
                    .getDeclaredMethod("traduzir", UUID.class, String.class);
            metodo.setAccessible(true);
            return (Optional<InboundMessage>) metodo.invoke(tradutor, CONEXAO, payload);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * O tradutor tem visibilidade de pacote — é interno ao módulo
     * {@code channel} e não deve ser público só para o teste alcançá-lo. A
     * reflexão aqui é o preço de manter a fronteira honesta.
     */
    private static Object instanciarTradutor() {
        try {
            Class<?> classe = Class.forName(
                    "br.com.pnp.crm.channel.internal.TradutorDeUpdateTelegram");
            Constructor<?> construtor = classe.getDeclaredConstructors()[0];
            construtor.setAccessible(true);
            return construtor.newInstance(new tools.jackson.databind.ObjectMapper());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível instanciar o tradutor.", e);
        }
    }
}
