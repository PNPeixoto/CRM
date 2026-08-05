import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { contatosApi, funilApi, tarefasApi } from '@/shared/crm/api';
import { ContactsPage } from './contacts/ContactsPage';
import { PipelinesPage } from './pipelines/PipelinesPage';
import { TasksPage } from './tasks/TasksPage';

vi.mock('@/shared/crm/api', () => ({
  contatosApi: { listar: vi.fn(), criar: vi.fn(), atualizar: vi.fn(), excluir: vi.fn() },
  funilApi: {
    listarFunis: vi.fn(),
    listarOportunidades: vi.fn(),
    criar: vi.fn(),
    mover: vi.fn(),
    excluir: vi.fn(),
  },
  tarefasApi: {
    listar: vi.fn(),
    criar: vi.fn(),
    alternarConclusao: vi.fn(),
    excluir: vi.fn(),
  },
}));

describe('visão consolidada por responsável', () => {
  beforeEach(() => {
    vi.mocked(contatosApi.listar).mockResolvedValue([
      {
        id: 'contato-maria',
        nome: 'Maria',
        email: null,
        telefone: null,
        empresa: null,
        observacoes: null,
        responsavelId: 'usuario-peixoto',
        responsavelLogin: 'peixoto',
        responsavelNome: 'Peixoto',
        criadoEm: '2026-08-03T10:00:00Z',
      },
      {
        id: 'contato-joao',
        nome: 'João',
        email: null,
        telefone: null,
        empresa: null,
        observacoes: null,
        responsavelId: 'usuario-atendente',
        responsavelLogin: 'atendente',
        responsavelNome: 'Atendente',
        criadoEm: '2026-08-03T10:00:00Z',
      },
    ]);

    vi.mocked(tarefasApi.listar).mockResolvedValue([
      {
        id: 'tarefa-1',
        titulo: 'Retornar para Maria',
        descricao: null,
        vencimentoEm: null,
        concluidaEm: null,
        responsavelId: 'usuario-atendente',
        responsavelLogin: 'atendente',
        responsavelNome: 'Atendente',
        contatoId: 'contato-maria',
        oportunidadeId: null,
        criadaEm: '2026-08-03T10:00:00Z',
      },
    ]);

    vi.mocked(funilApi.listarFunis).mockResolvedValue([
      {
        id: 'funil-1',
        nome: 'Comercial',
        padrao: true,
        etapas: [{ id: 'etapa-1', nome: 'Novo', posicao: 1, ganho: false, perda: false }],
      },
    ]);
    vi.mocked(funilApi.listarOportunidades).mockResolvedValue([
      {
        id: 'oportunidade-1',
        funilId: 'funil-1',
        etapaId: 'etapa-1',
        contatoId: 'contato-maria',
        titulo: 'Contrato Maria',
        valorCentavos: 150_000,
        status: 'OPEN',
        previsaoFechamento: null,
        motivoPerda: null,
        responsavelId: 'usuario-peixoto',
        responsavelLogin: 'peixoto',
        responsavelNome: 'Peixoto',
        criadaEm: '2026-08-03T10:00:00Z',
      },
    ]);
  });

  it('identifica donos diferentes na lista consolidada de contatos', async () => {
    render(<ContactsPage />);

    expect(await screen.findByText('Maria')).toBeInTheDocument();
    expect(screen.getByText('Peixoto (@peixoto)')).toBeInTheDocument();
    expect(screen.getByText('Atendente (@atendente)')).toBeInTheDocument();
  });

  it('identifica o responsável da tarefa', async () => {
    render(<TasksPage />);

    expect(await screen.findByText('Retornar para Maria')).toBeInTheDocument();
    expect(screen.getByText('Responsável: Atendente (@atendente)')).toBeInTheDocument();
  });

  it('identifica o responsável no card do funil', async () => {
    render(<PipelinesPage />);

    expect(await screen.findByText('Contrato Maria')).toBeInTheDocument();
    expect(screen.getByText('Responsável: Peixoto (@peixoto)')).toBeInTheDocument();
  });
});
