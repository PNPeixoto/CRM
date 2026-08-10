/**
 * Modelos da apresentação. DTOs de transporte vêm do OpenAPI gerado e são
 * convertidos em `shared/crm/api.ts`; as telas não interpretam envelopes HTTP.
 * Valores monetários são centavos inteiros — ver `shared/formato.ts`.
 */

export interface Contato {
  readonly id: string;
  readonly nome: string;
  readonly email: string | null;
  readonly telefone: string | null;
  readonly empresa: string | null;
  readonly observacoes: string | null;
  readonly responsavelId: string | null;
  readonly responsavelLogin: string | null;
  readonly responsavelNome: string | null;
  readonly criadoEm: string;
}

export interface Etapa {
  readonly id: string;
  readonly nome: string;
  readonly posicao: number;
  readonly ganho: boolean;
  readonly perda: boolean;
}

export interface Funil {
  readonly id: string;
  readonly nome: string;
  readonly padrao: boolean;
  readonly etapas: readonly Etapa[];
}

export type StatusOportunidade = 'OPEN' | 'WON' | 'LOST';

export interface Oportunidade {
  readonly id: string;
  readonly funilId: string;
  readonly etapaId: string;
  readonly contatoId: string | null;
  readonly titulo: string;
  readonly valorCentavos: number;
  readonly status: StatusOportunidade;
  readonly previsaoFechamento: string | null;
  readonly motivoPerda: string | null;
  readonly responsavelId: string | null;
  readonly responsavelLogin: string | null;
  readonly responsavelNome: string | null;
  readonly criadaEm: string;
}

export interface Tarefa {
  readonly id: string;
  readonly titulo: string;
  readonly descricao: string | null;
  readonly vencimentoEm: string | null;
  readonly concluidaEm: string | null;
  readonly responsavelId: string | null;
  readonly responsavelLogin: string | null;
  readonly responsavelNome: string | null;
  readonly contatoId: string | null;
  readonly oportunidadeId: string | null;
  readonly criadaEm: string;
}

export type TipoCanal =
  | 'LIVE_CHAT'
  | 'TELEGRAM'
  | 'WHATSAPP_CLOUD'
  | 'WHATSAPP_EVOLUTION'
  | 'INSTAGRAM';
export type EstadoRemotoCanal = 'CHECKING' | 'HEALTHY' | 'DEGRADED' | 'REPAIRED' | 'ERROR';

export interface Canal {
  readonly id: string;
  readonly tipo: TipoCanal;
  readonly nome: string;
  readonly identificadorExterno: string | null;
  readonly ativo: boolean;
  /** O backend nunca devolve o segredo — apenas se existe. */
  readonly temToken: boolean;
  readonly temSegredoWebhook: boolean;
  readonly estadoRemoto: EstadoRemotoCanal | null;
  readonly pendenciasRemotas: number | null;
  readonly ultimaReconciliacaoEm: string | null;
  readonly ultimaFalhaRemotaEm: string | null;
}

/**
 * Material de pareamento do WhatsApp Evolution.
 *
 * <p>Isto é credencial de sessão: quem tiver o QR ou o código passa a enviar
 * como aquele número. Nunca é persistido, nem em memória além do necessário
 * para exibir — a resposta vem `no-store` e a tela o descarta ao fechar.
 *
 * <p>`qrCode` e `codigo` vêm nulos quando a sessão já está aberta: não há o
 * que parear, e o backend recusa emitir material novo para não derrubar uma
 * conexão em uso.
 */
export interface PareamentoCanal {
  readonly estado: string;
  readonly qrCode: string | null;
  readonly codigo: string | null;
  readonly tentativa: number;
}

export interface VisaoGeral {
  readonly conversasAbertas: number;
  readonly conversasAguardando: number;
  readonly mensagensRecebidasHoje: number;
  readonly totalDeContatos: number;
  readonly oportunidadesAbertas: number;
  readonly valorAbertoCentavos: number;
  readonly oportunidadesGanhas: number;
  readonly valorGanhoCentavos: number;
  readonly oportunidadesPerdidas: number;
  readonly tarefasAbertas: number;
  readonly tarefasAtrasadas: number;
  readonly canaisAtivos: number;
  readonly taxaDeConversaoPercentual: number;
}
