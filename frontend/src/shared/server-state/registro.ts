import type { QueryClient } from '@tanstack/react-query';

const clientesAtivos = new Set<QueryClient>();

export function registrarClienteDeEstadoServidor(cliente: QueryClient): () => void {
  clientesAtivos.add(cliente);
  return () => clientesAtivos.delete(cliente);
}

/**
 * Interrompe requisições e apaga dados remotos antes de a interface adotar
 * outra identidade. A limpeza é deliberadamente só em memória: não existe
 * persistência offline do cache.
 */
export function limparCachesDeServidor(): void {
  for (const cliente of clientesAtivos) {
    void cliente.cancelQueries();
    cliente.clear();
  }
}
