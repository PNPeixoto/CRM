import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Junta classes do Tailwind resolvendo conflitos pela última ocorrência.
 *
 * Sem o `twMerge`, `cn('p-2', 'p-4')` produz as duas classes e o resultado
 * passa a depender da ordem no CSS gerado, não da ordem da chamada — que é
 * exatamente o que quebra a sobrescrita de estilo em componente reutilizável.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
