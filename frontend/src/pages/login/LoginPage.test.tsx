import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { definirAccessToken } from '@/lib/api';
import { instalarHttpMock } from '@/test/http';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { LoginPage } from './LoginPage';

afterEach(() => {
  definirAccessToken(null);
  vi.restoreAllMocks();
});

function tentarEntrar() {
  fireEvent.change(screen.getByLabelText('Empresa'), { target: { value: 'pnp' } });
  fireEvent.change(screen.getByLabelText('Login'), { target: { value: 'alguem' } });
  fireEvent.change(screen.getByLabelText('Senha'), {
    target: { value: 'irrelevante-para-o-mock' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

function montar(fixtures: Parameters<typeof instalarHttpMock>[0]) {
  const http = instalarHttpMock([
    { metodo: 'POST', caminho: '/api/auth/refresh', status: 401, json: {} },
    ...fixtures,
  ]);
  render(
    <MemoryRouter>
      <AuthProvider><LoginPage /></AuthProvider>
    </MemoryRouter>,
  );
  return http;
}

describe('mensagens da tela de entrada', () => {
  it('mantém falha de credencial indistinguível', async () => {
    const http = montar([
      {
        metodo: 'POST',
        caminho: '/api/auth/login',
        status: 401,
        json: { codigo: 'CREDENCIAIS_INVALIDAS' },
      },
    ]);

    await waitFor(() => expect(screen.getByLabelText('Empresa')).toBeEnabled());
    tentarEntrar();

    // Empresa inexistente, login inexistente e senha errada precisam produzir
    // exatamente esta frase, ou a tela conta a quem está de fora o que existe.
    await waitFor(() =>
      expect(screen.getByText('Empresa, login ou senha inválidos.')).toBeVisible());
    http.verificarTudoConsumido();
  });

  it('não acusa senha errada quando o que falta é o segundo fator', async () => {
    const http = montar([
      {
        metodo: 'POST',
        caminho: '/api/auth/login',
        status: 422,
        json: { codigo: 'MFA_CADASTRO_NECESSARIO' },
      },
    ]);

    await waitFor(() => expect(screen.getByLabelText('Empresa')).toBeEnabled());
    tentarEntrar();

    // Este código só alcança quem já provou a senha. Escondê-lo atrás de
    // "senha inválida" é uma acusação falsa, e deixa a pessoa repetindo uma
    // senha que já estava certa.
    await waitFor(() => expect(screen.getByText(/verificação em duas etapas/i)).toBeVisible());
    expect(screen.queryByText('Empresa, login ou senha inválidos.')).not.toBeInTheDocument();
    http.verificarTudoConsumido();
  });

  it('usa a mensagem uniforme para código desconhecido', async () => {
    const http = montar([
      {
        metodo: 'POST',
        caminho: '/api/auth/login',
        status: 422,
        json: { codigo: 'REGRA_QUE_AINDA_NAO_EXISTE' },
      },
    ]);

    await waitFor(() => expect(screen.getByLabelText('Empresa')).toBeEnabled());
    tentarEntrar();

    // Falha fechada: código novo do backend não vaza texto por engano.
    await waitFor(() =>
      expect(screen.getByText('Empresa, login ou senha inválidos.')).toBeVisible());
    http.verificarTudoConsumido();
  });
});
