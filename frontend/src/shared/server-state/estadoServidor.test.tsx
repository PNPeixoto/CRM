import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Contato, Tarefa } from '@/shared/crm/tipos';
import { chaveDoRecurso, type ContextoDeEstadoServidor } from './chaves';
import {
  FronteiraDeEstadoServidor,
} from './EstadoServidorProvider';
import { criarClienteDeEstadoServidor } from './cliente';
import {
  useAlternarConclusaoDeTarefa,
  useContatos,
  useCriarContato,
} from './recursos';
import { limparCachesDeServidor } from './registro';

const mocks = vi.hoisted(() => ({
  listarContatos: vi.fn(),
  criarContato: vi.fn(),
  alternarTarefa: vi.fn(),
}));

vi.mock('@/shared/crm/api', () => ({
  contatosApi: {
    listar: mocks.listarContatos,
    criar: mocks.criarContato,
  },
  tarefasApi: {
    alternarConclusao: mocks.alternarTarefa,
  },
}));

const contextoA: ContextoDeEstadoServidor = {
  tenantId: 'tenant-a',
  unidadeId: 'unidade-a',
  identidadeId: 'usuario-a',
};
const contextoB: ContextoDeEstadoServidor = {
  tenantId: 'tenant-b',
  unidadeId: null,
  identidadeId: 'usuario-b',
};

