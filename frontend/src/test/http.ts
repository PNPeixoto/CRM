import { vi } from 'vitest';

export interface HttpFixture {
  readonly metodo?: string;
  readonly caminho: string;
  readonly status?: number;
  readonly json?: unknown;
  readonly corpo?: string;
  readonly headers?: HeadersInit;
  readonly vezes?: number;
}

export interface ChamadaHttp {
  readonly metodo: string;
  readonly caminho: string;
  /** Consulta pontual para não despejar headers inteiros no diagnóstico. */
  readonly cabecalho: (nome: string) => string | null;
}

/**
 * Simula HTTP na fronteira real da aplicação (`fetch`).
 *
 * Fixtures são locais e sintéticas. Chamada não declarada falha fechada e o
 * erro omite query, headers e corpo para não transformar a suíte em log de
 * dados sensíveis.
 */
export function instalarHttpMock(fixtures: readonly HttpFixture[]) {
  const pendentes = fixtures.map((fixture) => ({
    fixture,
    restantes: fixture.vezes ?? 1,
  }));
  const chamadas: ChamadaHttp[] = [];

  const fetchMock = vi.fn(async (
    entrada: RequestInfo | URL,
    init?: RequestInit,
  ): Promise<Response> => {
    const metodo = (
      init?.method ?? (entrada instanceof Request ? entrada.method : 'GET')
    ).toUpperCase();
    const url = new URL(
      entrada instanceof Request ? entrada.url : entrada.toString(),
      window.location.origin,
    );
    const caminho = `${url.pathname}${url.search}`;
    const headers = new Headers(
      init?.headers ?? (entrada instanceof Request ? entrada.headers : undefined),
    );

    chamadas.push({
      metodo,
      caminho,
      cabecalho: (nome) => headers.get(nome),
    });

    const item = pendentes.find(({ fixture, restantes }) =>
      restantes > 0
      && (fixture.metodo ?? 'GET').toUpperCase() === metodo
      && fixture.caminho === caminho,
    );

    if (!item) {
      throw new Error(`HTTP não simulado: ${metodo} ${url.pathname}`);
    }
    item.restantes -= 1;

    if (item.fixture.json !== undefined && item.fixture.corpo !== undefined) {
      throw new Error(`Fixture HTTP ambígua: ${metodo} ${url.pathname}`);
    }

    const status = item.fixture.status ?? 200;
    const responseHeaders = new Headers(item.fixture.headers);
    let corpo: BodyInit | null = null;

    if (status !== 204 && status !== 205) {
      if (item.fixture.json !== undefined) {
        responseHeaders.set('Content-Type', 'application/json');
        corpo = JSON.stringify(item.fixture.json);
      } else if (item.fixture.corpo !== undefined) {
        corpo = item.fixture.corpo;
      }
    }

    return new Response(corpo, { status, headers: responseHeaders });
  });

  vi.stubGlobal('fetch', fetchMock);

  return {
    chamadas,
    fetchMock,
    verificarTudoConsumido(): void {
      const restantes = pendentes
        .filter((item) => item.restantes > 0)
        .map(({ fixture, restantes }) =>
          `${(fixture.metodo ?? 'GET').toUpperCase()} ${fixture.caminho} (${restantes})`,
        );
      if (restantes.length > 0) {
        throw new Error(`Fixtures HTTP não consumidas: ${restantes.join(', ')}`);
      }
    },
  };
}
