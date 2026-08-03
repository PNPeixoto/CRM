import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { definirAccessToken } from '@/lib/api';
import { instalarHttpMock } from '@/test/http';
import { AuthProvider } from './AuthContext';
import { RotaProtegida } from './RotaProtegida';

afterEach(() => {
  definirAccessToken(null);
  vi.restoreAllMocks();
});

const USUARIO_SINTETICO = {
  id: '019fa91c-0f66-7a68-8384-20e85b6c8f5a',
  tenantId: '019fa91c-0f63-75f7-b4a0-1494c1304c42',
  login: 'sintetico',
  nomeCompleto: 'Usuário Sintético',
};

/** Espia o destino e o estado guardado, que é o que alimenta o retorno pós-login. */
function EspiaDeLogin() {
  const localizacao = useLocation();
  const estado = localizacao.state as { de?: string } | null;
  return <span data-testid="login">login|de={estado?.de ?? '(vazio)'}</span>;
}

function montar(entrada: string) {
  return render(
    <MemoryRouter initialEntries={[entrada]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<EspiaDeLogin />} />
          <Route element={<RotaProtegida />}>
            <Route path="/contatos" element={<span>lista de contatos</span>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('guarda de rota autenticada', () => {
  it('aguarda a resolução inicial antes de decidir', async () => {
    // Sem isto, todo usuário com sessão válida seria jogado para o login a
    // cada F5: o redirecionamento aconteceria antes de o refresh responder.
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', json: { accessToken: 'token-vivo' } },
      { caminho: '/api/auth/me', json: USUARIO_SINTETICO },
    ]);

    montar('/contatos');

    // Estado intermediário explícito, e não um piscar para o login.
    expect(screen.getByText('Verificando sessão…')).toBeVisible();
    expect(screen.queryByTestId('login')).not.toBeInTheDocument();

    await waitFor(() => expect(screen.getByText('lista de contatos')).toBeVisible());
    http.verificarTudoConsumido();
  });

  it('manda ao login e preserva o destino quando não há sessão', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', status: 401, json: {} },
    ]);

    montar('/contatos');

    await waitFor(() => expect(screen.getByTestId('login')).toBeVisible());
    expect(screen.getByTestId('login')).toHaveTextContent('de=/contatos');
    expect(screen.queryByText('lista de contatos')).not.toBeInTheDocument();
    http.verificarTudoConsumido();
  });

  it('preserva consulta e âncora do destino', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', status: 401, json: {} },
    ]);

    montar('/contatos?busca=ana#topo');

    await waitFor(() => expect(screen.getByTestId('login')).toBeVisible());
    // Devolver à lista sem o filtro que a pessoa tinha aberto é devolver ao
    // lugar errado com aparência de acerto.
    expect(screen.getByTestId('login')).toHaveTextContent('de=/contatos?busca=ana#topo');
    http.verificarTudoConsumido();
  });
});
