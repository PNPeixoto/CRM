import type { ReactNode } from 'react';
import {
  EstadoDeConteudo,
  type TipoDeEstadoDeConteudo,
} from '@/components/ui/content-state';
import { cn } from '@/lib/utils';

/**
 * Casca comum das páginas de conteúdo: cabeçalho fixo e corpo rolável.
 *
 * Centralizada porque a alternativa — cada página repetir a estrutura — faz o
 * espaçamento divergir aos poucos, e o produto passa a parecer montado por
 * pessoas diferentes.
 */
export function Pagina({
  titulo,
  descricao,
  acoes,
  children,
}: {
  readonly titulo: string;
  readonly descricao?: string;
  readonly acoes?: ReactNode;
  readonly children: ReactNode;
}) {
  return (
    <div className="flex h-full flex-col">
      <header
        className="flex shrink-0 items-center justify-between gap-4 border-b border-[var(--border-subtle)] bg-[var(--surface-raised)] px-[var(--space-page)] py-3"
      >
        <div className="min-w-0">
          <h1 className="truncate text-base font-semibold">{titulo}</h1>
          {descricao && (
            <p className="truncate text-sm text-[var(--text-muted)]">
              {descricao}
            </p>
          )}
        </div>
        {acoes && <div className="flex shrink-0 items-center gap-2">{acoes}</div>}
      </header>

      {/* min-h-0 é obrigatório num filho flex com overflow: sem ele a rolagem
          escapa para a página inteira. */}
      <div className="min-h-0 flex-1 overflow-y-auto p-[var(--space-page)]">{children}</div>
    </div>
  );
}

export function Vazio({
  titulo,
  descricao,
  tipo = 'vazio-inicial',
}: {
  readonly titulo: string;
  readonly descricao: string;
  readonly tipo?: Extract<TipoDeEstadoDeConteudo, 'vazio-inicial' | 'sem-resultados'>;
}) {
  return <EstadoDeConteudo tipo={tipo} titulo={titulo} descricao={descricao} />;
}

export function Carregando({ rotulo = 'Carregando…' }: { readonly rotulo?: string }) {
  return <EstadoDeConteudo tipo="carregando" titulo={rotulo} />;
}

export function Cartao({ children, className }: { readonly children: ReactNode; readonly className?: string }) {
  return (
    <div
      className={cn(
        'rounded-[var(--radius-surface)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4',
        className,
      )}
    >
      {children}
    </div>
  );
}
