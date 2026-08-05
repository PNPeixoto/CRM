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
import { obterApresentacao, obterPermissoes, salvarPerfilInicial } from './api';
import type {
  ApresentacaoDoTenant,
  PermissoesDoUsuario,
  SegmentoDeNegocio,
} from './tipos';

interface TenantPresentationContextValue {
  readonly apresentacao: ApresentacaoDoTenant | null;
  readonly permissoes: PermissoesDoUsuario | null;
  readonly carregando: boolean;
  readonly erroAoCarregar: boolean;
  readonly recarregar: () => Promise<void>;
  readonly escolherSegmento: (segmento: SegmentoDeNegocio) => Promise<ApresentacaoDoTenant>;
}

const TenantPresentationContext = createContext<TenantPresentationContextValue | null>(null);

export function TenantPresentationProvider({ children }: { readonly children: ReactNode }) {
  const { usuario } = useAuth();
  const [apresentacao, setApresentacao] = useState<ApresentacaoDoTenant | null>(null);
  const [permissoes, setPermissoes] = useState<PermissoesDoUsuario | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erroAoCarregar, setErroAoCarregar] = useState(false);

  const recarregar = useCallback(async () => {
    if (!usuario) {
      setApresentacao(null);
      setPermissoes(null);
      setCarregando(false);
      setErroAoCarregar(false);
      return;
    }
    setCarregando(true);
    setErroAoCarregar(false);
    try {
      const [novaApresentacao, novasPermissoes] = await Promise.all([
        obterApresentacao(),
        obterPermissoes(),
      ]);
      setApresentacao(novaApresentacao);
      setPermissoes(novasPermissoes);
    } catch {
      // Sem os sinais de acesso, a interface falha fechada e não tenta
      // descobrir autorização chamando cada endpoint protegido.
      setApresentacao(null);
      setPermissoes(null);
      setErroAoCarregar(true);
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
    () => ({
      apresentacao,
      permissoes,
      carregando,
      erroAoCarregar,
      recarregar,
      escolherSegmento,
    }),
    [apresentacao, permissoes, carregando, erroAoCarregar, recarregar, escolherSegmento],
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
