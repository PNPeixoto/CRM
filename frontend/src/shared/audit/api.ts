import { api } from '@/lib/api';
import type { AcaoAuditoria, PaginaAuditoria, ResultadoAuditoria } from './tipos';

export interface FiltrosAuditoria {
  readonly acao: AcaoAuditoria | '';
  readonly resultado: ResultadoAuditoria | '';
}

export const auditoriaApi = {
  listar: (
    filtros: FiltrosAuditoria,
    cursor: string | null,
    signal?: AbortSignal,
  ) => {
    const parametros = new URLSearchParams({ limite: '25' });
    if (filtros.acao) parametros.set('acao', filtros.acao);
    if (filtros.resultado) parametros.set('resultado', filtros.resultado);
    if (cursor) parametros.set('cursor', cursor);
    return api.get<PaginaAuditoria>(`/auditoria?${parametros.toString()}`, { signal });
  },
};

