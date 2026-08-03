import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

export function Input({ className, type, ...props }: ComponentProps<'input'>) {
  return (
    <input
      type={type}
      className={cn(
        'flex h-[var(--control-height)] w-full rounded-[var(--radius-control)] border border-[var(--border-control)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-strong)]',
        'placeholder:text-[var(--text-muted)]',
        'disabled:cursor-not-allowed disabled:opacity-50',
        // Sinaliza erro pelo aria-invalid, e não por uma prop própria: assim a
        // borda vermelha e a informação lida pelo leitor de tela vêm sempre do
        // mesmo lugar e não têm como divergir.
        'aria-[invalid=true]:border-[var(--danger)]',
        className,
      )}
      {...props}
    />
  );
}
