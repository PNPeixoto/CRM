import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ROTAS } from '@/app/routes';
import { resolveNavigation } from './resolveNavigation';
import { TenantPresentationProvider, useTenantPresentation } from './TenantPresentationContext';
import { obterApresentacao, obterPermissoes, salvarPerfilInicial } from './api';
import type { ApresentacaoDoTenant } from './tipos';

const { authenticatedUser } = vi.hoisted(() => ({ authenticatedUser: { id: 'user-1' } }));

vi.mock('@/shared/auth/AuthContext', () => ({
  useAuth: () => ({ usuario: authenticatedUser }),
}));

vi.mock('./api', () => ({
  obterApresentacao: vi.fn(),
  obterPermissoes: vi.fn(),
  salvarPerfilInicial: vi.fn(),
}));

const general = makePresentation('GENERAL_SERVICES', 'Contatos');
const confectionery = makePresentation('CONFECTIONERY', 'Clientes');

function makePresentation(
  segmento: ApresentacaoDoTenant['segmento'],
  contactsLabel: string,
): ApresentacaoDoTenant {
  return {
    segmento,
    versaoPreset: 1,
    onboardingConcluido: true,
    navegacao: [
      { routeId: 'contacts', rotulo: contactsLabel, grupo: 'operacao', ordem: 10, visivel: true },
    ],
    funilPadrao: { nome: 'Funil', etapas: [] },
  };
}

function Harness() {
  const { apresentacao, escolherSegmento } = useTenantPresentation();
  const label = resolveNavigation(ROTAS, apresentacao)
    .find((route) => route.id === 'contacts')?.rotulo;
  return (
    <>
      <span>{label}</span>
      <button type="button" onClick={() => void escolherSegmento('CONFECTIONERY')}>Escolher</button>
    </>
  );
}

describe('TenantPresentationProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(obterApresentacao).mockResolvedValue(general);
    vi.mocked(obterPermissoes).mockResolvedValue({ 'contacts.read': 'OWN' });
    vi.mocked(salvarPerfilInicial).mockResolvedValue(confectionery);
  });

  it('updates the resolved menu immediately after saving the segment', async () => {
    render(<TenantPresentationProvider><Harness /></TenantPresentationProvider>);
    expect(await screen.findByText('Contatos')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Escolher' }));
    expect(await screen.findByText('Clientes')).toBeInTheDocument();
  });

  it('loads the persisted presentation again after a page remount', async () => {
    vi.mocked(obterApresentacao).mockResolvedValue(confectionery);
    const first = render(<TenantPresentationProvider><Harness /></TenantPresentationProvider>);
    expect(await first.findByText('Clientes')).toBeInTheDocument();
    first.unmount();

    const second = render(<TenantPresentationProvider><Harness /></TenantPresentationProvider>);
    await waitFor(() => expect(obterApresentacao).toHaveBeenCalledTimes(2));
    expect(second.getByText('Clientes')).toBeInTheDocument();
  });
});
