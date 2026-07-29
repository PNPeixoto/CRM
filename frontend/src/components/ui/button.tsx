import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

/**
 * Trazido do FinUp (shadcn/ui sobre Radix) com a paleta REMAPEADA para os
 * tokens do CRM. Copiar o arquivo original importaria a paleta do FinUp junto,
 * que é o erro que o 05-reaproveitamento-finup.md avisa.
 *
 * O `asChild` do Radix existe para renderizar o estilo de botão sobre outro
 * elemento — um `<Link>`, por exemplo — sem aninhar `<a>` dentro de
 * `<button>`, que é HTML inválido e quebra a navegação por teclado.
 */
const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[var(--radius-control)] text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary: 'bg-[var(--brand)] text-[var(--text-on-brand)] hover:bg-[var(--brand-hover)]',
        secundario:
          'bg-[var(--surface-raised)] text-[var(--text-strong)] border border-[var(--border-subtle)] hover:bg-[var(--surface-sunken)]',
        fantasma: 'text-[var(--text-strong)] hover:bg-[var(--surface-sunken)]',
        destrutivo: 'bg-[var(--danger)] text-[var(--text-on-brand)] hover:opacity-90',
      },
      size: {
        // 40px de altura: acima do mínimo de área de toque que o WCAG pede.
        padrao: 'h-10 px-4 py-2',
        pequeno: 'h-9 px-3',
        grande: 'h-11 px-6',
        icone: 'h-10 w-10',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'padrao',
    },
  },
);

type ButtonProps = ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
  };

export function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Componente = asChild ? Slot : 'button';
  return (
    <Componente className={cn(buttonVariants({ variant, size, className }))} {...props} />
  );
}

export { buttonVariants };
