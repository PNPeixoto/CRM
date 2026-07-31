import { api } from '@/lib/api';
import type { Canal, Contato, Funil, Oportunidade, Tarefa, VisaoGeral } from './tipos';

/**
 * Nenhuma função recebe `tenantId`: ele é resolvido no backend a partir do
 * token verificado. Aceitá-lo aqui daria a impressão de que o cliente escolhe
 * de qual empresa está lendo.
 */

export const contatosApi = {
  listar: (busca?: string) =>
    api.get<Contato[]>(`/contatos${busca ? `?busca=${encodeURIComponent(busca)}` : ''}`),
  criar: (dados: Partial<Contato> & { nome: string }) => api.post<Contato>('/contatos', dados),
  atualizar: (id: string, dados: Partial<Contato> & { nome: string }) =>
    api.put<Contato>(`/contatos/${id}`, dados),
  excluir: (id: string) => api.delete<void>(`/contatos/${id}`),
};

export const funilApi = {
  listarFunis: () => api.get<Funil[]>('/funis'),
  listarOportunidades: (funilId: string) =>
    api.get<Oportunidade[]>(`/funis/${funilId}/oportunidades`),
  criar: (dados: {
    titulo: string;
    etapaId: string;
    valorCentavos: number;
    contatoId?: string | null;
    previsaoFechamento?: string | null;
  }) => api.post<Oportunidade>('/oportunidades', dados),
  mover: (id: string, etapaId: string, motivoPerda?: string) =>
    api.post<Oportunidade>(`/oportunidades/${id}/mover`, { etapaId, motivoPerda }),
  excluir: (id: string) => api.delete<void>(`/oportunidades/${id}`),
};

export const tarefasApi = {
  listar: (apenasAbertas = false) =>
    api.get<Tarefa[]>(`/tarefas?apenasAbertas=${apenasAbertas}`),
  criar: (dados: { titulo: string; descricao?: string; vencimentoEm?: string | null }) =>
    api.post<Tarefa>('/tarefas', dados),
  alternarConclusao: (id: string) => api.post<Tarefa>(`/tarefas/${id}/concluir`),
  excluir: (id: string) => api.delete<void>(`/tarefas/${id}`),
};

export const canaisApi = {
  listar: () => api.get<Canal[]>('/canais'),
  criar: (dados: {
    tipo: string;
    nome: string;
    identificadorExterno?: string;
    token?: string;
    segredoWebhook?: string;
  }) => api.post<Canal>('/canais', dados),
  alternarAtivacao: (id: string) => api.post<Canal>(`/canais/${id}/ativacao`),
};

export const relatoriosApi = {
  visaoGeral: () => api.get<VisaoGeral>('/relatorios/visao-geral'),
};
