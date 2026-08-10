import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ROTAS } from '@/app/routes';
import { resolveNavigation } from './resolveNavigation';
import { TenantPresentationProvider, useTenantPresentation } from './TenantPresentationContext';
import { obterApresentacao, obterContextos, obterPermissoes, salvarPerfilInicial } from './api';
import type { ApresentacaoDoTenant } from './tipos';
import { renderComEstadoServidor } from '@/test/estadoServidor';

const { authenticatedUser } = vi.hoisted(() => ({
  authenticatedUser: { id: 'user-1', tenantId: 'tenant-teste' },
}));

vi.mock('@/shared/auth/AuthContext', () => ({
  useAuth: () => ({ usuario: authenticatedUser }),
}));

vi.mock('./api', () => ({
  obterApresentacao: vi.fn(),
  obterContextos: vi.fn(),
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
  const { apresentacao, contextosNavegaveis, escolherSegmento } = useTenantPresentation();
  const label = resolveNavigation(ROTAS, apresentacao)
    .find((route) => route.id === 'contacts')?.rotulo;
  return (
    <>
      <span>{label}</span>
      {contextosNavegaveis.map((contexto) => <span key={contexto.id}>{contexto.nome}</span>)}
      <button type="button" onClick={() => void escolherSegmento('CONFECTIONERY')}>Escolher</button>
    </>
  );
}

describe('TenantPresentationProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(obterApresentacao).mockResolvedValue(general);
    vi.mocked(obterContextos).mockResolvedValue({
      tenantId: 'tenant-teste',
      membershipId: 'membership-teste',
      contextos: [{
        id: 'tenant-teste',
        tipo: 'TENANT',
        codigo: 'tenant-teste',
        nome: 'Empresa Teste',
        papeis: ['atendente'],
        permissoes: ['contacts.read'],
        escopos: ['OWN'],
      }],
    });
    vi.mocked(obterPermissoes).mockResolvedValue({ 'contacts.read': 'OWN' });
    vi.mocked(salvarPerfilInicial).mockResolvedValue(confectionery);
  });

  it('updates the resolved menu immediately after saving the segment', async () => {
    renderComEstadoServidor(
      <TenantPresentationProvider><Harness /></TenantPresentationProvider>,
    );
    expect(await screen.findByText('Contatos')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Escolher' }));
    expect(await screen.findByText('Clientes')).toBeInTheDocument();
  });

  it('loads the persisted presentation again after a page remount', async () => {
    vi.mocked(obterApresentacao).mockResolvedValue(confectionery);
    const first = renderComEstadoServidor(
      <TenantPresentationProvider><Harness /></TenantPresentationProvider>,
    );
    expect(await first.findByText('Clientes')).toBeInTheDocument();
    first.unmount();

    const second = renderComEstadoServidor(
      <TenantPresentationProvider><Harness /></TenantPresentationProvider>,
    );
    expect(await second.findByText('Clientes')).toBeInTheDocument();
    await waitFor(() => expect(obterApresentacao).toHaveBeenCalledTimes(2));
  });

  it('usa o nome real do tenant e não oferece unidade sem ativação ponta a ponta', async () => {
    vi.mocked(obterContextos).mockResolvedValue({
      tenantId: 'tenant-teste',
      membershipId: 'membership-teste',
      contextos: [
        {
          id: 'tenant-teste',
          tipo: 'TENANT',
          codigo: 'tenant-teste',
          nome: 'PNP real',
          papeis: ['master'],
          permissoes: ['contacts.read'],
          escopos: ['TENANT'],
        },
        {
          id: 'unidade-centro',
          tipo: 'UNIT',
          codigo: 'centro',
          nome: 'Unidade Centro',
          papeis: ['atendente'],
          permissoes: ['contacts.read'],
          escopos: ['UNIT'],
        },
      ],
    });

    renderComEstadoServidor(
      <TenantPresentationProvider><Harness /></TenantPresentationProvider>,
    );

    expect(await screen.findByText('PNP real')).toBeInTheDocument();
    expect(screen.queryByText('Unidade Centro')).not.toBeInTheDocument();
  });
});
