package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.TipoConteudo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TradutorDeWebhookEvolutionTest {

    private final TradutorDeWebhookEvolution tradutor =
            new TradutorDeWebhookEvolution(new ObjectMapper());
    private final UUID conexao = UUID.randomUUID();

    @Test
    void mensagemRecebidaEhNormalizadaSemAcoplarEnvelopeEvolution() {
        var mensagem = tradutor.traduzir(conexao, """
                {
                  "event":"messages.upsert",
                  "instance":"pnp-teste",
                  "data":{
                    "key":{"remoteJid":"5511999999999@s.whatsapp.net","fromMe":false,"id":"ABC123"},
                    "pushName":"Maria",
                    "message":{"conversation":"Ola pelo WhatsApp"},
                    "messageTimestamp":1785000000
                  }
                }
                """).orElseThrow();

        assertThat(mensagem.channelConnectionId()).isEqualTo(conexao);
        assertThat(mensagem.externalId()).isEqualTo("ABC123");
        assertThat(mensagem.externalContactId()).isEqualTo("5511999999999");
        assertThat(mensagem.contactDisplayName()).isEqualTo("Maria");
        assertThat(mensagem.tipoConteudo()).isEqualTo(TipoConteudo.TEXT);
        assertThat(mensagem.texto()).isEqualTo("Ola pelo WhatsApp");
        assertThat(mensagem.ocorridoEm()).isEqualTo(Instant.ofEpochSecond(1785000000));
        assertThat(mensagem.payload()).containsEntry("provedor", "evolution");
    }

    @Test
    void prefereNumeroAlternativoQuandoEvolutionEntregaContatoComoLid() {
        var mensagem = tradutor.traduzir(conexao, """
                {"event":"messages.upsert","data":{
                  "key":{"remoteJid":"69385314111689@lid",
                         "remoteJidAlt":"5511988887777@s.whatsapp.net",
                         "fromMe":false,"id":"LID-1"},
                  "message":{"extendedTextMessage":{"text":"oi"}},
                  "messageTimestamp":"1785000001"}}
                """).orElseThrow();

        assertThat(mensagem.externalContactId()).isEqualTo("5511988887777");
        assertThat(mensagem.texto()).isEqualTo("oi");
    }

    @Test
    void eventoDeSaidaNaoVoltaComoNovaMensagemRecebida() {
        assertThat(tradutor.traduzir(conexao, """
                {"event":"messages.upsert","data":{
                  "key":{"remoteJid":"5511999999999@s.whatsapp.net",
                         "fromMe":true,"id":"OUT-1"},
                  "message":{"conversation":"enviada"},
                  "messageTimestamp":1785000000}}
                """)).isEmpty();
    }
}