describe('estado de servidor isolado', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('inclui tenant, unidade, identidade e parâmetros em todas as chaves', () => {
    expect(chaveDoRecurso(contextoA, 'contatos', { busca: 'maria' })).toEqual([
      'servidor',
      'tenant-a',
      'unidade-a',
      'usuario-a',
      'contatos',
      { busca: 'maria' },
    ]);
    expect(chaveDoRecurso(contextoB, 'contatos', { busca: 'maria' }))
      .not.toEqual(chaveDoRecurso(contextoA, 'contatos', { busca: 'maria' }));
  });

  it('não mostra dados da identidade anterior durante a troca de contexto', async () => {
    const primeira = contato('antigo', 'Dado antigo');
    const segunda = contato('novo', 'Dado novo');
    mocks.listarContatos.mockResolvedValueOnce([primeira]).mockResolvedValueOnce([segunda]);

    const view = render(
      <FronteiraDeEstadoServidor key="a" contexto={contextoA}>
        <ListaDeContatos busca="" />
      </FronteiraDeEstadoServidor>,
    );
    expect(await screen.findByText('Dado antigo')).toBeInTheDocument();

    view.rerender(
      <FronteiraDeEstadoServidor key="b" contexto={contextoB}>
        <ListaDeContatos busca="" />
      </FronteiraDeEstadoServidor>,
    );

    expect(screen.queryByText('Dado antigo')).not.toBeInTheDocument();
    expect(await screen.findByText('Dado novo')).toBeInTheDocument();
  });

  it('ignora resposta antiga que chega depois da busca atual', async () => {
    const antiga = adiar<readonly Contato[]>();
    const atual = adiar<readonly Contato[]>();
    mocks.listarContatos.mockReturnValueOnce(antiga.promise).mockReturnValueOnce(atual.promise);

    const view = render(
      <FronteiraDeEstadoServidor contexto={contextoA}>
        <ListaDeContatos busca="ma" />
      </FronteiraDeEstadoServidor>,
    );
    view.rerender(
      <FronteiraDeEstadoServidor contexto={contextoA}>
        <ListaDeContatos busca="maria" />
      </FronteiraDeEstadoServidor>,
    );

    atual.resolve([contato('atual', 'Maria atual')]);
    expect(await screen.findByText('Maria atual')).toBeInTheDocument();
    antiga.resolve([contato('antiga', 'Maria antiga')]);
    await Promise.resolve();
    expect(screen.queryByText('Maria antiga')).not.toBeInTheDocument();
    expect(screen.getByText('Maria atual')).toBeInTheDocument();
  });

  it('faz rollback determinístico quando a atualização otimista falha', async () => {
    const cliente = criarClienteDeEstadoServidor();
    const chave = chaveDoRecurso(contextoA, 'tarefas', { apenasAbertas: true });
    const tarefa = tarefaAberta();
    cliente.setQueryData(chave, [tarefa]);
    const resposta = adiar<Tarefa>();
    mocks.alternarTarefa.mockReturnValue(resposta.promise);

    render(
      <FronteiraDeEstadoServidor contexto={contextoA} cliente={cliente}>
        <AlternarTarefa />
      </FronteiraDeEstadoServidor>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Alternar' }));
    await waitFor(() => expect(cliente.getQueryData(chave)).toEqual([]));

    resposta.reject(new Error('falha sintética'));
    await waitFor(() => expect(cliente.getQueryData(chave)).toEqual([tarefa]));
  });

  it('invalida somente contatos e visão geral depois de criar um contato', async () => {
    const cliente = criarClienteDeEstadoServidor();
    const contatos = chaveDoRecurso(contextoA, 'contatos', { busca: '' });
    const visao = chaveDoRecurso(contextoA, 'visao-geral');
    const canais = chaveDoRecurso(contextoA, 'canais');
    cliente.setQueryData(contatos, []);
    cliente.setQueryData(visao, { totalDeContatos: 0 });
    cliente.setQueryData(canais, []);
    mocks.criarContato.mockResolvedValue(contato('novo', 'Novo contato'));

    render(
      <FronteiraDeEstadoServidor contexto={contextoA} cliente={cliente}>
        <CriarContato />
      </FronteiraDeEstadoServidor>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Criar' }));
    expect(await screen.findByText('Concluído')).toBeInTheDocument();

    expect(cliente.getQueryState(contatos)?.isInvalidated).toBe(true);
    expect(cliente.getQueryState(visao)?.isInvalidated).toBe(true);
    expect(cliente.getQueryState(canais)?.isInvalidated).toBe(false);
  });

  it('apaga caches no logout sem persistir dados no navegador', async () => {
    const cliente = criarClienteDeEstadoServidor();
    const chave = chaveDoRecurso(contextoA, 'contatos', { busca: '' });
    const view = render(
      <FronteiraDeEstadoServidor contexto={contextoA} cliente={cliente}>
        <span>sessão</span>
      </FronteiraDeEstadoServidor>,
    );
    cliente.setQueryData(chave, [contato('sensivel', 'Dado sensível')]);

    limparCachesDeServidor();
    expect(cliente.getQueryCache().getAll()).toHaveLength(0);
    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
    view.unmount();
  });
});

function ListaDeContatos({ busca }: { readonly busca: string }) {
  const query = useContatos(busca);
  if (query.isPending) return <span>Carregando</span>;
  return <>{query.data?.map((item) => <span key={item.id}>{item.nome}</span>)}</>;
}

function AlternarTarefa() {
  const mutation = useAlternarConclusaoDeTarefa();
  return (
    <button type="button" onClick={() => void mutation.mutateAsync('tarefa-1').catch(() => {})}>
      Alternar
    </button>
  );
}

function CriarContato() {
  const mutation = useCriarContato();
  const [concluido, setConcluido] = useState(false);
  return (
    <>
      <button
        type="button"
        onClick={() => void mutation.mutateAsync({ nome: 'Novo contato' }).then(() => setConcluido(true))}
      >
        Criar
      </button>
      {concluido && <span>Concluído</span>}
    </>
  );
}

function contato(id: string, nome: string): Contato {
  return {
    id,
    nome,
    email: null,
    telefone: null,
    empresa: null,
    observacoes: null,
    responsavelId: null,
    responsavelLogin: null,
    responsavelNome: null,
    criadoEm: '2026-08-08T10:00:00Z',
  };
}

function tarefaAberta(): Tarefa {
  return {
    id: 'tarefa-1',
    titulo: 'Revisar proposta',
    descricao: null,
    vencimentoEm: null,
    concluidaEm: null,
    responsavelId: null,
    responsavelLogin: null,
    responsavelNome: null,
    contatoId: null,
    oportunidadeId: null,
    criadaEm: '2026-08-08T10:00:00Z',
  };
}

function adiar<T>() {
  let resolve!: (valor: T) => void;
  let reject!: (erro: unknown) => void;
  const promise = new Promise<T>((resolver, rejeitar) => {
    resolve = resolver;
    reject = rejeitar;
  });
  return { promise, resolve, reject };
}
