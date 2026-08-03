package br.com.pnp.crm.shared.api;

import java.util.UUID;

/**
 * Porta de autorização para inscrição em tópico de tempo real.
 *
 * <p><b>Por que a porta mora em shared e não em organization:</b> quem precisa
 * decidir é o interceptador STOMP, que vive em {@code shared.internal}. Se ele
 * importasse {@code organization.api}, shared passaria a depender de
 * organization, que já depende de shared — ciclo que o Spring Modulith reprova.
 * A inversão põe o contrato do lado de quem consome e a decisão do lado de quem
 * sabe.
 *
 * <p><b>Por que não reusar {@code Autorizacao}:</b> aquele serviço tem escopo de
 * requisição e lê o usuário do {@code SecurityContext}. Um frame STOMP não é uma
 * requisição HTTP e não tem nenhum dos dois — o usuário vem do principal da
 * sessão WebSocket, e por isso entra aqui como parâmetro.
 */
public interface AutorizacaoDeEscuta {

    /**
     * Confirma que o usuário ainda pode ouvir o tempo real do tenant.
     *
     * <p>A conexão WebSocket dura horas. Verificar apenas no CONNECT deixaria
     * uma permissão revogada valendo até o cliente reconectar — e quem foi
     * desligado no meio do expediente continuaria recebendo mensagem de cliente
     * em tempo real.
     *
     * @throws AcessoNegadoException quando não há membership vigente ou a
     *                               permissão de leitura de conversa não foi
     *                               concedida
     */
    void exigirEscutaDeConversas(UUID tenantId, UUID usuarioId);
}
