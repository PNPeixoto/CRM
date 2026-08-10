import { createContext, use } from 'react';
import type { ContextoDeEstadoServidor } from './chaves';

export const ContextoEstadoServidor = createContext<ContextoDeEstadoServidor | null>(null);

export function useContextoEstadoServidor(): ContextoDeEstadoServidor | null {
  return use(ContextoEstadoServidor);
}
