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
  it('mantém seletor acessível e oferece alça para arrastar o card', async () => {
    const aoMover = vi.fn();
    const { container } = render(
      <KanbanBoard
        etapas={etapas}
        oportunidades={[oportunidade]}
        oportunidadeEmMovimento={null}
        aoMover={aoMover}
      />,
    );

    const alca = screen.getByRole('button', { name: 'Arrastar Expansão Grupo Horizonte' });
    expect(alca).toHaveAttribute('aria-roledescription', 'oportunidade arrastável');
    expect(alca).toHaveAttribute('aria-describedby');

    fireEvent.change(
      screen.getByLabelText('Mover Expansão Grupo Horizonte para outra etapa'),
      { target: { value: 'proposta' } },
    );
    expect(aoMover).toHaveBeenCalledWith('oportunidade-1', 'proposta');
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
      />,
    );

    expect(screen.getByRole('button', { name: 'Arrastar Expansão Grupo Horizonte' }))
      .toBeDisabled();
    expect(screen.getByLabelText('Mover Expansão Grupo Horizonte para outra etapa'))
      .toBeDisabled();
    expect(screen.getByRole('button', { name: 'Arrastar Renovação Clínica Íris' }))
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
