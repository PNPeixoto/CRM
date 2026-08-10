import type {
  CanalRequestWire,
  CanalWire,
  PareamentoWire,
  ContatoRequestWire,
  ContatoWire,
  DealFunilWire,
  OportunidadeRequestWire,
  OportunidadeWire,
  TarefaRequestWire,
  TarefaWire,
  VisaoGeralWire,
} from '@/adapters/http/contracts';
import { obrigatorio, umDe } from '@/adapters/http/mapping';
import { api } from '@/lib/api';
import type {
  Canal, Contato, Funil, Oportunidade, PareamentoCanal, Tarefa, VisaoGeral,
} from './tipos';

const STATUS_OPORTUNIDADE = ['OPEN', 'WON', 'LOST'] as const;
const TIPOS_CANAL = [
  'LIVE_CHAT', 'TELEGRAM', 'WHATSAPP_CLOUD', 'WHATSAPP_EVOLUTION', 'INSTAGRAM',
] as const;
const ESTADOS_REMOTOS_CANAL = ['CHECKING', 'HEALTHY', 'DEGRADED', 'REPAIRED', 'ERROR'] as const;

function mapearContato(dados: ContatoWire): Contato {
  return {
    id: obrigatorio(dados.id, 'contato.id'),
    nome: obrigatorio(dados.nome, 'contato.nome'),
    email: dados.email ?? null,
    telefone: dados.telefone ?? null,
    empresa: dados.empresa ?? null,
    observacoes: dados.observacoes ?? null,
    responsavelId: dados.responsavelId ?? null,
    responsavelLogin: dados.responsavelLogin ?? null,
    responsavelNome: dados.responsavelNome ?? null,
    criadoEm: obrigatorio(dados.criadoEm, 'contato.criadoEm'),
  };
}

function mapearFunil(dados: DealFunilWire): Funil {
  return {
    id: obrigatorio(dados.id, 'funil.id'),
    nome: obrigatorio(dados.nome, 'funil.nome'),
    padrao: obrigatorio(dados.padrao, 'funil.padrao'),
    etapas: obrigatorio(dados.etapas, 'funil.etapas').map((etapa) => ({
      id: obrigatorio(etapa.id, 'etapa.id'),
      nome: obrigatorio(etapa.nome, 'etapa.nome'),
      posicao: obrigatorio(etapa.posicao, 'etapa.posicao'),
      ganho: obrigatorio(etapa.ganho, 'etapa.ganho'),
      perda: obrigatorio(etapa.perda, 'etapa.perda'),
    })),
  };
}

function mapearOportunidade(dados: OportunidadeWire): Oportunidade {
  return {
    id: obrigatorio(dados.id, 'oportunidade.id'),
    funilId: obrigatorio(dados.funilId, 'oportunidade.funilId'),
    etapaId: obrigatorio(dados.etapaId, 'oportunidade.etapaId'),
    contatoId: dados.contatoId ?? null,
    titulo: obrigatorio(dados.titulo, 'oportunidade.titulo'),
    valorCentavos: obrigatorio(dados.valorCentavos, 'oportunidade.valorCentavos'),
    status: umDe(dados.status, STATUS_OPORTUNIDADE, 'oportunidade.status'),
    previsaoFechamento: dados.previsaoFechamento ?? null,
    motivoPerda: dados.motivoPerda ?? null,
    responsavelId: dados.responsavelId ?? null,
    responsavelLogin: dados.responsavelLogin ?? null,
    responsavelNome: dados.responsavelNome ?? null,
    criadaEm: obrigatorio(dados.criadaEm, 'oportunidade.criadaEm'),
  };
}

function mapearTarefa(dados: TarefaWire): Tarefa {
  return {
    id: obrigatorio(dados.id, 'tarefa.id'),
    titulo: obrigatorio(dados.titulo, 'tarefa.titulo'),
    descricao: dados.descricao ?? null,
    vencimentoEm: dados.vencimentoEm ?? null,
    concluidaEm: dados.concluidaEm ?? null,
    responsavelId: dados.responsavelId ?? null,
    responsavelLogin: dados.responsavelLogin ?? null,
    responsavelNome: dados.responsavelNome ?? null,
    contatoId: dados.contatoId ?? null,
    oportunidadeId: dados.oportunidadeId ?? null,
    criadaEm: obrigatorio(dados.criadaEm, 'tarefa.criadaEm'),
  };
}

