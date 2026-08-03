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
  readonly contatoNome: string | null;
  readonly status: StatusConversa;
  readonly atendenteId: string | null;
  readonly ultimaMensagemEm: string | null;
}

export interface Mensagem {
  readonly id: string;
  readonly direcao: DirecaoMensagem;
  readonly tipoConteudo: TipoConteudo;
  readonly texto: string | null;
  readonly status: StatusMensagem;
  readonly autorId: string | null;
  readonly criadaEm: string;
}

/**
 * O que trafega pelo WebSocket. Deliberadamente menor que {@link Mensagem} —
 * o backend envia só o necessário para atualizar a tela, e a mensagem
 * completa vem do REST.
 */
export interface MensagemPush {
  readonly conversationId: string;
  readonly messageId: string;
  readonly direcao: DirecaoMensagem;
  readonly texto: string | null;
  readonly ocorridoEm: string;
}
