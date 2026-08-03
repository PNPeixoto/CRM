import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';

describe('primitivas de formulário', () => {
  it('mantém botão comum fora do fluxo de submit e respeita disabled', () => {
    const aoClicar = vi.fn();
    const { rerender } = render(<Button onClick={aoClicar}>Continuar</Button>);

    const botao = screen.getByRole('button', { name: 'Continuar' });
    expect(botao).toHaveAttribute('type', 'button');
    fireEvent.click(botao);
    expect(aoClicar).toHaveBeenCalledOnce();

    rerender(
      <Button onClick={aoClicar} disabled>
        Continuar
      </Button>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(aoClicar).toHaveBeenCalledOnce();
  });

  it('preserva a semântica do elemento quando usa asChild', () => {
    render(
      <Button asChild>
        <a href="/destino">Abrir destino</a>
      </Button>,
    );

    expect(screen.getByRole('link', { name: 'Abrir destino' })).toHaveAttribute(
      'href',
      '/destino',
    );
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('associa rótulos, erro e descrição aos campos nativos', async () => {
    const { container } = render(
      <form>
        <Label htmlFor="nome">Nome</Label>
        <Input id="nome" aria-invalid="true" aria-describedby="erro-nome" />
        <p id="erro-nome">Informe o nome.</p>

        <Label htmlFor="tipo">Tipo</Label>
        <Select id="tipo" defaultValue="cliente">
          <option value="cliente">Cliente</option>
        </Select>

        <AlertaErro>Não foi possível salvar.</AlertaErro>
        <Button type="submit">Salvar</Button>
      </form>,
    );

    expect(screen.getByLabelText('Nome')).toHaveAccessibleDescription('Informe o nome.');
    expect(screen.getByLabelText('Nome')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('combobox', { name: 'Tipo' })).toHaveValue('cliente');
    expect(screen.getByRole('alert')).toHaveTextContent('Não foi possível salvar.');
    await esperarSemViolacoesAcessiveis(container);
  });

  it.each(['light', 'dark'] as const)('renderiza as primitivas no tema %s', async (tema) => {
    document.documentElement.dataset.theme = tema;
    const { container } = render(
      <div>
        <Label htmlFor={`campo-${tema}`}>Campo</Label>
        <Input id={`campo-${tema}`} disabled />
        <Label htmlFor={`selecao-${tema}`}>Seleção</Label>
        <Select id={`selecao-${tema}`} disabled>
          <option>Opção</option>
        </Select>
        <Button disabled>Indisponível</Button>
        <AlertaErro>Falha conhecida.</AlertaErro>
      </div>,
    );

    expect(document.documentElement).toHaveAttribute('data-theme', tema);
    await esperarSemViolacoesAcessiveis(container);
  });
});
