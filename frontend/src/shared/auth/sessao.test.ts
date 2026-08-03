import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, definirAccessToken, obterAccessToken } from '@/lib/api';
import { instalarHttpMock } from '@/test/http';
import { criarCanalDeSessao } from './canalDeSessao';

afterEach(() => {
  definirAccessToken(null);
  vi.restoreAllMocks();
});

describe('renovação de sessão', () => {
  it('atende uma rajada de expiração com um único refresh', async () => {
    definirAccessToken('token-vencido');

    const http = instalarHttpMock([
      // Dez leituras que expiram, dez que passam depois da renovação.
      { caminho: '/api/recurso', status: 401, json: {}, vezes: 10 },
      { metodo: 'POST', caminho: '/api/auth/refresh', json: { accessToken: 'token-novo' } },
      { caminho: '/api/recurso', json: { ok: true }, vezes: 10 },
    ]);

    const resultados = await Promise.all(
      Array.from({ length: 10 }, () => api.get<{ ok: boolean }>('/recurso')),
    );

    expect(resultados).toHaveLength(10);
    expect(resultados.every((r) => r.ok)).toBe(true);

    // O ponto do teste: dez cadeias concorrentes, UM refresh. Sem
    // single-flight, dez renovações simultâneas rodariam a família de refresh
    // tokens dez vezes e a detecção de reuso derrubaria a sessão do usuário —
    // uma falha que só aparece sob concorrência, ou seja, em produção.
    const refreshes = http.chamadas.filter((c) => c.caminho === '/api/auth/refresh');
    expect(refreshes).toHaveLength(1);

    expect(obterAccessToken()).toBe('token-novo');
    http.verificarTudoConsumido();
  });

  it('não repete a requisição original mais de uma vez por cadeia', async () => {
    definirAccessToken('token-vencido');

    // A renovação "funciona", mas o recurso segue devolvendo 401. Sem o
    // limite de uma tentativa por cadeia, isto seria um laço infinito.
    const http = instalarHttpMock([
      { caminho: '/api/recurso', status: 401, json: {}, vezes: 2 },
      { metodo: 'POST', caminho: '/api/auth/refresh', json: { accessToken: 'token-novo' } },
    ]);

    await expect(api.get('/recurso')).rejects.toBeInstanceOf(ApiError);

    expect(http.chamadas.filter((c) => c.caminho === '/api/recurso')).toHaveLength(2);
    expect(http.chamadas.filter((c) => c.caminho === '/api/auth/refresh')).toHaveLength(1);
    http.verificarTudoConsumido();
  });

  it('converge para estado sem sessão quando a renovação falha', async () => {
    definirAccessToken('token-vencido');

    const http = instalarHttpMock([
      { caminho: '/api/recurso', status: 401, json: {} },
      { metodo: 'POST', caminho: '/api/auth/refresh', status: 401, json: {} },
    ]);

    const falha = await api.get('/recurso').catch((e: unknown) => e);

    expect(falha).toBeInstanceOf(ApiError);
    expect((falha as ApiError).kind).toBe('unauthenticated');
    // Estado estável: sem token em memória, sem tentativa pendente.
    expect(obterAccessToken()).toBeNull();
    http.verificarTudoConsumido();
  });

  it('não tenta renovar a partir das próprias rotas de autenticação', async () => {
    // Renovar durante o login criaria recursão: o 401 do login dispararia um
    // refresh, cujo 401 dispararia outro.
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/login', status: 401, json: {} },
    ]);

    await expect(api.post('/auth/login', {})).rejects.toBeInstanceOf(ApiError);
    expect(http.chamadas.filter((c) => c.caminho === '/api/auth/refresh')).toHaveLength(0);
    http.verificarTudoConsumido();
  });
});

describe('canal de sessão entre abas', () => {
  it('transmite apenas o verbo, nunca token ou dado pessoal', async () => {
    const emissor = criarCanalDeSessao();
    const receptor = criarCanalDeSessao();

    const recebidas: unknown[] = [];
    const cancelar = receptor.aoReceber((evento) => recebidas.push(evento));

    emissor.anunciarSaida();
    await vi.waitFor(() => expect(recebidas).toHaveLength(1));

    expect(recebidas[0]).toEqual({ tipo: 'saiu' });
    // A carga inteira precisa caber em um verbo: nada de token, id, nome ou
    // e-mail. Qualquer script da mesma origem lê este canal.
    expect(Object.keys(recebidas[0] as object)).toEqual(['tipo']);

    cancelar();
    emissor.fechar();
    receptor.fechar();
  });

  it('ignora mensagem malformada publicada por outro script da origem', async () => {
    const emissor = criarCanalDeSessao();
    const receptor = criarCanalDeSessao();

    const recebidas: unknown[] = [];
    const cancelar = receptor.aoReceber((evento) => recebidas.push(evento));

    const canalCru = new BroadcastChannel('crm-pnp:sessao:v1');
    canalCru.postMessage('saiu');
    canalCru.postMessage({ tipo: 'entrou-de-fininho' });
    canalCru.postMessage(null);
    emissor.anunciarSaida();

    await vi.waitFor(() => expect(recebidas).toHaveLength(1));
    expect(recebidas[0]).toEqual({ tipo: 'saiu' });

    cancelar();
    canalCru.close();
    emissor.fechar();
    receptor.fechar();
  });
});
