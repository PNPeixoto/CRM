import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ROTAS } from '@/app/routes';
import { useAuth } from '@/shared/auth/AuthContext';
import { chaveDaPreferenciaDeMenu } from '@/shared/preferences/menuPreference';
import { resolveNavigation } from '@/shared/tenant/resolveNavigation';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { AppLayout, type ContextoAutorizado } from './AppLayout';

vi.mock('@/shared/auth/AuthContext', () => ({ useAuth: vi.fn() }));

const usuario = {
  id: 'usuario-shell-1',
  tenantId: 'tenant-shell-1',
  login: 'operador',
  nomeCompleto: 'Operador Sintético',
};
const navigation = resolveNavigation(ROTAS.slice(0, 2), null);
const contextoUnico = [{ id: usuario.tenantId, rotulo: 'Empresa atual' }];

describe('AppLayout', () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue({
      usuario,
      carregando: false,
      entrar: vi.fn(),
      sair: vi.fn(),
    });
  });

  it('oferece skip link e menu mobile com foco e Escape', async () => {
    const { container } = renderLayout({ contextosAutorizados: contextoUnico });

    expect(screen.getByRole('link', { name: 'Pular para o conteúdo' })).toHaveAttribute(
      'href',
      '#conteudo-principal',
    );
    const abrir = screen.getByRole('button', { name: 'Abrir menu' });
    expect(abrir).toHaveAttribute('aria-expanded', 'false');

    fireEvent.click(abrir);
    expect(screen.getByRole('button', { name: 'Fechar menu' })).toHaveAttribute(
      'aria-expanded',
      'true',
    );
    await waitFor(() => expect(screen.getByRole('link', { name: 'Visão geral' })).toHaveFocus());

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getByRole('button', { name: 'Abrir menu' })).toHaveFocus();
    await esperarSemViolacoesAcessiveis(container);
  });

  it('não mostra seletor com um contexto e persiste menu recolhido sem dados do usuário', async () => {
    const primeira = renderLayout({ contextosAutorizados: contextoUnico });
    expect(screen.queryByLabelText('Empresa ou unidade ativa')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Recolher menu' }));
    const chave = chaveDaPreferenciaDeMenu(usuario.id);
    expect(localStorage.getItem(chave)).toBe('1');
    expect(chave).not.toContain(usuario.id);
    expect(localStorage.length).toBe(1);
    primeira.unmount();

    renderLayout({ contextosAutorizados: contextoUnico });
    expect(await screen.findByRole('button', { name: 'Expandir menu' })).toBeVisible();
  });

  it('mostra apenas contextos autorizados e desmonta dados antigos durante a troca lenta', async () => {
    const contextos: ContextoAutorizado[] = [
      { id: 'unidade-1', rotulo: 'Unidade Centro' },
      { id: 'unidade-2', rotulo: 'Unidade Norte' },
    ];
    let concluir: (() => void) | undefined;
    const aoTrocarContexto = vi.fn(
      () => new Promise<void>((resolve) => { concluir = resolve; }),
    );
    renderLayout({
      contextosAutorizados: contextos,
      contextoAtivoId: 'unidade-1',
      aoTrocarContexto,
    });

    const seletor = screen.getByRole('combobox', { name: 'Empresa ou unidade ativa' });
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Unidade Centro',
      'Unidade Norte',
    ]);
    fireEvent.change(seletor, { target: { value: 'unidade-2' } });

    expect(aoTrocarContexto).toHaveBeenCalledWith('unidade-2');
    expect(screen.queryByText('Dados do contexto anterior')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Trocando contexto…');

    concluir?.();
    await waitFor(() => expect(screen.getByText('Dados do contexto anterior')).toBeVisible());
  });
});

function renderLayout({
  contextosAutorizados,
  contextoAtivoId = usuario.tenantId,
  aoTrocarContexto,
}: {
  readonly contextosAutorizados: readonly ContextoAutorizado[];
  readonly contextoAtivoId?: string;
  readonly aoTrocarContexto?: (contextoId: string) => Promise<void>;
}) {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route
          element={
            <AppLayout
              navigation={navigation}
              contextosAutorizados={contextosAutorizados}
              contextoAtivoId={contextoAtivoId}
              aoTrocarContexto={aoTrocarContexto}
            />
          }
        >
          <Route path="/dashboard" element={<p>Dados do contexto anterior</p>} />
          <Route path="/inbox" element={<p>Inbox</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
