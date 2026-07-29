import * as LabelPrimitive from '@radix-ui/react-label';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

/**
 * Sobre o Radix e não sobre `<label>` puro: o primitivo garante a associação
 * com o controle mesmo quando o campo é um componente composto, que é onde o
 * `htmlFor` costuma se perder silenciosamente. Rótulo desassociado é campo
 * sem nome para o leitor de tela.
 */
export function Label({ className, ...props }: ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      className={cn(
        'text-sm font-medium text-[var(--text-strong)] peer-disabled:opacity-70',
        className,
      )}
      {...props}
    />
  );
}
