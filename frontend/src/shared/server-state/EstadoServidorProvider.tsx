import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useAuth } from '@/shared/auth/AuthContext';
import type { ContextoDeEstadoServidor } from './chaves';
import { criarClienteDeEstadoServidor } from './cliente';
import { ContextoEstadoServidor } from './contexto';
import { registrarClienteDeEstadoServidor } from './registro';

export function FronteiraDeEstadoServidor({
  contexto,
  children,
  cliente,
}: {
  readonly contexto: ContextoDeEstadoServidor | null;
  readonly children: ReactNode;
  readonly cliente?: QueryClient;
}) {
  const [clienteAtivo] = useState(() => cliente ?? criarClienteDeEstadoServidor());

  useEffect(() => {
    const removerRegistro = registrarClienteDeEstadoServidor(clienteAtivo);
    return () => {
      removerRegistro();
      void clienteAtivo.cancelQueries();
      clienteAtivo.clear();
    };
  }, [clienteAtivo]);

  return (
    <QueryClientProvider client={clienteAtivo}>
      <ContextoEstadoServidor value={contexto}>{children}</ContextoEstadoServidor>
    </QueryClientProvider>
  );
}

export function EstadoServidorProvider({ children }: { readonly children: ReactNode }) {
  const { usuario } = useAuth();
  const contexto = useMemo<ContextoDeEstadoServidor | null>(
    () => usuario
      ? { tenantId: usuario.tenantId, unidadeId: null, identidadeId: usuario.id }
      : null,
    [usuario],
  );
  const identidade = contexto
    ? `${contexto.tenantId}:${contexto.unidadeId ?? 'tenant-inteiro'}:${contexto.identidadeId}`
    : 'anonimo';

  // A chave cria uma fronteira física nova. Assim, dados da identidade antiga
  // não conseguem aparecer nem por um frame sob o rótulo da nova identidade.
  return (
    <FronteiraDeEstadoServidor key={identidade} contexto={contexto}>
      {children}
    </FronteiraDeEstadoServidor>
  );
}
