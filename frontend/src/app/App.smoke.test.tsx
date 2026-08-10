import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppRouter } from './router';
import { definirAccessToken } from '@/lib/api';
import { analisarAcessibilidade, esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { instalarHttpMock } from '@/test/http';

describe('smoke acessível da aplicação', () => {
  beforeEach(() => {
    definirAccessToken(null);
    window.history.replaceState(null, '', '/login');
  });

  it('renderiza o login por papéis e rótulos acessíveis', async () => {
    const http = instalarHttpMock([
      { metodo: 'POST', caminho: '/api/auth/refresh', status: 401 },
    ]);
    const { container } = render(<AppRouter />);

    expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Empresa' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Login' })).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeEnabled();

    await waitFor(() => http.verificarTudoConsumido());
    await esperarSemViolacoesAcessiveis(container);
  });

  it('prova que o auditor detecta uma violação acessível plantada', async () => {
    const { container } = render(<button type="button" />);
    const resultado = await analisarAcessibilidade(container);

    expect(resultado.violations.map((violacao) => violacao.id)).toContain('button-name');
  });

  it('mantém a casca e apresenta 404 para rota desconhecida', async () => {
    window.history.replaceState(null, '', '/rota-inexistente');
    const http = instalarHttpMock([
      {
        metodo: 'POST',
        caminho: '/api/auth/refresh',
        json: { accessToken: 'token-sintetico' },
      },
      {
        caminho: '/api/auth/me',
        json: {
          id: 'usuario-sintetico',
          tenantId: 'tenant-sintetico',
          login: 'operador',
          nomeCompleto: 'Operador Sintético',
        },
      },
      {
        caminho: '/api/empresa/apresentacao',
        json: {
          segmento: 'GENERAL_SERVICES',
          versaoPreset: 1,
          onboardingConcluido: true,
          navegacao: [],
          funilPadrao: { nome: 'Funil', etapas: [] },
        },
      },
      {
        caminho: '/api/organizacao/permissoes',
        json: { 'contacts.read': 'OWN' },
      },
      contextoOrganizacional(),
    ]);
    const { container } = render(<AppRouter />);

    expect(
      await screen.findByRole('heading', { name: 'Página não encontrada' }),
    ).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    http.verificarTudoConsumido();
    await esperarSemViolacoesAcessiveis(container);
  });

  it('blocks a known route before calling an API without permission', async () => {
    window.history.replaceState(null, '', '/dashboard');
    const http = instalarHttpMock([
      {
        metodo: 'POST',
        caminho: '/api/auth/refresh',
        json: { accessToken: 'token-sintetico' },
      },
      {
        caminho: '/api/auth/me',
        json: {
          id: 'usuario-sintetico',
          tenantId: 'tenant-sintetico',
          login: 'atendente',
          nomeCompleto: 'Atendente Sintetico',
        },
      },
      {
        caminho: '/api/empresa/apresentacao',
        json: {
          segmento: 'GENERAL_SERVICES',
          versaoPreset: 1,
          onboardingConcluido: true,
          navegacao: [],
          funilPadrao: { nome: 'Funil', etapas: [] },
        },
      },
      {
        caminho: '/api/organizacao/permissoes',
        json: {
          'contacts.read': 'OWN',
          'conversations.read': 'TENANT',
        },
      },
      contextoOrganizacional(),
    ]);

    render(<AppRouter />);

    expect(await screen.findByRole('heading', { name: /Acesso.*permitido/i })).toBeVisible();
    expect(http.chamadas.some(({ caminho }) => caminho === '/api/relatorios/visao-geral')).toBe(false);
    http.verificarTudoConsumido();
  });
});

function contextoOrganizacional() {
  return {
    caminho: '/api/organizacao/contextos',
    json: {
      tenantId: 'tenant-sintetico',
      membershipId: 'membership-sintetico',
      contexts: [{
        id: 'tenant-sintetico',
        type: 'TENANT',
        code: 'empresa-sintetica',
        name: 'Empresa Sintética',
        roles: ['operador'],
        permissions: ['contacts.read'],
        scopes: ['OWN'],
      }],
    },
  } as const;
}
