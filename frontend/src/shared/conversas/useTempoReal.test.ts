import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { MensagemPush, PaginaEventos } from './tipos';

const mocks = vi.hoisted(() => ({
  clientes: [] as unknown[],
  eventos: vi.fn(),
  renovar: vi.fn(),
  token: 'access-token' as string | null,
}));

vi.mock('@stomp/stompjs', () => {
  class ClienteFalso {
    connected = false;
    connectHeaders: Record<string, string> = {};
    inscricoes = new Map<string, (frame: { body: string }) => void>();
    beforeConnect: () => Promise<void> = async () => undefined;
    onConnect: () => void = () => undefined;
    onStompError: () => void = () => undefined;
    onWebSocketClose: () => void = () => undefined;
    reconnectDelay = 0;
    maxReconnectDelay = 0;
    reconnectTimeMode = '';

    constructor(configuracao: Record<string, unknown>) {
      Object.assign(this, configuracao);
      mocks.clientes.push(this);
    }

    activate() {}
    async deactivate() { this.connected = false; }
    subscribe(destino: string, callback: (frame: { body: string }) => void) {
      this.inscricoes.set(destino, callback);
      return { unsubscribe: () => this.inscricoes.delete(destino) };
    }
  }
  return {
    Client: ClienteFalso,
    ReconnectionTimeMode: { EXPONENTIAL: 'EXPONENTIAL' },
  };
});

vi.mock('@/lib/api', () => ({
  obterAccessToken: () => mocks.token,
  tentarRenovarSessao: mocks.renovar,
}));

vi.mock('./api', () => ({
  conversasApi: { eventos: mocks.eventos },
}));

import { decidirSequencia, useTempoReal } from './useTempoReal';

interface ClienteDeTeste {
  connected: boolean;
  connectHeaders: Record<string, string>;
  inscricoes: Map<string, (frame: { body: string }) => void>;
  beforeConnect: () => Promise<void>;
  onConnect: () => void;
  onStompError: () => void;
  reconnectDelay: number;
  maxReconnectDelay: number;
  reconnectTimeMode: string;
}

describe('tempo real resiliente da Inbox', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    mocks.clientes.length = 0;
    mocks.token = 'access-token';
    mocks.eventos.mockResolvedValue(pagina(0));
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
  });

  it('classifica duplicata, próximo evento e lacuna', () => {
    expect(decidirSequencia(8, 8)).toBe('duplicado');
    expect(decidirSequencia(8, 7)).toBe('duplicado');
    expect(decidirSequencia(8, 9)).toBe('proximo');
    expect(decidirSequencia(8, 10)).toBe('lacuna');
  });

  it('usa backoff exponencial com jitter e renova antes do CONNECT', async () => {
    mocks.token = null;
    renderHook(() => useHookDeTeste({ sequenciaInicial: 0 }));
    const cliente = clienteAtual();

    expect(cliente.reconnectTimeMode).toBe('EXPONENTIAL');
    expect(cliente.reconnectDelay).toBeGreaterThanOrEqual(1_000);
    expect(cliente.reconnectDelay).toBeLessThan(1_500);
    expect(cliente.maxReconnectDelay).toBe(30_000);

    await cliente.beforeConnect();
    expect(mocks.renovar).toHaveBeenCalledOnce();
    expect(cliente.connectHeaders.Authorization).toBe('Bearer ');
  });

  it('deduplica push e recupera lacuna fora de ordem antes de sincronizar', async () => {
    const aoReceber = vi.fn();
    const aoSincronizar = vi.fn();
    mocks.eventos.mockResolvedValue(pagina(5));
    renderHook(() => useHookDeTeste({ sequenciaInicial: 5, aoReceber, aoSincronizar }));
    const cliente = clienteAtual();

    await act(async () => {
      cliente.connected = true;
      cliente.onConnect();
    });
    await waitFor(() => expect(mocks.eventos).toHaveBeenCalledWith(5, expect.any(AbortSignal)));

    emitir(cliente, push(5));
    emitir(cliente, push(6));
    expect(aoReceber).toHaveBeenCalledTimes(1);

    mocks.eventos.mockResolvedValueOnce(pagina(8, [evento(8), evento(7)]));
    emitir(cliente, push(8));
    await waitFor(() => expect(aoSincronizar).toHaveBeenCalledOnce());
    expect(mocks.eventos).toHaveBeenLastCalledWith(6, expect.any(AbortSignal));

    emitir(cliente, push(7));
    expect(aoReceber).toHaveBeenCalledTimes(1);
  });

  it('faz polling mais lento em background e pausa quando offline', async () => {
    vi.useFakeTimers();
    mocks.eventos.mockResolvedValue(pagina(0));
    renderHook(() => useHookDeTeste({ sequenciaInicial: 0 }));

    await act(async () => vi.advanceTimersByTimeAsync(5_000));
    expect(mocks.eventos).toHaveBeenCalledTimes(1);

    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    document.dispatchEvent(new Event('visibilitychange'));
    await act(async () => vi.advanceTimersByTimeAsync(0));
    expect(mocks.eventos).toHaveBeenCalledTimes(2);

    await act(async () => vi.advanceTimersByTimeAsync(29_000));
    expect(mocks.eventos).toHaveBeenCalledTimes(2);

    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false });
    await act(async () => vi.advanceTimersByTimeAsync(1_000));
    expect(mocks.eventos).toHaveBeenCalledTimes(2);
  });
});

function useHookDeTeste(opcoes: {
  readonly sequenciaInicial: number;
  readonly aoReceber?: (push: MensagemPush) => void;
  readonly aoSincronizar?: () => void;
}) {
  return useTempoReal({
    tenantId: 'tenant-a',
    conversaAtiva: null,
    sequenciaInicial: opcoes.sequenciaInicial,
    aoReceberMensagem: opcoes.aoReceber ?? (() => undefined),
    aoSincronizar: opcoes.aoSincronizar ?? (() => undefined),
  });
}

function clienteAtual(): ClienteDeTeste {
  return mocks.clientes.at(-1) as ClienteDeTeste;
}

function emitir(cliente: ClienteDeTeste, mensagem: MensagemPush) {
  act(() => cliente.inscricoes.get('/topic/tenant/tenant-a/inbox')?.({
    body: JSON.stringify(mensagem),
  }));
}

function push(sequence: number): MensagemPush {
  return {
    eventId: `evento-${sequence}`,
    sequence,
    tipo: 'MESSAGE_CREATED',
    conversationId: 'conversa-1',
    messageId: `mensagem-${sequence}`,
    versao: sequence,
    ocorridoEm: '2026-08-09T12:00:00Z',
  };
}

function evento(sequence: number) {
  return {
    id: `evento-${sequence}`,
    sequence,
    tipo: 'MESSAGE_CREATED',
    conversationId: 'conversa-1',
    messageId: `mensagem-${sequence}`,
    versao: sequence,
    ocorridoEm: '2026-08-09T12:00:00Z',
  };
}

function pagina(
  ultimaSequencia: number,
  eventos: PaginaEventos['eventos'] = [],
): PaginaEventos {
  return { eventos, ultimaSequencia, temMais: false, resetObrigatorio: false };
}
