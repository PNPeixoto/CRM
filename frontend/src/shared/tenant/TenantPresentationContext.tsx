import {
  createContext,
  use,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useAuth } from '@/shared/auth/AuthContext';
import { obterApresentacao, salvarPerfilInicial } from './api';
import type { ApresentacaoDoTenant, SegmentoDeNegocio } from './tipos';

interface TenantPresentationContextValue {
  readonly apresentacao: ApresentacaoDoTenant | null;
  readonly carregando: boolean;
  readonly recarregar: () => Promise<void>;
  readonly escolherSegmento: (segmento: SegmentoDeNegocio) => Promise<ApresentacaoDoTenant>;
}

const TenantPresentationContext = createContext<TenantPresentationContextValue | null>(null);

export function TenantPresentationProvider({ children }: { readonly children: ReactNode }) {
  const { usuario } = useAuth();
  const [apresentacao, setApresentacao] = useState<ApresentacaoDoTenant | null>(null);
  const [carregando, setCarregando] = useState(false);

  const recarregar = useCallback(async () => {
    if (!usuario) {
      setApresentacao(null);
      setCarregando(false);
      return;
    }
    setCarregando(true);
    try {
      setApresentacao(await obterApresentacao());
    } finally {
      setCarregando(false);
    }
  }, [usuario]);

  useEffect(() => {
    void recarregar();
  }, [recarregar]);

  const escolherSegmento = useCallback(async (segmento: SegmentoDeNegocio) => {
    const atualizada = await salvarPerfilInicial(segmento);
    // A resposta substitui o estado imediatamente; não há novo login e nem
    // janela em que menu e roteador usem presets diferentes.
    setApresentacao(atualizada);
    return atualizada;
  }, []);

  const value = useMemo(
    () => ({ apresentacao, carregando, recarregar, escolherSegmento }),
    [apresentacao, carregando, recarregar, escolherSegmento],
  );

  return <TenantPresentationContext value={value}>{children}</TenantPresentationContext>;
}

export function useTenantPresentation(): TenantPresentationContextValue {
  const context = use(TenantPresentationContext);
  if (!context) {
    throw new Error('useTenantPresentation precisa estar dentro do provider.');
  }
  return context;
}
