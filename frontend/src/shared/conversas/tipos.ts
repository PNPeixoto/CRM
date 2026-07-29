/**
 * Espelha os DTOs de `conversation/internal/ConversationDtos.java`.
 *
 * <p>Escrito à mão por enquanto. O `00-projeto.md` prevê OpenAPI como contrato
 * com client TypeScript gerado — quando isso existir, este arquivo é
 * substituído pelo gerado. Até lá, uma divergência entre backend e frontend só
 * aparece em tempo de execução, e é por isso que o REST é a fonte da verdade:
 * um campo renomeado quebra a tela, não corrompe dado.
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
