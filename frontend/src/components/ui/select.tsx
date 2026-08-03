import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

type SelectProps = ComponentProps<'select'> & {
  readonly tamanho?: 'padrao' | 'compacto';
};

/** Campo de seleção nativo: preserva teclado, leitor de tela e UI do sistema. */
export function Select({ className, tamanho = 'padrao', ...props }: SelectProps) {
  return (
    <select
      className={cn(
        'rounded-[var(--radius-control)] border border-[var(--border-control)] bg-[var(--surface-raised)] text-[var(--text-strong)]',
        'disabled:cursor-not-allowed disabled:opacity-50 aria-[invalid=true]:border-[var(--danger)]',
        tamanho === 'compacto'
          ? 'h-[var(--control-height-compact)] px-1.5 text-xs'
          : 'h-[var(--control-height)] px-2 text-sm',
        className,
      )}
      {...props}
    />
  );
}
