import type {
  CatalogoDePapeisWire,
  EquipeWire,
  MembroWire,
  PapelWire,
} from '@/adapters/http/contracts';
import { obrigatorio, umDe } from '@/adapters/http/mapping';
import { api } from '@/lib/api';
import {
  ALCANCES,
  type Alcance,
  type CatalogoDePapeis,
  type Equipe,
  type Membro,
  type Papel,
} from './tipos';

function mapearPapel(dados: PapelWire): Papel {
  return {
    id: obrigatorio(dados.id, 'papel.id'),
    codigo: obrigatorio(dados.codigo, 'papel.codigo'),
    nome: obrigatorio(dados.nome, 'papel.nome'),
    descricao: dados.descricao ?? null,
    sistema: obrigatorio(dados.sistema, 'papel.sistema'),
    ativo: obrigatorio(dados.ativo, 'papel.ativo'),
    permissoes: obrigatorio(dados.permissoes, 'papel.permissoes'),
    gerenciavel: obrigatorio(dados.gerenciavel, 'papel.gerenciavel'),
    atribuicoes: obrigatorio(dados.atribuicoes, 'papel.atribuicoes'),
  };
}

function mapearMembro(dados: MembroWire): Membro {
  return {
    membershipId: obrigatorio(dados.membershipId, 'membro.membershipId'),
    usuarioId: obrigatorio(dados.usuarioId, 'membro.usuarioId'),
    login: obrigatorio(dados.login, 'membro.login'),
    nome: obrigatorio(dados.nome, 'membro.nome'),
    atribuicoes: obrigatorio(dados.atribuicoes, 'membro.atribuicoes').map((item) => ({
      id: obrigatorio(item.id, 'atribuicao.id'),
      papelId: obrigatorio(item.papelId, 'atribuicao.papelId'),
      papelCodigo: obrigatorio(item.papelCodigo, 'atribuicao.papelCodigo'),
      papelNome: obrigatorio(item.papelNome, 'atribuicao.papelNome'),
      // O alcance vem validado contra a lista fechada: um valor novo do
      // servidor vira erro de contrato em vez de render silencioso e errado.
      alcance: umDe(item.alcance, ALCANCES, 'atribuicao.alcance'),
    })),
  };
}

function mapearEquipe(dados: EquipeWire): Equipe {
  return {
    gestorId: obrigatorio(dados.gestorId, 'equipe.gestorId'),
    gestorNome: obrigatorio(dados.gestorNome, 'equipe.gestorNome'),
    liderados: obrigatorio(dados.liderados, 'equipe.liderados').map((item) => ({
      usuarioId: obrigatorio(item.usuarioId, 'liderado.usuarioId'),
      nome: obrigatorio(item.nome, 'liderado.nome'),
      login: obrigatorio(item.login, 'liderado.login'),
    })),
  };
}

export const organizacaoApi = {
  catalogo: async (signal?: AbortSignal): Promise<CatalogoDePapeis> => {
    const dados = await api.get<CatalogoDePapeisWire>('/organizacao/papeis', { signal });
    return {
      papeis: obrigatorio(dados.papeis, 'catalogo.papeis').map(mapearPapel),
      permissoes: obrigatorio(dados.permissoes, 'catalogo.permissoes').map((item) => ({
        codigo: obrigatorio(item.codigo, 'permissao.codigo'),
        delegavelNoTenant: obrigatorio(item.delegavelNoTenant, 'permissao.delegavelNoTenant'),
        delegavelNaEquipe: obrigatorio(item.delegavelNaEquipe, 'permissao.delegavelNaEquipe'),
        delegavelProprio: obrigatorio(item.delegavelProprio, 'permissao.delegavelProprio'),
      })),
    };
  },

  criarPapel: async (dados: {
    codigo: string;
    nome: string;
    descricao?: string | null;
    permissoes: readonly string[];
  }): Promise<Papel> => mapearPapel(await api.post<PapelWire>('/organizacao/papeis', {
    codigo: dados.codigo,
    nome: dados.nome,
    descricao: dados.descricao ?? null,
    permissoes: [...dados.permissoes],
  })),

  atualizarPapel: async (id: string, dados: {
    nome: string;
    descricao?: string | null;
    ativo: boolean;
  }): Promise<Papel> => mapearPapel(await api.put<PapelWire>(`/organizacao/papeis/${id}`, {
    nome: dados.nome,
    descricao: dados.descricao ?? null,
    ativo: dados.ativo,
  })),

  definirPermissoes: async (id: string, permissoes: readonly string[]): Promise<Papel> =>
    mapearPapel(await api.put<PapelWire>(`/organizacao/papeis/${id}/permissoes`, {
      permissoes: [...permissoes],
    })),

  removerPapel: (id: string): Promise<void> =>
    api.delete<void>(`/organizacao/papeis/${id}`),

  membros: async (signal?: AbortSignal): Promise<Membro[]> =>
    (await api.get<readonly MembroWire[]>('/organizacao/membros', { signal }))
      .map(mapearMembro),

  atribuirPapel: (membershipId: string, papelId: string, alcance: Alcance): Promise<void> =>
    api.post<void>(`/organizacao/membros/${membershipId}/papeis`, { papelId, alcance }),

  revogarPapel: (membershipId: string, atribuicaoId: string): Promise<void> =>
    api.delete<void>(`/organizacao/membros/${membershipId}/papeis/${atribuicaoId}`),

  equipes: async (signal?: AbortSignal): Promise<Equipe[]> =>
    (await api.get<readonly EquipeWire[]>('/organizacao/equipes', { signal }))
      .map(mapearEquipe),

  incluirNaEquipe: (gestorId: string, usuarioId: string): Promise<void> =>
    api.post<void>(`/organizacao/equipes/${gestorId}/membros`, { usuarioId }),

  removerDaEquipe: (gestorId: string, usuarioId: string): Promise<void> =>
    api.delete<void>(`/organizacao/equipes/${gestorId}/membros/${usuarioId}`),
};