function mapearCanal(dados: CanalWire): Canal {
  return {
    id: obrigatorio(dados.id, 'canal.id'),
    tipo: umDe(dados.tipo, TIPOS_CANAL, 'canal.tipo'),
    nome: obrigatorio(dados.nome, 'canal.nome'),
    identificadorExterno: dados.identificadorExterno ?? null,
    ativo: obrigatorio(dados.ativo, 'canal.ativo'),
    temToken: obrigatorio(dados.temToken, 'canal.temToken'),
    temSegredoWebhook: obrigatorio(dados.temSegredoWebhook, 'canal.temSegredoWebhook'),
    estadoRemoto: dados.estadoRemoto == null
      ? null
      : umDe(dados.estadoRemoto, ESTADOS_REMOTOS_CANAL, 'canal.estadoRemoto'),
    pendenciasRemotas: dados.pendenciasRemotas ?? null,
    ultimaReconciliacaoEm: dados.ultimaReconciliacaoEm ?? null,
    ultimaFalhaRemotaEm: dados.ultimaFalhaRemotaEm ?? null,
  };
}

function mapearVisao(dados: VisaoGeralWire): VisaoGeral {
  return {
    conversasAbertas: obrigatorio(dados.conversasAbertas, 'visao.conversasAbertas'),
    conversasAguardando: obrigatorio(dados.conversasAguardando, 'visao.conversasAguardando'),
    mensagensRecebidasHoje: obrigatorio(dados.mensagensRecebidasHoje, 'visao.mensagensRecebidasHoje'),
    totalDeContatos: obrigatorio(dados.totalDeContatos, 'visao.totalDeContatos'),
    oportunidadesAbertas: obrigatorio(dados.oportunidadesAbertas, 'visao.oportunidadesAbertas'),
    valorAbertoCentavos: obrigatorio(dados.valorAbertoCentavos, 'visao.valorAbertoCentavos'),
    oportunidadesGanhas: obrigatorio(dados.oportunidadesGanhas, 'visao.oportunidadesGanhas'),
    valorGanhoCentavos: obrigatorio(dados.valorGanhoCentavos, 'visao.valorGanhoCentavos'),
    oportunidadesPerdidas: obrigatorio(dados.oportunidadesPerdidas, 'visao.oportunidadesPerdidas'),
    tarefasAbertas: obrigatorio(dados.tarefasAbertas, 'visao.tarefasAbertas'),
    tarefasAtrasadas: obrigatorio(dados.tarefasAtrasadas, 'visao.tarefasAtrasadas'),
    canaisAtivos: obrigatorio(dados.canaisAtivos, 'visao.canaisAtivos'),
    taxaDeConversaoPercentual: obrigatorio(
      dados.taxaDeConversaoPercentual,
      'visao.taxaDeConversaoPercentual',
    ),
  };
}

function corpoContato(dados: Partial<Contato> & { nome: string }): ContatoRequestWire {
  return {
    nome: dados.nome,
    ...(dados.email ? { email: dados.email } : {}),
    ...(dados.telefone ? { telefone: dados.telefone } : {}),
    ...(dados.empresa ? { empresa: dados.empresa } : {}),
    ...(dados.observacoes ? { observacoes: dados.observacoes } : {}),
    ...(dados.responsavelId ? { responsavelId: dados.responsavelId } : {}),
  };
}

export const contatosApi = {
  listar: async (
    busca?: string,
    signal?: AbortSignal,
    pagina?: number,
    tamanho?: number,
  ): Promise<Contato[]> => {
    const parametros = new URLSearchParams();
    if (busca) parametros.set('busca', busca);
    if (pagina !== undefined) parametros.set('pagina', String(pagina));
    if (tamanho !== undefined) parametros.set('tamanho', String(tamanho));
    if (pagina !== undefined || tamanho !== undefined) parametros.set('ordenarPor', 'nome');
    const consulta = parametros.toString();
    return (
    (await api.get<readonly ContatoWire[]>(
      `/contatos${consulta ? `?${consulta}` : ''}`,
      { signal },
    )).map(mapearContato));
  },
  criar: async (dados: Partial<Contato> & { nome: string }): Promise<Contato> =>
    mapearContato(await api.post<ContatoWire>('/contatos', corpoContato(dados), {
      idempotencyKey: globalThis.crypto.randomUUID(),
      retry: 'idempotent-write',
    })),
  atualizar: async (id: string, dados: Partial<Contato> & { nome: string }): Promise<Contato> =>
    mapearContato(await api.put<ContatoWire>(`/contatos/${id}`, corpoContato(dados))),
  excluir: (id: string): Promise<void> => api.delete<void>(`/contatos/${id}`),
};

