package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.channel.api.ChannelAdapter;
import br.com.pnp.crm.channel.api.OutboundMessage;
import br.com.pnp.crm.channel.api.TipoCanal;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chat ao vivo: o widget no site do cliente.
 *
 * <p>É o adaptador mais simples porque não existe provedor externo — a entrega
 * é uma publicação no nosso próprio broker. Por isso mesmo ele é o primeiro:
 * valida conversa, fila, atribuição, tempo real e histórico sem depender de
 * aprovação de terceiro nem de credencial nenhuma.
 *
 * <p><b>E é por isso que ele sozinho não bastava para desenhar a porta.</b>
 * Não tem webhook, não tem {@code external_id} de terceiro, não tem API de
 * mídia e não reenvia evento — as quatro coisas que a porta precisa suportar.
 * O Telegram, na fase seguinte, é o primeiro caso concreto que exercita tudo
 * isso, e é ele quem vai provar se este desenho está certo.
 */
@Component
class LiveChatAdapter implements ChannelAdapter {

    @Override
    public TipoCanal tipo() {
        return TipoCanal.LIVE_CHAT;
    }

    /**
     * Não há chamada de rede a fazer: a mensagem já está gravada e o
     * {@code external_id} do chat ao vivo é o próprio id interno.
     *
     * <p>Ainda assim passa pela fila de saída como qualquer outro canal. Um
     * atalho aqui — "chat ao vivo é local, envia direto" — criaria um segundo
     * caminho de envio, e o caminho menos usado é sempre o menos testado.
     *
     * <p>A entrega ao visitante fica pendente junto com o widget: o visitante
     * não é usuário do CRM, então precisa de sessão própria emitida pelo
     * backend, com origem validada. Enquanto o widget não existe, o que este
     * adaptador entrega é o lado do atendente, que já é observável.
     */
    @Override
    public String enviar(OutboundMessage mensagem) {
        return identificadorLocal(mensagem.messageId());
    }

    /**
     * O canal não tem numeração própria, mas a coluna {@code external_id} e a
     * constraint de idempotência valem para todos. Usar o id interno mantém a
     * regra uniforme em vez de abrir exceção para um canal.
     */
    private String identificadorLocal(UUID messageId) {
        return "livechat:" + messageId;
    }
}
