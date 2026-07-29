package br.com.pnp.crm.channel.api;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mensagem recebida, já traduzida do formato do provedor.
 *
 * <p>Este é o contrato que faz o módulo {@code conversation} não saber se a
 * mensagem veio do Telegram, do WhatsApp ou do widget do site. Nenhum campo
 * específico de provedor aparece aqui: o que for específico vive em
 * {@link #payload()}, que existe para diagnóstico e nunca para regra de
 * negócio. No dia em que alguém ler o payload para decidir alguma coisa, a
 * normalização deixou de existir e o domínio voltou a conhecer o provedor.
 *
 * @param channelConnectionId conexão que recebeu — resolve o tenant e o canal
 * @param externalId          id da mensagem no provedor; base da idempotência
 * @param externalContactId   id do interlocutor no provedor
 * @param contactDisplayName  nome informado pelo provedor, quando houver
 * @param tipoConteudo        classificação normalizada
 * @param texto               conteúdo textual; vazio em mensagem só de mídia
 * @param ocorridoEm          quando o provedor diz que a mensagem foi enviada
 * @param payload             original do provedor, para diagnóstico
 */
public record InboundMessage(
        UUID channelConnectionId,
        String externalId,
        String externalContactId,
        String contactDisplayName,
        TipoConteudo tipoConteudo,
        String texto,
        Instant ocorridoEm,
        Map<String, Object> payload) {

    public InboundMessage {
        if (channelConnectionId == null) {
            throw new IllegalArgumentException("channelConnectionId é obrigatório");
        }
        if (externalId == null || externalId.isBlank()) {
            // Sem externalId não há idempotência possível, e o reenvio de
            // webhook — que é certo, não hipotético — duplicaria a mensagem.
            throw new IllegalArgumentException("externalId é obrigatório");
        }
        if (externalContactId == null || externalContactId.isBlank()) {
            throw new IllegalArgumentException("externalContactId é obrigatório");
        }
        if (tipoConteudo == null) {
            throw new IllegalArgumentException("tipoConteudo é obrigatório");
        }
        if (ocorridoEm == null) {
            throw new IllegalArgumentException("ocorridoEm é obrigatório");
        }
        // Cópia defensiva: o adaptador não pode alterar o payload depois de
        // entregá-lo, e Map.copyOf ainda rejeita nulo dentro do mapa.
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public Optional<String> textoOpcional() {
        return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(texto);
    }

    public Optional<String> nomeExibicao() {
        return contactDisplayName == null || contactDisplayName.isBlank()
                ? Optional.empty()
                : Optional.of(contactDisplayName);
    }
}
