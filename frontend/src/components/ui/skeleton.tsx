import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

/** Forma visual de carregamento; o estado acessível é anunciado pelo contêiner. */
export function Esqueleto({ className, ...props }: ComponentProps<'span'>) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'block animate-pulse rounded-[var(--radius-control)] bg-[var(--surface-sunken)]',
        className,
      )}
      {...props}
    />
  );
}
