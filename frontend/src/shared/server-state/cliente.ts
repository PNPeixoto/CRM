import { QueryClient } from '@tanstack/react-query';

export function criarClienteDeEstadoServidor(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
        // A camada HTTP já repete leituras transitórias com backoff. Repetir
        // de novo aqui multiplicaria chamadas e latência.
        retry: false,
      },
      mutations: { retry: false },
    },
  });
}
