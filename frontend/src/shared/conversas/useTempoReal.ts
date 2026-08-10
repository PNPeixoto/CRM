import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';
import { obterAccessToken, tentarRenovarSessao } from '@/lib/api';
import { conversasApi } from './api';
import type { MensagemPush } from './tipos';

export type EstadoConexao = 'conectando' | 'conectado' | 'desconectado';

interface Opcoes {
  readonly tenantId: string | null;
  readonly conversaAtiva: string | null;
  readonly sequenciaInicial: number;
  readonly aoReceberMensagem: (push: MensagemPush) => void;
  readonly aoSincronizar: () => void;
}

const URL_WEBSOCKET = (() => {
  const protocolo = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${window.location.host}/ws`;
})();

export type DecisaoDeSequencia = 'duplicado' | 'proximo' | 'lacuna';

/** Regra pura compartilhada pelos caminhos WebSocket e REST. */
export function decidirSequencia(atual: number, recebida: number): DecisaoDeSequencia {
  if (recebida <= atual) return 'duplicado';
  if (recebida === atual + 1) return 'proximo';
  return 'lacuna';
}

function lerPush(frame: IMessage): MensagemPush | null {
  try {
    const valor = JSON.parse(frame.body) as Partial<MensagemPush>;
    if (
      typeof valor.eventId !== 'string'
      || typeof valor.sequence !== 'number'
      || typeof valor.tipo !== 'string'
      || typeof valor.conversationId !== 'string'
      || (valor.messageId !== null && typeof valor.messageId !== 'string')
      || typeof valor.versao !== 'number'
      || typeof valor.ocorridoEm !== 'string'
    ) return null;
    return valor as MensagemPush;
  } catch {
    return null;
  }
}

/**
 * Mantém o socket como aceleração e o REST como fonte da verdade. Cada evento
 * possui sequência monotônica por empresa; duplicatas são ignoradas e qualquer
 * lacuna é recomposta pelo endpoint de eventos antes de atualizar a tela.
 */
export function useTempoReal({
  tenantId,
  conversaAtiva,
  sequenciaInicial,
  aoReceberMensagem,
  aoSincronizar,
}: Opcoes): EstadoConexao {
  const [estado, setEstado] = useState<EstadoConexao>('desconectado');
  const clienteRef = useRef<Client | null>(null);
  const cursorRef = useRef(sequenciaInicial);
  const sequenciaInicialRef = useRef(sequenciaInicial);
  const processarPushRef = useRef<(push: MensagemPush) => void>(() => undefined);
  const recuperarRef = useRef<() => Promise<void>>(async () => undefined);
  const callbacksRef = useRef({ aoReceberMensagem, aoSincronizar });
  callbacksRef.current = { aoReceberMensagem, aoSincronizar };
  sequenciaInicialRef.current = sequenciaInicial;

  useEffect(() => {
    cursorRef.current = Math.max(cursorRef.current, sequenciaInicial);
  }, [sequenciaInicial, tenantId]);

  useEffect(() => {
    if (!tenantId) {
      setEstado('desconectado');
      return;
    }

    cursorRef.current = sequenciaInicialRef.current;
    const abortador = new AbortController();
    let recuperacaoEmCurso: Promise<void> | null = null;
    let timerDePolling: ReturnType<typeof setTimeout> | null = null;
    let encerrado = false;

    const recuperar = (): Promise<void> => {
      if (recuperacaoEmCurso) return recuperacaoEmCurso;

      const tarefa = (async () => {
        let houveMudanca = false;
        do {
          const pagina = await conversasApi.eventos(cursorRef.current, abortador.signal);
          if (pagina.resetObrigatorio) {
            cursorRef.current = pagina.ultimaSequencia;
            callbacksRef.current.aoSincronizar();
            return;
          }

          for (const evento of [...pagina.eventos].sort((a, b) => a.sequence - b.sequence)) {
            const decisao = decidirSequencia(cursorRef.current, evento.sequence);
            if (decisao === 'duplicado') continue;
            if (decisao === 'lacuna') {
              cursorRef.current = pagina.ultimaSequencia;
              callbacksRef.current.aoSincronizar();
              return;
            }
            cursorRef.current = evento.sequence;
            houveMudanca = true;
          }

          if (!pagina.temMais) break;
        } while (!abortador.signal.aborted);

        if (houveMudanca) callbacksRef.current.aoSincronizar();
      })().catch((erro: unknown) => {
        if (!abortador.signal.aborted) {
          // A próxima tentativa do socket ou do polling retoma do mesmo cursor.
          console.warn('Não foi possível recuperar os eventos da Inbox.', erro);
        }
      });

      recuperacaoEmCurso = tarefa.finally(() => {
        recuperacaoEmCurso = null;
      });
      return recuperacaoEmCurso;
    };
    recuperarRef.current = recuperar;

    processarPushRef.current = (push) => {
      const decisao = decidirSequencia(cursorRef.current, push.sequence);
      if (decisao === 'duplicado') return;
      if (decisao === 'lacuna') {
        void recuperar();
        return;
      }
      cursorRef.current = push.sequence;
      callbacksRef.current.aoReceberMensagem(push);
    };

    const cliente = new Client({
      brokerURL: URL_WEBSOCKET,
      reconnectDelay: 1_000 + Math.floor(Math.random() * 500),
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      maxReconnectDelay: 30_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async () => {
        setEstado('conectando');
        if (!obterAccessToken()) await tentarRenovarSessao();
        cliente.connectHeaders = { Authorization: `Bearer ${obterAccessToken() ?? ''}` };
      },
      onConnect: () => {
        setEstado('conectado');
        cliente.subscribe(`/topic/tenant/${tenantId}/inbox`, (frame: IMessage) => {
          const push = lerPush(frame);
          if (push) processarPushRef.current(push);
        });
        void recuperar();
      },
      onWebSocketClose: () => setEstado('desconectado'),
      onStompError: () => {
        setEstado('desconectado');
        void tentarRenovarSessao();
      },
    });

    clienteRef.current = cliente;
    setEstado('conectando');
    cliente.activate();

    const agendarPolling = (imediato = false) => {
      if (timerDePolling) clearTimeout(timerDePolling);
      const intervalo = imediato ? 0 : document.hidden ? 30_000 : 5_000;
      timerDePolling = setTimeout(async () => {
        if (!encerrado && navigator.onLine && !cliente.connected) await recuperar();
        if (!encerrado) agendarPolling();
      }, intervalo);
    };
    const aoVoltar = () => agendarPolling(true);
    const aoFicarOffline = () => setEstado('desconectado');
    document.addEventListener('visibilitychange', aoVoltar);
    window.addEventListener('online', aoVoltar);
    window.addEventListener('offline', aoFicarOffline);
    agendarPolling();

    return () => {
      encerrado = true;
      abortador.abort();
      if (timerDePolling) clearTimeout(timerDePolling);
      document.removeEventListener('visibilitychange', aoVoltar);
      window.removeEventListener('online', aoVoltar);
      window.removeEventListener('offline', aoFicarOffline);
      clienteRef.current = null;
      void cliente.deactivate();
    };
  }, [tenantId]);

  useEffect(() => {
    const cliente = clienteRef.current;
    if (!cliente || !cliente.connected || !tenantId || !conversaAtiva) return;

    const inscricao = cliente.subscribe(
      `/topic/tenant/${tenantId}/conversa/${conversaAtiva}`,
      (frame: IMessage) => {
        const push = lerPush(frame);
        if (push) processarPushRef.current(push);
      },
    );
    return () => inscricao.unsubscribe();
  }, [tenantId, conversaAtiva, estado]);

  return estado;
}