export const funilApi = {
  listarFunis: async (signal?: AbortSignal): Promise<Funil[]> =>
    (await api.get<readonly DealFunilWire[]>('/funis', { signal })).map(mapearFunil),
  listarOportunidades: async (funilId: string, signal?: AbortSignal): Promise<Oportunidade[]> =>
    (await api.get<readonly OportunidadeWire[]>(`/funis/${funilId}/oportunidades`, { signal }))
      .map(mapearOportunidade),
  criar: async (dados: {
    titulo: string;
    etapaId: string;
    valorCentavos: number;
    contatoId?: string | null;
    previsaoFechamento?: string | null;
  }): Promise<Oportunidade> => {
    const corpo: OportunidadeRequestWire = {
      titulo: dados.titulo,
      etapaId: dados.etapaId,
      valorCentavos: dados.valorCentavos,
      ...(dados.contatoId ? { contatoId: dados.contatoId } : {}),
      ...(dados.previsaoFechamento ? { previsaoFechamento: dados.previsaoFechamento } : {}),
    };
    return mapearOportunidade(await api.post<OportunidadeWire>('/oportunidades', corpo));
  },
  mover: async (id: string, etapaId: string, motivoPerda?: string): Promise<Oportunidade> =>
    mapearOportunidade(await api.post<OportunidadeWire>(`/oportunidades/${id}/mover`, {
      etapaId,
      ...(motivoPerda ? { motivoPerda } : {}),
    })),
  excluir: (id: string): Promise<void> => api.delete<void>(`/oportunidades/${id}`),
};

export const tarefasApi = {
  listar: async (apenasAbertas = false, signal?: AbortSignal): Promise<Tarefa[]> =>
    (await api.get<readonly TarefaWire[]>(`/tarefas?apenasAbertas=${apenasAbertas}`, { signal }))
      .map(mapearTarefa),
  criar: async (dados: {
    titulo: string;
    descricao?: string;
    vencimentoEm?: string | null;
  }): Promise<Tarefa> => {
    const corpo: TarefaRequestWire = {
      titulo: dados.titulo,
      ...(dados.descricao ? { descricao: dados.descricao } : {}),
      ...(dados.vencimentoEm ? { vencimentoEm: dados.vencimentoEm } : {}),
    };
    return mapearTarefa(await api.post<TarefaWire>('/tarefas', corpo));
  },
  alternarConclusao: async (id: string): Promise<Tarefa> =>
    mapearTarefa(await api.post<TarefaWire>(`/tarefas/${id}/concluir`)),
  excluir: (id: string): Promise<void> => api.delete<void>(`/tarefas/${id}`),
};

export const canaisApi = {
  listar: async (signal?: AbortSignal): Promise<Canal[]> =>
    (await api.get<readonly CanalWire[]>('/canais', { signal })).map(mapearCanal),
  criar: async (dados: {
    tipo: string;
    nome: string;
    identificadorExterno?: string;
    token?: string;
    segredoWebhook?: string;
  }): Promise<Canal> => {
    const corpo: CanalRequestWire = {
      ...dados,
      tipo: umDe(dados.tipo, TIPOS_CANAL, 'canal.tipo'),
    };
    return mapearCanal(await api.post<CanalWire>('/canais', corpo));
  },
  alternarAtivacao: (id: string): Promise<void> => api.post<void>(`/canais/${id}/ativacao`),

  /**
   * Pede material de pareamento do WhatsApp Evolution.
   *
   * <p>O retorno é credencial de sessão e não entra em cache de servidor nem
   * de cliente: a resposta vem `no-store` e esta função não é exposta como
   * query, só como ação explícita de quem clicou.
   */
  parear: async (id: string): Promise<PareamentoCanal> => {
    const dados = await api.post<PareamentoWire>(`/canais/${id}/pareamento`);
    return {
      estado: dados.estado ?? 'unknown',
      qrCode: dados.qrCodeBase64 ?? null,
      codigo: dados.codigoDeParear ?? null,
      tentativa: dados.tentativa ?? 0,
    };
  },
};

export const relatoriosApi = {
  visaoGeral: async (signal?: AbortSignal): Promise<VisaoGeral> =>
    mapearVisao(await api.get<VisaoGeralWire>('/relatorios/visao-geral', { signal })),
};
