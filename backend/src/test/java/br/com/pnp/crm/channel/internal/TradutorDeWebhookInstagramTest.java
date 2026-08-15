package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.TipoConteudo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TradutorDeWebhookInstagramTest {

    private final TradutorDeWebhookInstagram tradutor =
            new TradutorDeWebhookInstagram(new ObjectMapper());
    private final UUID conexao = UUID.randomUUID();

    @Test
    void traduzLoteEIgnoraEcoSemPersistirUrlDeMidia() {
        var mensagens = tradutor.traduzir(conexao, """
                {
                  "object":"instagram",
                  "entry":[{
                    "id":"178900000000001",
                    "messaging":[
                      {"sender":{"id":"IGSID-1"},"recipient":{"id":"178900000000001"},
                       "timestamp":1785000000123,
                       "message":{"mid":"MID-1","text":"Ola pelo Instagram"}},
                      {"sender":{"id":"IGSID-2"},"recipient":{"id":"178900000000001"},
                       "timestamp":"1785000001123",
                       "message":{"mid":"MID-2","attachments":[
                         {"type":"image","payload":{"url":"https://cdn.example/privada"}}]}},
                      {"sender":{"id":"178900000000001"},"recipient":{"id":"IGSID-1"},
                       "timestamp":1785000002123,
                       "message":{"mid":"MID-3","text":"eco","is_echo":true}}
                    ]
                  }]
                }
                """);

        assertThat(mensagens).hasSize(2);
        assertThat(mensagens.getFirst().externalId()).isEqualTo("MID-1");
        assertThat(mensagens.getFirst().externalContactId()).isEqualTo("IGSID-1");
        assertThat(mensagens.getFirst().tipoConteudo()).isEqualTo(TipoConteudo.TEXT);
        assertThat(mensagens.getFirst().texto()).isEqualTo("Ola pelo Instagram");
        assertThat(mensagens.getFirst().ocorridoEm())
                .isEqualTo(Instant.ofEpochMilli(1785000000123L));

        assertThat(mensagens.get(1).tipoConteudo()).isEqualTo(TipoConteudo.IMAGE);
        assertThat(mensagens.get(1).payload())
                .containsEntry("provedor", "instagram")
                .containsEntry("tipo_anexo", "image");
        assertThat(mensagens.get(1).payload().toString()).doesNotContain("cdn.example");
    }

    @Test
    void ignoraObjetoQueNaoEhInstagram() {
        assertThat(tradutor.traduzir(conexao,
                "{\"object\":\"page\",\"entry\":[]}"))
                .isEmpty();
    }
}
