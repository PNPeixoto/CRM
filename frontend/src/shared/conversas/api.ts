import type { ConversaWire, MensagemWire } from '@/adapters/http/contracts';
import { obrigatorio, umDe } from '@/adapters/http/mapping';
import { api } from '@/lib/api';
import type { ConversaResumo, Mensagem } from './tipos';

const STATUS_CONVERSA = ['OPEN', 'PENDING', 'CLOSED'] as const;
const DIRECOES = ['INBOUND', 'OUTBOUND'] as const;
const STATUS_MENSAGEM = ['RECEIVED', 'PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'] as const;
const TIPOS_CONTEUDO = ['TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'DOCUMENT', 'LOCATION', 'OTHER'] as const;

function mapearConversa(dados: ConversaWire): ConversaResumo {
  return {
    id: obrigatorio(dados.id, 'conversa.id'),
    channelConnectionId: obrigatorio(dados.channelConnectionId, 'conversa.channelConnectionId'),
    contatoNome: dados.contatoNome ?? null,
    status: umDe(dados.status, STATUS_CONVERSA, 'conversa.status'),
    atendenteId: dados.atendenteId ?? null,
    ultimaMensagemEm: dados.ultimaMensagemEm ?? null,
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
    criadaEm: obrigatorio(dados.criadaEm, 'mensagem.criadaEm'),
  };
}

export const conversasApi = {
  listar: async (): Promise<readonly ConversaResumo[]> =>
    (await api.get<readonly ConversaWire[]>('/conversas')).map(mapearConversa),
  mensagens: async (conversaId: string): Promise<readonly Mensagem[]> =>
    (await api.get<readonly MensagemWire[]>(`/conversas/${conversaId}/mensagens`)).map(mapearMensagem),
  enviar: async (conversaId: string, texto: string): Promise<Mensagem> =>
    mapearMensagem(await api.post<MensagemWire>(
      `/conversas/${conversaId}/mensagens`,
      { texto },
      { idempotencyKey: crypto.randomUUID(), retry: 'idempotent-write' },
    )),
};
