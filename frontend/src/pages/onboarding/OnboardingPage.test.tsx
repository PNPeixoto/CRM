import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import type { ApresentacaoDoTenant } from '@/shared/tenant/tipos';
import { OnboardingPage } from './OnboardingPage';

const { escolherSegmento } = vi.hoisted(() => ({
  escolherSegmento: vi.fn(),
}));

vi.mock('@/shared/tenant/TenantPresentationContext', () => ({
  useTenantPresentation: () => ({
    escolherSegmento,
    permissoes: { 'reports.read': 'TENANT' },
  }),
}));

const apresentacaoConcluida: ApresentacaoDoTenant = {
  segmento: 'RESTAURANT',
  versaoPreset: 1,
  onboardingConcluido: true,
  navegacao: [{
    routeId: 'dashboard',
    rotulo: 'Visão geral',
    grupo: 'operacao',
    ordem: 10,
    visivel: true,
  }],
  funilPadrao: { nome: 'Pedidos', etapas: [] },
};

describe('onboarding da empresa', () => {
  beforeEach(() => {
    escolherSegmento.mockReset();
    escolherSegmento.mockResolvedValue(apresentacaoConcluida);
  });

  it('oferece escolhas acessíveis e recarrega a navegação sem novo login', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/primeiro-acesso']}>
        <Routes>
          <Route path="/primeiro-acesso" element={<OnboardingPage />} />
          <Route path="/dashboard" element={<h1>CRM preparado</h1>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Como sua empresa trabalha?' })).toBeVisible();
    expect(screen.getAllByRole('button')).toHaveLength(4);
    await esperarSemViolacoesAcessiveis(container);

    fireEvent.click(screen.getByRole('button', { name: /Restaurante/i }));

    expect(escolherSegmento).toHaveBeenCalledWith('RESTAURANT');
    expect(await screen.findByRole('heading', { name: 'CRM preparado' })).toBeVisible();
  });
});
