import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/lib/api';
import { contatosApi, funilApi, tarefasApi } from '@/shared/crm/api';
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

  it('abre a ficha 360 pelo identificador e carrega somente as relações do contato', async () => {
    const listar = vi.spyOn(contatosApi, 'listar').mockResolvedValue([]);
    const obter = vi.spyOn(contatosApi, 'obter').mockResolvedValue({
      id: 'contact-1',
      nome: 'Maria Silva',
      email: 'maria@horizonte.test',
      telefone: '+55 11 99999-0000',
      empresa: 'Grupo Horizonte',
      observacoes: 'Prefere contato no período da tarde.',
      responsavelId: 'user-carla',
      responsavelLogin: 'carla',
      responsavelNome: 'Carla Mendes',
      criadoEm: '2026-07-18T13:00:00Z',
    });
    const listarOportunidades = vi.spyOn(funilApi, 'listarOportunidadesDoContato')
      .mockResolvedValue([{
        id: 'deal-1',
        funilId: 'pipeline-1',
        etapaId: 'stage-1',
        contatoId: 'contact-1',
        titulo: 'Expansão Grupo Horizonte',
        valorCentavos: 12800000,
        status: 'OPEN',
        previsaoFechamento: '2026-08-31',
        motivoPerda: null,
        responsavelId: 'user-carla',
        responsavelLogin: 'carla',
        responsavelNome: 'Carla Mendes',
        criadaEm: '2026-08-01T12:00:00Z',
      }]);
    const listarTarefas = vi.spyOn(tarefasApi, 'listar').mockResolvedValue([{
      id: 'task-1',
      titulo: 'Follow-up com Maria Silva',
      descricao: null,
      vencimentoEm: '2026-08-17T16:00:00Z',
      concluidaEm: null,
      responsavelId: 'user-carla',
      responsavelLogin: 'carla',
      responsavelNome: 'Carla Mendes',
      contatoId: 'contact-1',
      oportunidadeId: 'deal-1',
      criadaEm: '2026-08-10T12:00:00Z',
    }]);

    const { container } = renderComEstadoServidor(
      <MemoryRouter initialEntries={['/contatos?contato=contact-1']}>
        <Routes>
          <Route path="/contatos" element={<ContactsPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'Maria Silva' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'maria@horizonte.test' })).toHaveAttribute(
      'href',
      'mailto:maria@horizonte.test',
    );
    expect(screen.getByText('Expansão Grupo Horizonte')).toBeVisible();
    expect(screen.getByText('Follow-up com Maria Silva')).toBeVisible();
    expect(screen.getAllByText('R$ 128.000,00')).toHaveLength(2);
    expect(listar).not.toHaveBeenCalled();
    expect(obter).toHaveBeenCalledWith('contact-1', expect.any(AbortSignal));
    expect(listarOportunidades).toHaveBeenCalledWith(
      'contact-1',
      expect.any(AbortSignal),
    );
    expect(listarTarefas).toHaveBeenCalledWith(
      false,
      expect.any(AbortSignal),
      'contact-1',
    );
    await esperarSemViolacoesAcessiveis(container);
  });
});
