package br.com.pnp.crm.conversation.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado quando uma mensagem entra no sistema pela primeira vez.
 *
 * <p><b>Não é publicado em reentrega.</b> Provedor reenvia webhook o tempo
 * todo; se o evento saísse a cada reentrega, o atendente veria a mensagem
 * piscar na tela repetidamente e qualquer automação futura disparia várias
 * vezes pelo mesmo fato.
 *
 * <p>É por aqui que o tempo real será alimentado na próxima etapa: o módulo
 * que empurra para o WebSocket escuta este evento em vez de ser chamado
 * direto pela ingestão. Entrega em tempo real é otimização, não fonte da
 * verdade — se o push falhar, o dado está no banco e o cliente recupera por
 * REST ao reconectar.
 *
 * <p><b>Não carrega o texto da mensagem, e isso é deliberado.</b> O Spring
 * Modulith serializa cada evento publicado em {@code event_publication} — a
 * única tabela do schema sem RLS, sem prazo de retenção e com acesso total do
 * runtime. Um campo de texto aqui virava cópia em claro do conteúdo do
 * cliente, fora do isolamento por tenant e fora de qualquer expurgo: a mesma
 * frase que {@code inbound_event} guarda cifrada por sete dias ficaria ali
 * para sempre, legível.
 *
 * <p>Quem precisar do conteúdo carrega a mensagem pelo {@code messageId},
 * onde o RLS e a autorização valem. É a mesma regra do push de WebSocket, que
 * transporta identificador e nunca texto.
 *
 * <p>Registrado como {@code LGPD-001} em
 * {@code contexto/privacidade/inventario-de-tratamento.md}.
 */
public record MensagemRecebidaEvent(
        UUID tenantId,
        UUID conversationId,
        UUID messageId,
        UUID channelConnectionId,
        Instant ocorridoEm) {
}
