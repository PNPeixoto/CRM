import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { relatoriosApi } from '@/shared/crm/api';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { renderComEstadoServidor } from '@/test/estadoServidor';
import { DashboardPage } from './DashboardPage';

afterEach(() => vi.restoreAllMocks());

describe('Visão geral', () => {
  it('prioriza atendimento, rotina e pipeline com destinos operacionais', async () => {
    vi.spyOn(relatoriosApi, 'visaoGeral').mockResolvedValue({
      conversasAbertas: 2,
      conversasAguardando: 1,
      mensagensRecebidasHoje: 18,
      totalDeContatos: 8,
      oportunidadesAbertas: 6,
      valorAbertoCentavos: 36_400_000,
      oportunidadesGanhas: 4,
      valorGanhoCentavos: 18_400_000,
      oportunidadesPerdidas: 2,
      tarefasAbertas: 6,
      tarefasAtrasadas: 1,
      canaisAtivos: 4,
      taxaDeConversaoPercentual: 66.7,
    });

    const { container } = renderComEstadoServidor(
      <MemoryRouter><DashboardPage /></MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeVisible();
    expect((await screen.findAllByText(/364\.000,00/))[0]).toBeVisible();
    expect(screen.getByText('66,7%')).toBeVisible();
    expect(screen.getAllByRole('link', { name: /conversas abertas/i })[0])
      .toHaveAttribute('href', '/inbox');
    expect(screen.getByRole('link', { name: /contatos cadastrados/i }))
      .toHaveAttribute('href', '/contatos');
    await esperarSemViolacoesAcessiveis(container);
  }, 10_000);
});
