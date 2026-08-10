import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import {
  FronteiraDeEstadoServidor,
} from '@/shared/server-state/EstadoServidorProvider';
import { criarClienteDeEstadoServidor } from '@/shared/server-state/cliente';
import type { ContextoDeEstadoServidor } from '@/shared/server-state/chaves';

export const CONTEXTO_DE_TESTE: ContextoDeEstadoServidor = {
  tenantId: 'tenant-teste',
  unidadeId: null,
  identidadeId: 'usuario-teste',
};

export function renderComEstadoServidor(
  elemento: ReactElement,
  opcoes?: Omit<RenderOptions, 'wrapper'> & {
    readonly contexto?: ContextoDeEstadoServidor;
  },
) {
  const { contexto = CONTEXTO_DE_TESTE, ...renderOptions } = opcoes ?? {};
  const cliente = criarClienteDeEstadoServidor();
  return {
    cliente,
    ...render(elemento, {
      ...renderOptions,
      wrapper: ({ children }: { readonly children: ReactNode }) => (
        <FronteiraDeEstadoServidor contexto={contexto} cliente={cliente}>
          {children}
        </FronteiraDeEstadoServidor>
      ),
    }),
  };
}
