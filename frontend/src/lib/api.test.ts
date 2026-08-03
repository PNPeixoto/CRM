import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  api,
  ApiError,
  definirAccessToken,
  montarConsultaPaginada,
} from './api';
import { instalarHttpMock } from '@/test/http';

afterEach(() => {
  definirAccessToken(null);
  vi.restoreAllMocks();
});

describe('camada HTTP', () => {
  it('centraliza URL, credenciais e correlation id', async () => {
    const http = instalarHttpMock([
      { caminho: '/api/recurso', json: { id: 'recurso-sintetico' } },
    ]);

    await expect(api.get<{ id: string }>('/recurso'))
      .resolves.toEqual({ id: 'recurso-sintetico' });
    expect(http.chamadas[0]).toMatchObject({ metodo: 'GET', caminho: '/api/recurso' });
    expect(http.chamadas[0]?.cabecalho('X-Correlation-ID')).toBeTruthy();
    http.verificarTudoConsumido();
  });

  it('converte JSON inválido em falha segura de contrato', async () => {
    instalarHttpMock([
      { caminho: '/api/contrato-invalido', corpo: '{', headers: { 'Content-Type': 'application/json' } },
    ]);

    const erro = await api.get('/contrato-invalido').catch((falha: unknown) => falha);
    expect(erro).toMatchObject({
      kind: 'contract',
      message: 'O servidor respondeu em um formato inesperado.',
    });
  });

  it.each([
    [400, 'bad_request'],
    [401, 'unauthenticated'],
    [403, 'forbidden'],
    [404, 'not_found'],
    [409, 'conflict'],
    [422, 'validation'],
    [429, 'rate_limited'],
    [500, 'server'],
  ] as const)('distingue o status %i como %s sem exibir detail cru', async (status, kind) => {
    instalarHttpMock([{
      metodo: 'POST',
      caminho: `/api/auth/falha-${status}`,
      status,
      headers: { 'X-Correlation-ID': 'corr-header' },
      json: {
        type: '/problemas/exemplo',
        title: 'Título do servidor',
        status,
        detail: 'DETALHE CRU QUE NÃO DEVE APARECER',
        instance: '/api/recurso',
        codigo: 'EXEMPLO',
        correlacaoId: 'corr-body',
        campos: [{ campo: 'nome', mensagem: 'Informe o nome.' }],
      },
    }]);

    const erro = await api.post(`/auth/falha-${status}`).catch((falha: unknown) => falha);
    expect(erro).toBeInstanceOf(ApiError);
    expect(erro).toMatchObject({ status, kind, codigo: 'EXEMPLO', correlacaoId: 'corr-body' });
    expect((erro as ApiError).message).not.toContain('DETALHE CRU');
    expect((erro as ApiError).campos).toEqual([{ campo: 'nome', mensagem: 'Informe o nome.' }]);
  });

  it('cancela uma requisição pelo AbortSignal do chamador', async () => {
    vi.stubGlobal('fetch', vi.fn((_entrada: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('abort', 'AbortError')));
      })));
    const controller = new AbortController();
    const pendente = api.get('/recurso', { signal: controller.signal });
    controller.abort();

    await expect(pendente).rejects.toMatchObject({ kind: 'cancelled' });
  });

  it('interrompe por timeout configurável', async () => {
    vi.stubGlobal('fetch', vi.fn((_entrada: RequestInfo | URL, init?: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('abort', 'AbortError')));
      })));

    await expect(api.post('/auth/lento', undefined, { timeoutMs: 5 }))
      .rejects.toMatchObject({ kind: 'timeout' });
  });

  it('repete leitura transitória com limite e backoff', async () => {
    vi.spyOn(Math, 'random').mockReturnValue(0);
    const http = instalarHttpMock([
      { caminho: '/api/instavel', status: 503, vezes: 2 },
      { caminho: '/api/instavel', json: { ok: true } },
    ]);

    await expect(api.get('/instavel')).resolves.toEqual({ ok: true });
    expect(http.fetchMock).toHaveBeenCalledTimes(3);
  });

  it('não repete escrita sem idempotência explícita', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/escrita', status: 503 },
    ]);

    await expect(api.post('/escrita', { nome: 'teste' }))
      .rejects.toMatchObject({ kind: 'server' });
    expect(http.fetchMock).toHaveBeenCalledTimes(1);
  });

  it('repete escrita somente com chave de idempotência estável', async () => {
    vi.spyOn(Math, 'random').mockReturnValue(0);
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/escrita', status: 503 },
      { metodo: 'POST', caminho: '/api/escrita', json: { id: 'feito' } },
    ]);

    await expect(api.post('/escrita', { nome: 'teste' }, {
      retry: 'idempotent-write',
      idempotencyKey: 'operacao-123',
    })).resolves.toEqual({ id: 'feito' });
    expect(http.chamadas).toHaveLength(2);
    expect(http.chamadas.every((chamada) =>
      chamada.cabecalho('Idempotency-Key') === 'operacao-123')).toBe(true);
  });

  it('compartilha um único refresh entre dez respostas 401 concorrentes', async () => {
    let liberarRefresh!: (resposta: Response) => void;
    const refreshPendente = new Promise<Response>((resolve) => { liberarRefresh = resolve; });
    let refreshes = 0;

    const fetchMock = vi.fn(async (
      entrada: RequestInfo | URL,
      init?: RequestInit,
    ): Promise<Response> => {
      const url = new URL(
        entrada instanceof Request ? entrada.url : entrada.toString(),
        window.location.origin,
      );
      if (url.pathname === '/api/auth/refresh') {
        refreshes += 1;
        return refreshPendente;
      }
      if (!url.pathname.startsWith('/api/recurso/')) throw new Error('HTTP não simulado');
      const headers = new Headers(init?.headers);
      if (!headers.has('Authorization')) return new Response(null, { status: 401 });
      return new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const requisicoes = Array.from({ length: 10 }, (_, indice) =>
      api.get<{ ok: boolean }>(`/recurso/${indice}`));
    await vi.waitFor(() => expect(refreshes).toBe(1));
    liberarRefresh(new Response(JSON.stringify({ accessToken: 'acesso-sintetico' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    await expect(Promise.all(requisicoes))
      .resolves.toEqual(Array.from({ length: 10 }, () => ({ ok: true })));
    expect(refreshes).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(21);
  });

  it('normaliza paginação, ordenação e filtros e limita size', () => {
    expect(montarConsultaPaginada({
      page: 2,
      size: 50,
      sort: ['nome,asc', 'criadoEm,desc'],
      filters: { busca: 'ana', ativo: true, vazio: null },
    })).toBe('page=2&size=50&sort=nome%2Casc&sort=criadoEm%2Cdesc&busca=ana&ativo=true');
    expect(() => montarConsultaPaginada({ size: 101 })).toThrow(RangeError);
  });
});
