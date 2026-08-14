import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Etapa, Oportunidade } from '@/shared/crm/tipos';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { KanbanBoard } from './KanbanBoard';
import { resolverMovimentoDoKanban } from './kanban';

const etapas: readonly Etapa[] = [
  { id: 'novo', nome: 'Novo', posicao: 1, ganho: false, perda: false },
  { id: 'proposta', nome: 'Proposta', posicao: 2, ganho: false, perda: false },
];

const oportunidade: Oportunidade = {
  id: 'oportunidade-1',
  funilId: 'funil-1',
  etapaId: 'novo',
  contatoId: null,
  titulo: 'Expansão Grupo Horizonte',
  valorCentavos: 12_800_000,
  status: 'OPEN',
  previsaoFechamento: null,
  motivoPerda: null,
  responsavelId: 'usuario-carla',
  responsavelLogin: 'carla',
  responsavelNome: 'Carla Mendes',
  criadaEm: '2026-08-01T12:00:00Z',
};

describe('KanbanBoard', () => {
  it('oferece alça acessível e ação de adicionar na etapa sem seletor no card', async () => {
    const aoMover = vi.fn();
    const aoAdicionar = vi.fn();
    const { container } = render(
      <KanbanBoard
        etapas={etapas}
        oportunidades={[oportunidade]}
        oportunidadeEmMovimento={null}
        aoMover={aoMover}
        aoAdicionar={aoAdicionar}
      />,
    );

    const alca = screen.getByRole('button', { name: 'Arrastar Expansão Grupo Horizonte' });
    expect(alca).toHaveAttribute('aria-roledescription', 'oportunidade arrastável');
    expect(alca).toHaveAttribute('aria-describedby');
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar oportunidade em Novo' }));
    expect(aoAdicionar).toHaveBeenCalledWith('novo');
    await esperarSemViolacoesAcessiveis(container);
  });

  it('bloqueia os controles do card enquanto a movimentação está pendente', () => {
    const outraOportunidade: Oportunidade = {
      ...oportunidade,
      id: 'oportunidade-2',
      etapaId: 'proposta',
      titulo: 'Renovação Clínica Íris',
    };
    render(
      <KanbanBoard
        etapas={etapas}
        oportunidades={[oportunidade, outraOportunidade]}
        oportunidadeEmMovimento="oportunidade-1"
        aoMover={vi.fn()}
        aoAdicionar={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Arrastar Expansão Grupo Horizonte' }))
      .toBeDisabled();
    expect(screen.getByRole('button', { name: 'Arrastar Renovação Clínica Íris' }))
      .toBeDisabled();
    expect(screen.getByRole('button', { name: 'Adicionar oportunidade em Proposta' }))
      .toBeDisabled();
  });

  it('só produz movimento quando o destino é outra etapa', () => {
    expect(resolverMovimentoDoKanban('oportunidade-1', 'novo', null)).toBeNull();
    expect(resolverMovimentoDoKanban('oportunidade-1', 'novo', 'novo')).toBeNull();
    expect(resolverMovimentoDoKanban('oportunidade-1', 'novo', 'proposta')).toEqual({
      oportunidadeId: 'oportunidade-1',
      etapaId: 'proposta',
    });
  });
});
