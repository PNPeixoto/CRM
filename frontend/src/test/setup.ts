import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';

/**
 * Nenhum teste pode alcançar a rede por acidente. Cada teste que atravessa a
 * camada HTTP instala fixtures explícitas com `instalarHttpMock`.
 *
 * A mensagem contém somente método e caminho. Query, headers e corpo podem
 * carregar dado sensível e por isso não entram no diagnóstico de falha.
 */
beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit) => {
    const metodo = (init?.method ?? (entrada instanceof Request ? entrada.method : 'GET')).toUpperCase();
    const url = new URL(entrada instanceof Request ? entrada.url : entrada.toString(), window.location.origin);
    throw new Error(`Rede não simulada: ${metodo} ${url.pathname}`);
  }));
});

afterEach(() => {
  cleanup();

  if (vi.isFakeTimers()) {
    vi.clearAllTimers();
    vi.useRealTimers();
  }

  localStorage.clear();
  sessionStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  window.history.replaceState(null, '', '/');
  limparCookiesVisiveis();

  vi.restoreAllMocks();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

function limparCookiesVisiveis(): void {
  for (const parte of document.cookie.split(';')) {
    const nome = parte.split('=')[0]?.trim();
    if (!nome) continue;
    document.cookie = `${nome}=; Max-Age=0; path=/`;
    document.cookie = `${nome}=; Max-Age=0; path=/api`;
  }
}
