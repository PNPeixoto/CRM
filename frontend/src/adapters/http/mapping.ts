import { ApiError } from '@/lib/api';

export function obrigatorio<T>(valor: T | null | undefined, campo: string): T {
  if (valor === null || valor === undefined) {
    throw new ApiError({
      message: 'O servidor respondeu em um formato inesperado.',
      status: 200,
      kind: 'contract',
      codigo: `CAMPO_AUSENTE_${campo.toUpperCase()}`,
    });
  }
  return valor;
}

export function umDe<T extends string>(
  valor: string | null | undefined,
  permitidos: readonly T[],
  campo: string,
): T {
  if (valor !== undefined && valor !== null && permitidos.includes(valor as T)) return valor as T;
  throw new ApiError({
    message: 'O servidor respondeu em um formato inesperado.',
    status: 200,
    kind: 'contract',
    codigo: `VALOR_INVALIDO_${campo.toUpperCase()}`,
  });
}
