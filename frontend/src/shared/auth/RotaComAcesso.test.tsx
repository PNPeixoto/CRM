import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ROTAS } from '@/app/routes';
import { PaginaNaoEncontrada } from '@/shared/components/PaginaDeStatus';
import { resolveNavigation } from '@/shared/tenant/resolveNavigation';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { RotaComAcesso } from './RotaComAcesso';

describe('estados de rota', () => {
  it('renderiza 403 para módulo conhecido sem permissão', async () => {
    const rota = resolveNavigation([ROTAS[0]], null, { permissoes: [] })[0];
    const { container } = render(
      <RotaComAcesso rota={rota}>
        <span>Conteúdo protegido</span>
      </RotaComAcesso>,
    );

    expect(screen.getByRole('heading', { name: 'Acesso não permitido' })).toBeVisible();
    expect(screen.queryByText('Conteúdo protegido')).not.toBeInTheDocument();
    await esperarSemViolacoesAcessiveis(container);
  });

  it('renderiza conteúdo permitido e 404 desconhecido separadamente', async () => {
    const rota = resolveNavigation([ROTAS[0]], null)[0];
    const permitido = render(
      <RotaComAcesso rota={rota}>
        <span>Conteúdo permitido</span>
      </RotaComAcesso>,
    );
    expect(screen.getByText('Conteúdo permitido')).toBeVisible();
    permitido.unmount();

    const { container } = render(<PaginaNaoEncontrada />);
    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible();
    expect(screen.getByText(/Confira o endereço/i)).toBeVisible();
    await esperarSemViolacoesAcessiveis(container);
  });
});
