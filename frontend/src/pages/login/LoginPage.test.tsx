import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { definirAccessToken, obterAccessToken } from '@/lib/api';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { instalarHttpMock } from '@/test/http';
import { LoginPage } from './LoginPage';

afterEach(() => {
  definirAccessToken(null);
  vi.restoreAllMocks();
});

const USUARIO = {
  id: '019fa91c-0f66-7a68-8384-20e85b6c8f5a',
  tenantId: '019fa91c-0f63-75f7-b4a0-1494c1304c42',
  login: 'alguem',
  nomeCompleto: 'Alguém de Teste',
};

function sessao(accessToken: string) {
  return {
    accessToken,
    expiraEmSegundos: 900,
    mfaVerificado: true,
    usuario: USUARIO,
  };
}

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

describe('entrada e segundo fator', () => {
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

    await waitFor(() =>
      expect(screen.getByText('Empresa, login ou senha inválidos.')).toBeVisible());
    http.verificarTudoConsumido();
  });

  it('cadastra MFA no primeiro login administrativo e mostra recovery codes uma vez', async () => {
    const http = montar([
      {
        metodo: 'POST',
        caminho: '/api/auth/login',
        status: 422,
        json: { codigo: 'MFA_CADASTRO_NECESSARIO' },
      },
      {
        metodo: 'POST',
        caminho: '/api/auth/mfa/enrollment',
        json: {
          desafio: 'desafio-sintetico',
          segredo: 'JBSWY3DPEHPK3PXP',
          otpauthUri: 'otpauth://totp/pnp%3Aalguem?secret=JBSWY3DPEHPK3PXP',
          expiraEmSegundos: 600,
        },
      },
      {
        metodo: 'POST',
        caminho: '/api/auth/mfa/activation',
        json: {
          sessao: sessao('token-com-mfa'),
          codigosRecuperacao: ['AAAA-BBBB-CCCC-DDDD', 'EEEE-FFFF-GGGG-HHHH'],
        },
      },
    ]);

    await waitFor(() => expect(screen.getByLabelText('Empresa')).toBeEnabled());
    tentarEntrar();

    await waitFor(() => expect(screen.getByText('Proteja sua conta')).toBeVisible());
    expect(screen.getByLabelText('Chave de configuração do autenticador'))
      .toHaveTextContent('JBSWY3DPEHPK3PXP');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);

    fireEvent.change(screen.getByLabelText('Código de 6 dígitos'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Ativar e entrar' }));

    await waitFor(() => expect(screen.getByText('Verificação ativada')).toBeVisible());
    expect(screen.getByText('AAAA-BBBB-CCCC-DDDD')).toBeVisible();
    expect(screen.getByText('EEEE-FFFF-GGGG-HHHH')).toBeVisible();
    expect(obterAccessToken()).toBe('token-com-mfa');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verificarTudoConsumido();
  });

  it('pede o código quando o autenticador já está cadastrado', async () => {
    const http = montar([
      {
        metodo: 'POST',
        caminho: '/api/auth/login',
        status: 422,
        json: { codigo: 'MFA_NECESSARIO' },
      },
      {
        metodo: 'POST',
        caminho: '/api/auth/login',
        json: sessao('token-verificado'),
      },
    ]);

    await waitFor(() => expect(screen.getByLabelText('Empresa')).toBeEnabled());
    tentarEntrar();

    await waitFor(() => expect(screen.getByText('Verificação em duas etapas')).toBeVisible());
    expect(screen.queryByText('Empresa, login ou senha inválidos.')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Código de verificação'), {
      target: { value: 'AAAA-BBBB-CCCC-DDDD' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Verificar e entrar' }));

    await waitFor(() => expect(obterAccessToken()).toBe('token-verificado'));
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

    await waitFor(() =>
      expect(screen.getByText('Empresa, login ou senha inválidos.')).toBeVisible());
    http.verificarTudoConsumido();
  });
});
