import { createContext, use, useCallback, useMemo, type ReactNode } from 'react';
import { useAuth } from '@/shared/auth/AuthContext';
import {
  useApresentacaoDoTenant,
  useContextosOrganizacionais,
  usePermissoesDoUsuario,
  useSalvarPerfilInicial,
} from '@/shared/server-state/recursos';
import type {
  ApresentacaoDoTenant,
  ContextoAutorizadoDaOrganizacao,
  PermissoesDoUsuario,
  SegmentoDeNegocio,
} from './tipos';

interface TenantPresentationContextValue {
  readonly apresentacao: ApresentacaoDoTenant | null;
  readonly permissoes: PermissoesDoUsuario | null;
  readonly contextosNavegaveis: readonly ContextoAutorizadoDaOrganizacao[];
  readonly carregando: boolean;
  readonly erroAoCarregar: boolean;
  readonly recarregar: () => Promise<void>;
  readonly escolherSegmento: (segmento: SegmentoDeNegocio) => Promise<ApresentacaoDoTenant>;
}

const TenantPresentationContext = createContext<TenantPresentationContextValue | null>(null);

export function TenantPresentationProvider({ children }: { readonly children: ReactNode }) {
  const { usuario } = useAuth();
  const apresentacaoQuery = useApresentacaoDoTenant();
  const permissoesQuery = usePermissoesDoUsuario();
  const contextosQuery = useContextosOrganizacionais();
  const salvarPerfil = useSalvarPerfilInicial();

  const apresentacao = apresentacaoQuery.data ?? null;
  const permissoes = permissoesQuery.data ?? null;

  // UNIT ainda não é um contexto de consulta dos agregados do CRM (ADR-0008).
  // Exibi-la como selecionável rotularia dados do tenant inteiro como se fossem
  // de uma unidade. Consumimos o contrato real e expomos somente o contexto que
  // o backend consegue aplicar de ponta a ponta hoje.
  const contextosNavegaveis = useMemo(
    () => (contextosQuery.data?.contextos ?? []).filter(
      (contexto) => contexto.tipo === 'TENANT' && contexto.id === usuario?.tenantId,
    ),
    [contextosQuery.data, usuario?.tenantId],
  );

  const carregando = Boolean(usuario) && (
    apresentacaoQuery.isPending || permissoesQuery.isPending || contextosQuery.isPending
  );
  const erroAoCarregar = Boolean(usuario) && (
    apresentacaoQuery.isError || permissoesQuery.isError || contextosQuery.isError
  );

  const recarregar = useCallback(async () => {
    if (!usuario) return;
    await Promise.all([
      apresentacaoQuery.refetch(),
      permissoesQuery.refetch(),
      contextosQuery.refetch(),
    ]);
  }, [usuario, apresentacaoQuery, permissoesQuery, contextosQuery]);

  const escolherSegmento = useCallback(
    (segmento: SegmentoDeNegocio) => salvarPerfil.mutateAsync(segmento),
    [salvarPerfil],
  );

  const value = useMemo(
    () => ({
      apresentacao,
      permissoes,
      contextosNavegaveis,
      carregando,
      erroAoCarregar,
      recarregar,
      escolherSegmento,
    }),
    [
      apresentacao,
      permissoes,
      contextosNavegaveis,
      carregando,
      erroAoCarregar,
      recarregar,
      escolherSegmento,
    ],
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
