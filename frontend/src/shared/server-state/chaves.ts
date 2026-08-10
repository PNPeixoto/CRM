export interface ContextoDeEstadoServidor {
  readonly tenantId: string;
  readonly unidadeId: string | null;
  readonly identidadeId: string;
}

export type RecursoDeServidor =
  | 'apresentacao'
  | 'permissoes'
  | 'contextos-organizacionais'
  | 'contatos'
  | 'funis'
  | 'oportunidades'
  | 'tarefas'
  | 'canais'
  | 'auditoria'
  | 'visao-geral'
  | 'conversas'
  | 'mensagens';

const CONTEXTO_ANONIMO: ContextoDeEstadoServidor = {
  tenantId: 'sem-tenant',
  unidadeId: null,
  identidadeId: 'sem-identidade',
};

export function contextoSeguro(
  contexto: ContextoDeEstadoServidor | null,
): ContextoDeEstadoServidor {
  return contexto ?? CONTEXTO_ANONIMO;
}

export function prefixoDoRecurso(
  contexto: ContextoDeEstadoServidor,
  recurso: RecursoDeServidor,
) {
  return [
    'servidor',
    contexto.tenantId,
    contexto.unidadeId ?? 'tenant-inteiro',
    contexto.identidadeId,
    recurso,
  ] as const;
}

export function chaveDoRecurso(
  contexto: ContextoDeEstadoServidor,
  recurso: RecursoDeServidor,
  parametros: Readonly<Record<string, unknown>> = {},
) {
  return [...prefixoDoRecurso(contexto, recurso), parametros] as const;
}
