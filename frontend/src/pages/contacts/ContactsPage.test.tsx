import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/lib/api';
import { contatosApi } from '@/shared/crm/api';
import { esperarSemViolacoesAcessiveis } from '@/test/accessibility';
import { renderComEstadoServidor } from '@/test/estadoServidor';
import { ContactsPage, FormularioDeContato } from './ContactsPage';

afterEach(() => vi.restoreAllMocks());

describe('formulário de contato', () => {
  it('valida localmente, anuncia e foca o primeiro erro', async () => {
    const { container } = render(<FormularioDeContato aoSalvar={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: /salvar contato/i }));

    const nome = screen.getByLabelText('Nome');
    expect(await screen.findByRole('alert')).toHaveTextContent(/revise os campos/i);
    expect(nome).toHaveFocus();
    expect(nome).toHaveAttribute('aria-invalid', 'true');
    expect(nome).toHaveAccessibleDescription('Informe o nome do contato.');
    await esperarSemViolacoesAcessiveis(container);
  });

  it('preserva valores e associa erro de campo devolvido pelo servidor', async () => {
    const aoSalvar = vi.fn().mockRejectedValue(new ApiError({
      message: 'Verifique os dados informados.',
      status: 400,
      kind: 'bad_request',
      campos: [{ campo: 'email', mensagem: 'E-mail já está em uso.' }],
    }));
    render(<FormularioDeContato aoSalvar={aoSalvar} />);

    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Maria' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'maria@example.test' } });
    fireEvent.click(screen.getByRole('button', { name: /salvar contato/i }));

    const email = await screen.findByLabelText('E-mail');
    await waitFor(() => expect(email).toHaveFocus());
    expect(email).toHaveValue('maria@example.test');
    expect(screen.getByLabelText('Nome')).toHaveValue('Maria');
    expect(email).toHaveAccessibleDescription('E-mail já está em uso.');
  });

  it('ignora submissão concorrente enquanto a primeira está pendente', async () => {
    let concluir!: () => void;
    const pendente = new Promise<void>((resolve) => { concluir = resolve; });
    const aoSalvar = vi.fn(() => pendente);
    render(<FormularioDeContato aoSalvar={aoSalvar} />);
    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: 'Maria' } });

    const formulario = screen.getByRole('button', { name: /salvar contato/i }).closest('form');
    expect(formulario).not.toBeNull();
    fireEvent.submit(formulario!);
    fireEvent.submit(formulario!);
    expect(aoSalvar).toHaveBeenCalledOnce();

    concluir();
    await waitFor(() => expect(screen.getByRole('button', { name: /salvar contato/i })).toBeEnabled());
  });

  it('restaura filtro e página da URL e consulta o contrato limitado', async () => {
    const listar = vi.spyOn(contatosApi, 'listar').mockResolvedValue([]);
    renderComEstadoServidor(
      <MemoryRouter initialEntries={['/contatos?busca=ana&pagina=2']}>
        <Routes>
          <Route path="/contatos" element={<ContactsPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('searchbox')).toHaveValue('ana');
    expect((await screen.findAllByText('Página 3')).length).toBeGreaterThan(0);
    await waitFor(() => expect(listar).toHaveBeenCalledWith(
      'ana', expect.any(AbortSignal), 2, 20,
    ));
    fireEvent.click(screen.getByRole('button', { name: /página anterior/i }));
    await waitFor(() => expect(listar).toHaveBeenCalledWith(
      'ana', expect.any(AbortSignal), 1, 20,
    ));
  });
});
