package br.com.pnp.crm.channel.api;

/**
 * Publicado pelo módulo {@code channel} quando um evento de provedor vira uma
 * mensagem normalizada.
 *
 * <p><b>Existe para quebrar um ciclo.</b> O módulo {@code conversation} já
 * depende de {@code channel.api} — usa {@link InboundMessage},
 * {@link ChannelAdapter} e {@link TipoConteudo}. Se {@code channel} chamasse
 * {@code conversation} de volta para entregar a mensagem, os dois módulos
 * passariam a depender um do outro e {@code ApplicationModules.verify()}
 * reprovaria, com razão: o ciclo torna impossível entender ou extrair
 * qualquer um dos dois isoladamente.
 *
 * <p>Com evento, {@code channel} não conhece quem consome. A seta continua
 * apontando em uma direção só.
 */
public record MensagemNormalizadaEvent(InboundMessage mensagem) {
}
