import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { funilApi } from '@/shared/crm/api';
import type { Oportunidade } from '@/shared/crm/tipos';
import { renderComEstadoServidor } from '@/test/estadoServidor';
import { PipelinesPage } from './PipelinesPage';

const oportunidade: Oportunidade = {
  id: 'oportunidade-1',
  funilId: 'funil-1',
  etapaId: 'novo',
  contatoId: null,
  titulo: 'Contrato Maria',
  valorCentavos: 150_000,
  status: 'OPEN',
  previsaoFechamento: null,
  motivoPerda: null,
  responsavelId: 'usuario-peixoto',
  responsavelLogin: 'peixoto',
  responsavelNome: 'Peixoto',
  criadaEm: '2026-08-03T10:00:00Z',
};

afterEach(() => vi.restoreAllMocks());

describe('movimentação do funil', () => {
  it('move de forma otimista e restaura o card quando a API recusa', async () => {
    vi.spyOn(funilApi, 'listarFunis').mockResolvedValue([{
      id: 'funil-1',
      nome: 'Comercial',
      padrao: true,
      etapas: [
        { id: 'novo', nome: 'Novo', posicao: 1, ganho: false, perda: false },
        { id: 'proposta', nome: 'Proposta', posicao: 2, ganho: false, perda: false },
      ],
    }]);
    vi.spyOn(funilApi, 'listarOportunidades').mockResolvedValue([oportunidade]);
    let rejeitar!: (erro: Error) => void;
    const requisicao = new Promise<Oportunidade>((_resolver, rejeicao) => {
      rejeitar = rejeicao;
    });
    const mover = vi.spyOn(funilApi, 'mover').mockReturnValue(requisicao);

    renderComEstadoServidor(<PipelinesPage />);

    fireEvent.change(
      await screen.findByLabelText('Mover Contrato Maria para outra etapa'),
      { target: { value: 'proposta' } },
    );
    await waitFor(() => expect(mover).toHaveBeenCalledWith('oportunidade-1', 'proposta'));

    const proposta = screen.getByRole('region', { name: 'Etapa Proposta' });
    expect(within(proposta).getByText('Contrato Maria')).toBeVisible();

    rejeitar(new Error('indisponível'));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível mover a oportunidade.',
    );
    const novo = screen.getByRole('region', { name: 'Etapa Novo' });
    await waitFor(() => expect(within(novo).getByText('Contrato Maria')).toBeVisible());
  });
});
