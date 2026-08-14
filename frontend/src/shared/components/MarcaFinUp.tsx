import { cn } from '@/lib/utils';

interface MarcaFinUpProps {
  readonly recolhida?: boolean;
  readonly tema?: 'claro' | 'escuro';
  readonly className?: string;
}

export function MarcaFinUp({
  recolhida = false,
  tema = 'escuro',
  className,
}: MarcaFinUpProps) {
  return (
    <div
      role="img"
      aria-label="FinUp, Plataforma CRM"
      className={cn('flex min-w-0 items-center gap-2.5', className)}
    >
      <img
        src="/finup-logo.png"
        alt=""
        aria-hidden="true"
        className="h-[30px] w-6 shrink-0 object-contain"
      />
      {!recolhida && (
        <span className="min-w-0 leading-tight">
          <span
            className={cn(
              'block text-sm font-bold',
              tema === 'escuro'
                ? 'text-[var(--text-on-shell)]'
                : 'text-[var(--text-strong)]',
            )}
          >
            FinUp
          </span>
          <span
            className={cn(
              'block text-[10px]',
              tema === 'escuro'
                ? 'text-[var(--text-on-shell-muted)]'
                : 'text-[var(--text-muted)]',
            )}
          >
            Plataforma CRM
          </span>
        </span>
      )}
    </div>
  );
}
