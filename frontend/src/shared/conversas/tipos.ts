/**
 * Modelos usados pela apresentação. O adaptador REST converte os DTOs do
 * OpenAPI gerado para esta forma e falha de modo seguro quando o contrato não
 * contém um campo obrigatório.
 */

export type StatusConversa = 'OPEN' | 'PENDING' | 'CLOSED';

export type DirecaoMensagem = 'INBOUND' | 'OUTBOUND';

export type StatusMensagem =
  | 'RECEIVED'
  | 'PENDING'
  | 'SENT'
  | 'DELIVERED'
  | 'READ'
  | 'FAILED';

export type TipoConteudo =
  | 'TEXT'
  | 'IMAGE'
  | 'AUDIO'
  | 'VIDEO'
  | 'DOCUMENT'
  | 'LOCATION'
  | 'OTHER';

export interface ConversaResumo {
  readonly id: string;
  readonly channelConnectionId: string;
  readonly canalTipo: TipoCanal | null;
  readonly canalNome: string | null;
  readonly canalIdentificador: string | null;
  readonly contatoNome: string | null;
  readonly contatoIdentificador: string;
  readonly status: StatusConversa;
  readonly atendenteId: string | null;
  readonly atendenteNome: string | null;
  readonly ultimaMensagemEm: string | null;
  readonly ultimaMensagemRecebidaEm: string | null;
  readonly venceEm: string | null;
  readonly versao: number;
}

export interface Mensagem {
  readonly id: string;
  readonly direcao: DirecaoMensagem;
  readonly tipoConteudo: TipoConteudo;
  readonly texto: string | null;
  readonly status: StatusMensagem;
  readonly autorId: string | null;
  readonly autorNome: string | null;
  readonly criadaEm: string;
  readonly versao: number;
}

export interface CursorTemporal {
  readonly antesDe: string;
  readonly antesDoId: string;
}

export interface PaginaConversas {
  readonly itens: readonly ConversaResumo[];
  readonly proximoCursor: CursorTemporal | null;
  readonly temMais: boolean;
  readonly sequenciaDoStream: number;
}

export interface PaginaMensagens {
  readonly itens: readonly Mensagem[];
  readonly proximoCursor: CursorTemporal | null;
  readonly temMais: boolean;
  readonly sequenciaDoStream: number;
}

export interface EventoTempoReal {
  readonly id: string;
  readonly sequence: number;
  readonly tipo: string;
  readonly conversationId: string;
  readonly messageId: string | null;
  readonly versao: number;
  readonly ocorridoEm: string;
}

export interface PaginaEventos {
  readonly eventos: readonly EventoTempoReal[];
  readonly temMais: boolean;
  readonly resetObrigatorio: boolean;
  readonly ultimaSequencia: number;
}

/**
 * O que trafega pelo WebSocket. Deliberadamente menor que {@link Mensagem} —
 * o backend envia só o necessário para atualizar a tela, e a mensagem
 * completa vem do REST.
 */
export interface MensagemPush {
  readonly eventId: string;
  readonly sequence: number;
  readonly tipo: string;
  readonly conversationId: string;
  readonly messageId: string | null;
  readonly versao: number;
  readonly ocorridoEm: string;
}
import type { TipoCanal } from '@/shared/crm/tipos';
