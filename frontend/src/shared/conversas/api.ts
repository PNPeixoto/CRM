import type {
  ConversaWire,
  EventoWire,
  MensagemWire,
  PaginaConversasWire,
  PaginaEventosWire,
  PaginaMensagensWire,
} from '@/adapters/http/contracts';
import { obrigatorio, umDe } from '@/adapters/http/mapping';
import { api } from '@/lib/api';
import type {
  ConversaResumo,
  CursorTemporal,
  EventoTempoReal,
  Mensagem,
  PaginaConversas,
  PaginaEventos,
  PaginaMensagens,
} from './tipos';

const STATUS_CONVERSA = ['OPEN', 'PENDING', 'CLOSED'] as const;
const DIRECOES = ['INBOUND', 'OUTBOUND'] as const;
const STATUS_MENSAGEM = ['RECEIVED', 'PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'] as const;
const TIPOS_CONTEUDO = ['TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'DOCUMENT', 'LOCATION', 'OTHER'] as const;
const TIPOS_CANAL = [
  'LIVE_CHAT', 'TELEGRAM', 'WHATSAPP_CLOUD', 'WHATSAPP_EVOLUTION', 'INSTAGRAM',
] as const;

function mapearConversa(dados: ConversaWire): ConversaResumo {
  return {
    id: obrigatorio(dados.id, 'conversa.id'),
    channelConnectionId: obrigatorio(dados.channelConnectionId, 'conversa.channelConnectionId'),
    canalTipo: dados.canalTipo ? umDe(dados.canalTipo, TIPOS_CANAL, 'conversa.canalTipo') : null,
    canalNome: dados.canalNome ?? null,
    canalIdentificador: dados.canalIdentificador ?? null,
    contatoNome: dados.contatoNome ?? null,
    contatoIdentificador: obrigatorio(
      dados.contatoIdentificador,
      'conversa.contatoIdentificador',
    ),
    status: umDe(dados.status, STATUS_CONVERSA, 'conversa.status'),
    atendenteId: dados.atendenteId ?? null,
    atendenteNome: dados.atendenteNome ?? null,
    ultimaMensagemEm: dados.ultimaMensagemEm ?? null,
    venceEm: dados.venceEm ?? null,
    versao: obrigatorio(dados.versao, 'conversa.versao'),
  };
}

function mapearMensagem(dados: MensagemWire): Mensagem {
  return {
    id: obrigatorio(dados.id, 'mensagem.id'),
    direcao: umDe(dados.direcao, DIRECOES, 'mensagem.direcao'),
    tipoConteudo: umDe(dados.tipoConteudo, TIPOS_CONTEUDO, 'mensagem.tipoConteudo'),
    texto: dados.texto ?? null,
    status: umDe(dados.status, STATUS_MENSAGEM, 'mensagem.status'),
    autorId: dados.autorId ?? null,
    autorNome: dados.autorNome ?? null,
    criadaEm: obrigatorio(dados.criadaEm, 'mensagem.criadaEm'),
    versao: obrigatorio(dados.versao, 'mensagem.versao'),
  };
}

function mapearCursor(
  antesDe: string | undefined,
  antesDoId: string | undefined,
  temMais: boolean,
  origem: string,
): CursorTemporal | null {
  if (!temMais) return null;
  return {
    antesDe: obrigatorio(antesDe, `${origem}.proximoAntesDe`),
    antesDoId: obrigatorio(antesDoId, `${origem}.proximoAntesDoId`),
  };
}

function mapearPaginaConversas(dados: PaginaConversasWire): PaginaConversas {
  const temMais = dados.temMais === true;
  return {
    itens: (dados.itens ?? []).map(mapearConversa),
    proximoCursor: mapearCursor(
      dados.proximoAntesDe,
      dados.proximoAntesDoId,
      temMais,
      'paginaConversas',
    ),
    temMais,
    sequenciaDoStream: dados.sequenciaDoStream ?? 0,
  };
}

function mapearPaginaMensagens(dados: PaginaMensagensWire): PaginaMensagens {
  const temMais = dados.temMais === true;
  return {
    itens: (dados.itens ?? []).map(mapearMensagem),
    proximoCursor: mapearCursor(
      dados.proximoAntesDe,
      dados.proximoAntesDoId,
      temMais,
      'paginaMensagens',
    ),
    temMais,
    sequenciaDoStream: dados.sequenciaDoStream ?? 0,
  };
}

function mapearEvento(dados: EventoWire): EventoTempoReal {
  return {
    id: obrigatorio(dados.id, 'evento.id'),
    sequence: obrigatorio(dados.sequence, 'evento.sequence'),
    tipo: obrigatorio(dados.tipo, 'evento.tipo'),
    conversationId: obrigatorio(dados.conversationId, 'evento.conversationId'),
    messageId: dados.messageId ?? null,
    versao: obrigatorio(dados.versao, 'evento.versao'),
    ocorridoEm: obrigatorio(dados.ocorridoEm, 'evento.ocorridoEm'),
  };
}

function caminhoPaginado(base: string, cursor?: CursorTemporal, limite = 50): string {
  const parametros = new URLSearchParams({ limite: String(limite) });
  if (cursor) {
    parametros.set('antesDe', cursor.antesDe);
    parametros.set('antesDoId', cursor.antesDoId);
  }
  return `${base}?${parametros}`;
}

export const conversasApi = {
  listar: async (cursor?: CursorTemporal, signal?: AbortSignal): Promise<PaginaConversas> =>
    mapearPaginaConversas(await api.get<PaginaConversasWire>(
      caminhoPaginado('/conversas', cursor),
      { signal },
    )),
  mensagens: async (
    conversaId: string,
    cursor?: CursorTemporal,
    signal?: AbortSignal,
  ): Promise<PaginaMensagens> => mapearPaginaMensagens(await api.get<PaginaMensagensWire>(
    caminhoPaginado(`/conversas/${conversaId}/mensagens`, cursor),
    { signal },
  )),
  eventos: async (apos: number, signal?: AbortSignal, limite = 100): Promise<PaginaEventos> => {
    const parametros = new URLSearchParams({ apos: String(apos), limite: String(limite) });
    const dados = await api.get<PaginaEventosWire>(`/conversas/eventos?${parametros}`, { signal });
    return {
      eventos: (dados.eventos ?? []).map(mapearEvento),
      temMais: dados.temMais === true,
      resetObrigatorio: dados.resetObrigatorio === true,
      ultimaSequencia: dados.ultimaSequencia ?? apos,
    };
  },
  enviar: async (conversaId: string, texto: string): Promise<Mensagem> =>
    mapearMensagem(await api.post<MensagemWire>(
      `/conversas/${conversaId}/mensagens`,
      { texto },
      { idempotencyKey: crypto.randomUUID(), retry: 'idempotent-write' },
    )),
};
