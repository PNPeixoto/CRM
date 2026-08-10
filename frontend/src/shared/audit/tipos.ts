export type AcaoAuditoria =
  | 'AUTHORIZATION_DENIED'
  | 'AUDIT_READ'
  | 'EXPORT_REQUESTED'
  | 'EXPORT_COMPLETED'
  | 'CREDENTIAL_CHANGED'
  | 'ROLE_CHANGED'
  | 'SENSITIVE_CONFIGURATION_CHANGED';

export type ResultadoAuditoria = 'SUCCEEDED' | 'DENIED' | 'FAILED';

export interface EventoAuditoria {
  readonly id: string;
  readonly ocorridoEm: string;
  readonly acao: string;
  readonly resultado: ResultadoAuditoria;
  readonly motivo: string;
  readonly atorTipo: 'HUMAN' | 'SYSTEM';
  readonly atorId: string | null;
  readonly atorNome: string | null;
  readonly escopoTipo: string;
  readonly escopoId: string;
  readonly alvoTipo: string;
  readonly alvoId: string | null;
  readonly correlacaoId: string;
}

export interface PaginaAuditoria {
  readonly eventos: readonly EventoAuditoria[];
  readonly proximoCursor: string | null;
  readonly integridadeVerificada: boolean;
}

