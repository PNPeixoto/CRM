package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.conversation.api.DirecaoMensagem;
import br.com.pnp.crm.conversation.api.StatusConversa;
import br.com.pnp.crm.conversation.api.StatusMensagem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

final class ConversationDtos {

    /**
     * Limite de tamanho do texto. Não é estética: sem teto, uma requisição
     * carrega megabytes para dentro da transação e para o provedor, que vai
     * recusar de qualquer forma — melhor recusar antes de gravar.
     */
    private static final int TAMANHO_MAXIMO_TEXTO = 4096;

    private ConversationDtos() {
    }

    record ConversaResumo(
            UUID id,
            UUID channelConnectionId,
            String contatoNome,
            StatusConversa status,
            UUID atendenteId,
            Instant ultimaMensagemEm) {
    }

    record MensagemResposta(
            UUID id,
            DirecaoMensagem direcao,
            TipoConteudo tipoConteudo,
            String texto,
            StatusMensagem status,
            UUID autorId,
            Instant criadaEm) {
    }

    /**
     * Note o que NÃO está aqui: {@code tenantId}, {@code conversationId} e
     * {@code direction}. O tenant sai do token, a conversa vem do caminho da
     * URL e a direção é sempre saída. Aceitar qualquer um dos três no corpo
     * seria deixar o cliente escolher em nome de quem escreve.
     */
    record EnviarMensagemRequest(
            @NotBlank(message = "A mensagem não pode ficar em branco.")
            @Size(max = TAMANHO_MAXIMO_TEXTO, message = "Mensagem longa demais.")
            String texto) {
    }
}
