import { AlertCircle } from 'lucide-react';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

/**
 * Alerta de erro.
 *
 * `role="alert"` faz o leitor de tela anunciar o conteúdo assim que ele
 * aparece, sem esperar o foco chegar — sem isso, quem navega por teclado
 * submete o formulário e não recebe retorno nenhum.
 *
 * O ícone acompanha a cor de propósito: status que depende só de cor é
 * invisível para daltônicos, e a regra do 00-projeto.md §7 é explícita
 * quanto a isso.
 */
export function AlertaErro({ className, children, ...props }: ComponentProps<'div'>) {
  return (
    <div
      role="alert"
      className={cn(
        'flex items-start gap-2 rounded-[var(--radius-control)] border border-[var(--danger)] bg-[var(--danger-soft)] px-3 py-2 text-sm text-[var(--danger)]',
        className,
      )}
      {...props}
    >
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}
