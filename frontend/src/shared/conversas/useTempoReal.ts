import { Client, type IMessage } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';
import { obterAccessToken, tentarRenovarSessao } from '@/lib/api';
import type { MensagemPush } from './tipos';

export type EstadoConexao = 'conectando' | 'conectado' | 'desconectado';

interface Opcoes {
  readonly tenantId: string | null;
  /** Conversa aberta na tela; `null` quando o usuário está só na lista. */
  readonly conversaAtiva: string | null;
  readonly aoReceberMensagem: (push: MensagemPush) => void;
  /**
   * Chamado a cada (re)conexão. É aqui que a tela recarrega do REST.
   *
   * Enquanto o socket esteve caído, mensagens chegaram e não foram entregues —
   * e não há como saber quantas. Tentar remendar o que faltou é reconstruir
   * estado a partir de informação incompleta; recarregar é barato e sempre
   * correto. Tempo real é otimização, o REST é a fonte da verdade.
   */
  readonly aoSincronizar: () => void;
}

const URL_WEBSOCKET = (() => {
  const protocolo = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${window.location.host}/ws`;
})();

/**
 * Conexão STOMP com o backend.
 *
 * <p>O token vai no cabeçalho do frame CONNECT, não na URL. A API WebSocket do
 * navegador não deixa definir cabeçalho no handshake, e a saída comum — token
 * na query string — o despeja no log do proxy, no histórico e no `Referer`.
 * O `connectHeaders` do stompjs é enviado depois do handshake, dentro da
 * conexão já estabelecida.
 */
export function useTempoReal({
  tenantId,
  conversaAtiva,
  aoReceberMensagem,
  aoSincronizar,
}: Opcoes): EstadoConexao {
  const [estado, setEstado] = useState<EstadoConexao>('desconectado');
  const clienteRef = useRef<Client | null>(null);

  // Os callbacks entram por ref para que trocá-los não derrube e recrie a
  // conexão. Sem isso, cada render do componente pai reconectaria o socket.
  const callbacksRef = useRef({ aoReceberMensagem, aoSincronizar });
  callbacksRef.current = { aoReceberMensagem, aoSincronizar };

  useEffect(() => {
    if (!tenantId) return;

    const cliente = new Client({
      brokerURL: URL_WEBSOCKET,
      reconnectDelay: 5000,
      // Heartbeat nos dois sentidos. Sem ele, uma conexão morta por queda de
      // rede permanece "aberta" para o navegador por minutos, e a tela fica
      // silenciosamente desatualizada sem indicar nada ao usuário.
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      // Executado a cada tentativa, inclusive nas reconexões — e é por isso
      // que o token é lido aqui dentro e não capturado fora: depois de 15
      // minutos o token da primeira conexão já expirou.
      beforeConnect: async () => {
        if (!obterAccessToken()) {
          await tentarRenovarSessao();
        }
        cliente.connectHeaders = {
          Authorization: `Bearer ${obterAccessToken() ?? ''}`,
        };
      },

      onConnect: () => {
        setEstado('conectado');

        cliente.subscribe(`/topic/tenant/${tenantId}/inbox`, (frame: IMessage) => {
          callbacksRef.current.aoReceberMensagem(JSON.parse(frame.body) as MensagemPush);
        });

        callbacksRef.current.aoSincronizar();
      },

      onWebSocketClose: () => setEstado('desconectado'),

      onStompError: () => {
        // O backend derruba a conexão quando o CONNECT ou o SUBSCRIBE são
        // recusados. Token expirado é o caso comum: renovamos, e a política de
        // reconexão do stompjs faz a próxima tentativa já autenticada.
        setEstado('desconectado');
        void tentarRenovarSessao();
      },
    });

    clienteRef.current = cliente;
    setEstado('conectando');
    cliente.activate();

    return () => {
      clienteRef.current = null;
      void cliente.deactivate();
    };
  }, [tenantId]);

  // Inscrição na conversa aberta, separada da conexão: trocar de conversa não
  // pode derrubar o socket.
  useEffect(() => {
    const cliente = clienteRef.current;
    if (!cliente || !cliente.connected || !tenantId || !conversaAtiva) return;

    const inscricao = cliente.subscribe(
      `/topic/tenant/${tenantId}/conversa/${conversaAtiva}`,
      (frame: IMessage) => {
        callbacksRef.current.aoReceberMensagem(JSON.parse(frame.body) as MensagemPush);
      },
    );

    return () => inscricao.unsubscribe();
  }, [tenantId, conversaAtiva, estado]);

  return estado;
}
