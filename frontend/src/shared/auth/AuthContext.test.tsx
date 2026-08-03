import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { definirAccessToken, obterAccessToken } from '@/lib/api';
import { instalarHttpMock } from '@/test/http';
import { AuthProvider, useAuth } from './AuthContext';

afterEach(() => {
  definirAccessToken(null);
  vi.restoreAllMocks();
});

function Sonda() {
  const { usuario, carregando } = useAuth();
  if (carregando) return <span>verificando</span>;
  return <span>{usuario ? `sessao:${usuario.login}` : 'sem sessao'}</span>;
}

const USUARIO_SINTETICO = {
  id: '019fa91c-0f66-7a68-8384-20e85b6c8f5a',
  tenantId: '019fa91c-0f63-75f7-b4a0-1494c1304c42',
  login: 'sintetico',
  nomeCompleto: 'Usuário Sintético',
};

describe('sessão do provedor de autenticação', () => {
  it('reconstrói a sessão pelo refresh ao montar, sem ler armazenamento', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', json: { accessToken: 'token-vivo' } },
      { caminho: '/api/auth/me', json: USUARIO_SINTETICO },
    ]);

    render(<AuthProvider><Sonda /></AuthProvider>);

    await waitFor(() => expect(screen.getByText('sessao:sintetico')).toBeVisible());

    // O token existe em memória e em lugar nenhum além dela: é o que faz um
    // XSS que rode uma vez não colher credencial duradoura.
    expect(obterAccessToken()).toBe('token-vivo');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verificarTudoConsumido();
  });

  it('permanece sem sessão quando não há refresh válido', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', status: 401, json: {} },
    ]);

    render(<AuthProvider><Sonda /></AuthProvider>);

    await waitFor(() => expect(screen.getByText('sem sessao')).toBeVisible());
    expect(obterAccessToken()).toBeNull();
    http.verificarTudoConsumido();
  });

  it('encerra a sessão quando outra aba anuncia a saída', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', json: { accessToken: 'token-vivo' } },
      { caminho: '/api/auth/me', json: USUARIO_SINTETICO },
    ]);

    render(<AuthProvider><Sonda /></AuthProvider>);
    await waitFor(() => expect(screen.getByText('sessao:sintetico')).toBeVisible());

    // Outra aba saiu. Esta não recebe token nem dado do usuário — só o aviso.
    const outraAba = new BroadcastChannel('crm-pnp:sessao:v1');
    outraAba.postMessage({ tipo: 'saiu' });

    await waitFor(() => expect(screen.getByText('sem sessao')).toBeVisible());
    expect(obterAccessToken()).toBeNull();

    outraAba.close();
    http.verificarTudoConsumido();
  });
});
