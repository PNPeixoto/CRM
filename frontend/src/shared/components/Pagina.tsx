import type { ReactNode } from 'react';

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
        className="flex shrink-0 items-center justify-between gap-4 border-b px-6 py-3"
        style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
      >
        <div className="min-w-0">
          <h1 className="truncate text-base font-semibold">{titulo}</h1>
          {descricao && (
            <p className="truncate text-sm" style={{ color: 'var(--text-muted)' }}>
              {descricao}
            </p>
          )}
        </div>
        {acoes && <div className="flex shrink-0 items-center gap-2">{acoes}</div>}
      </header>

      {/* min-h-0 é obrigatório num filho flex com overflow: sem ele a rolagem
          escapa para a página inteira. */}
      <div className="min-h-0 flex-1 overflow-y-auto p-6">{children}</div>
    </div>
  );
}

export function Vazio({ titulo, descricao }: { readonly titulo: string; readonly descricao: string }) {
  return (
    <div
      className="rounded-[var(--radius-surface)] border border-dashed p-10 text-center"
      style={{ borderColor: 'var(--border-strong)' }}
    >
      <p className="text-sm font-medium">{titulo}</p>
      <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
        {descricao}
      </p>
    </div>
  );
}

export function Carregando() {
  return (
    <p className="text-sm" style={{ color: 'var(--text-muted)' }} role="status">
      Carregando…
    </p>
  );
}

export function Cartao({ children, className }: { readonly children: ReactNode; readonly className?: string }) {
  return (
    <div
      className={`rounded-[var(--radius-surface)] border p-4 ${className ?? ''}`}
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      {children}
    </div>
  );
}
