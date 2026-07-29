package br.com.pnp.crm.channel.api;

/**
 * A porta de canal. Um adaptador por provedor.
 *
 * <p>Só existem dois métodos, e isso é deliberado. A regra da abstração do
 * projeto diz para não criar interface antes do segundo caso de uso concreto:
 * esta nasce com {@code LiveChatAdapter} e {@code TelegramAdapter} à vista, e
 * qualquer coisa além de "envie isto" e "você atende este provedor?" seria
 * antecipação.
 *
 * <p><b>O que esta porta deliberadamente NÃO tem:</b>
 *
 * <ul>
 *   <li>Método de recebimento. Mensagem que chega é iniciativa do provedor,
 *       não nossa — cada adaptador expõe o próprio endpoint de webhook, com a
 *       autenticação que aquele provedor usa (HMAC na Meta, {@code
 *       secret_token} no Telegram), e traduz para {@link InboundMessage}. Um
 *       método {@code receber()} genérico teria que aceitar o formato de todos
 *       eles, o que é o oposto de normalizar.</li>
 *   <li>Consulta de janela de atendimento. Só o WhatsApp tem janela de 24h;
 *       Telegram e chat ao vivo não têm nada parecido. Colocar isso aqui
 *       agora obrigaria dois adaptadores a implementar um conceito que não
 *       existe no provedor deles.</li>
 * </ul>
 */
public interface ChannelAdapter {

    /**
     * @return o provedor que este adaptador atende
     */
    TipoCanal tipo();

    /**
     * Entrega a mensagem ao provedor.
     *
     * <p>Chamado <b>pelo worker da fila de saída</b>, nunca de dentro da
     * transação que criou a mensagem. Provedor externo pode demorar, falhar ou
     * ficar fora do ar, e uma chamada de rede dentro da transação segura a
     * conexão do banco pelo tempo do timeout HTTP — sob carga, isso esgota o
     * pool e derruba a aplicação inteira por causa de um provedor lento.
     *
     * @return o identificador atribuído pelo provedor, para casar com os
     * eventos de entrega e leitura que chegarem depois
     * @throws EnvioDeMensagemException quando o provedor recusa ou não responde;
     *                                  o worker decide retentar ou mandar para
     *                                  a fila morta
     */
    String enviar(OutboundMessage mensagem);
}
