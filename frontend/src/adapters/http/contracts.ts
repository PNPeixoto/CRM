/**
 * Única fronteira autorizada a importar o arquivo gerado.
 * Adaptadores convertem estes DTOs de transporte em modelos usados pelas telas.
 */
import type { components, operations } from '@/generated/api-schema';

type Schema<Nome extends keyof components['schemas']> = components['schemas'][Nome];

export type ApresentacaoWire = Schema<'ApresentacaoResponse'>;
export type CanalRequestWire = Schema<'CanalRequest'>;
export type CanalWire = Schema<'CanalResponse'>;
export type ContatoRequestWire = Schema<'ContatoRequest'>;
export type ContatoWire = Schema<'ContatoResponse'>;
export type ConversaWire = Schema<'ConversaResumo'>;
export type DealFunilWire = Schema<'DealFunilResponse'>;
export type LoginWire = Schema<'LoginRequest'>;
export type MensagemWire = Schema<'MensagemResposta'>;
export type MfaActivationRequestWire = Schema<'MfaActivationRequest'>;
export type MfaActivationResponseWire = Schema<'MfaActivationResponse'>;
export type MfaEnrollmentWire = Schema<'MfaEnrollmentResponse'>;
export type OportunidadeRequestWire = Schema<'OportunidadeRequest'>;
export type OportunidadeWire = Schema<'OportunidadeResponse'>;
export type PerfilInicialWire = Schema<'PerfilInicialRequest'>;
export type PermissoesWire = operations['permissoes']['responses'][200]['content']['*/*'];
export type SessaoWire = Schema<'SessaoResponse'>;
export type TarefaRequestWire = Schema<'TarefaRequest'>;
export type TarefaWire = Schema<'TarefaResponse'>;
export type UsuarioWire = Schema<'UsuarioResponse'>;
export type VisaoGeralWire = Schema<'VisaoGeralResponse'>;
