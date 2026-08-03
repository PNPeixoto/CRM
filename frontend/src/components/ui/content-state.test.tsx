import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Button } from '@/components/ui/button';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';

describe('EstadoDeConteudo', () => {
  it('anuncia carregamento e mantém o esqueleto apenas visual', () => {
    const { container } = render(
      <EstadoDeConteudo tipo="carregando" titulo="Carregando contatos…" />,
    );

    const estado = screen.getByRole('status');
    expect(estado).toHaveAttribute('aria-busy', 'true');
    expect(estado).toHaveTextContent('Carregando contatos…');
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(2);
  });

  it('distingue vazio inicial de filtro sem resultado', () => {
    render(
      <>
        <EstadoDeConteudo tipo="vazio-inicial" />
        <EstadoDeConteudo tipo="sem-resultados" />
      </>,
    );

    expect(screen.getByText('Nada por aqui ainda')).toBeInTheDocument();
    expect(screen.getByText('Nenhum resultado encontrado')).toBeInTheDocument();
    expect(screen.getByText(/primeiro registro/i)).toBeInTheDocument();
    expect(screen.getByText(/revise os filtros/i)).toBeInTheDocument();
  });

  it('expõe erro, falta de permissão e offline com semântica própria', async () => {
    const { container } = render(
      <div>
        <EstadoDeConteudo
          tipo="erro"
          acao={<Button>Tentar novamente</Button>}
        />
        <EstadoDeConteudo tipo="sem-permissao" />
        <EstadoDeConteudo tipo="offline" />
      </div>,
    );

    expect(within(screen.getByRole('alert')).getByText('Não foi possível carregar')).toBeVisible();
    expect(screen.getByText('Acesso não permitido')).toBeVisible();
    expect(within(screen.getByRole('status')).getByText('Você está offline')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeEnabled();
    await esperarSemViolacoesAcessiveis(container);
  });

  it.each(['light', 'dark'] as const)('renderiza os estados no tema %s', async (tema) => {
    document.documentElement.dataset.theme = tema;
    const { container } = render(
      <div>
        <EstadoDeConteudo tipo="carregando" />
        <EstadoDeConteudo tipo="vazio-inicial" />
        <EstadoDeConteudo tipo="sem-resultados" />
        <EstadoDeConteudo tipo="erro" />
        <EstadoDeConteudo tipo="sem-permissao" />
        <EstadoDeConteudo tipo="nao-encontrado" />
        <EstadoDeConteudo tipo="offline" />
      </div>,
    );

    expect(document.documentElement).toHaveAttribute('data-theme', tema);
    await esperarSemViolacoesAcessiveis(container);
  });
});
