import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { TenantOnboardingGuard } from './TenantOnboardingGuard';
import { useTenantPresentation } from './TenantPresentationContext';

vi.mock('./TenantPresentationContext', () => ({
  useTenantPresentation: vi.fn(),
}));

describe('TenantOnboardingGuard', () => {
  it('redirects the first access to onboarding', async () => {
    vi.mocked(useTenantPresentation).mockReturnValue({
      apresentacao: {
        segmento: 'GENERAL_SERVICES', versaoPreset: 1, onboardingConcluido: false,
        navegacao: [], funilPadrao: { nome: 'Funil', etapas: [] },
      },
      carregando: false,
      recarregar: vi.fn(),
      escolherSegmento: vi.fn(),
    });

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route element={<TenantOnboardingGuard safePath="/dashboard" />}>
            <Route path="/dashboard" element={<span>Dashboard</span>} />
            <Route path="/primeiro-acesso" element={<span>Escolha o segmento</span>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Escolha o segmento')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
  });
});
