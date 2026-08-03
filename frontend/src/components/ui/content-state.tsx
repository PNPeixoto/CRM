import { AlertCircle, FileQuestion, Inbox, SearchX, ShieldAlert, WifiOff } from 'lucide-react';
import type { ReactNode } from 'react';
import { Esqueleto } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

export type TipoDeEstadoDeConteudo =
  | 'carregando'
  | 'vazio-inicial'
  | 'sem-resultados'
  | 'erro'
  | 'sem-permissao'
  | 'nao-encontrado'
  | 'offline';

interface EstadoDeConteudoProps {
  readonly tipo: TipoDeEstadoDeConteudo;
  readonly titulo?: string;
  readonly descricao?: string;
  readonly acao?: ReactNode;
  readonly className?: string;
}

const CONTEUDO_PADRAO: Record<
  Exclude<TipoDeEstadoDeConteudo, 'carregando'>,
  { readonly titulo: string; readonly descricao: string }
> = {
  'vazio-inicial': {
    titulo: 'Nada por aqui ainda',
    descricao: 'O primeiro registro aparecerá aqui quando for criado.',
  },
  'sem-resultados': {
    titulo: 'Nenhum resultado encontrado',
    descricao: 'Revise os filtros ou tente outro termo de busca.',
  },
  erro: {
    titulo: 'Não foi possível carregar',
    descricao: 'Tente novamente. Se o problema continuar, procure o suporte.',
  },
  'sem-permissao': {
    titulo: 'Acesso não permitido',
    descricao: 'Seu perfil não possui permissão para acessar este conteúdo.',
  },
  'nao-encontrado': {
    titulo: 'Página não encontrada',
    descricao: 'Confira o endereço ou use a navegação para continuar.',
  },
  offline: {
    titulo: 'Você está offline',
    descricao: 'Confira sua conexão antes de tentar novamente.',
  },
};

const ICONE = {
  'vazio-inicial': Inbox,
  'sem-resultados': SearchX,
  erro: AlertCircle,
  'sem-permissao': ShieldAlert,
  'nao-encontrado': FileQuestion,
  offline: WifiOff,
} as const;

/** Estados de conteúdo compartilham estrutura, sem confundir suas causas. */
export function EstadoDeConteudo({
  tipo,
  titulo,
  descricao,
  acao,
  className,
}: EstadoDeConteudoProps) {
  if (tipo === 'carregando') {
    return (
      <div
        role="status"
        aria-live="polite"
        aria-busy="true"
        className={cn('space-y-3 py-2', className)}
      >
        <span className="sr-only">{titulo ?? 'Carregando…'}</span>
        <Esqueleto className="h-4 w-2/5" />
        <Esqueleto className="h-20 w-full" />
      </div>
    );
  }

  const padrao = CONTEUDO_PADRAO[tipo];
  const Icone = ICONE[tipo];
  const ehErro = tipo === 'erro';
  const ehAvisoDeConectividade = tipo === 'offline';

  return (
    <div
      role={ehErro ? 'alert' : ehAvisoDeConectividade ? 'status' : undefined}
      className={cn(
        'rounded-[var(--radius-surface)] border border-dashed border-[var(--border-strong)] px-6 py-10 text-center',
        ehErro && 'border-[var(--danger)] bg-[var(--danger-soft)]',
        className,
      )}
    >
      <Icone
        className={cn(
          'mx-auto size-5',
          ehErro ? 'text-[var(--danger)]' : 'text-[var(--text-muted)]',
        )}
        aria-hidden="true"
      />
      <p className="mt-3 text-sm font-medium text-[var(--text-strong)]">
        {titulo ?? padrao.titulo}
      </p>
      <p className="mx-auto mt-1 max-w-lg text-sm text-[var(--text-muted)]">
        {descricao ?? padrao.descricao}
      </p>
      {acao && <div className="mt-4 flex justify-center">{acao}</div>}
    </div>
  );
}
