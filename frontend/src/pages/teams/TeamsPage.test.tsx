import { fireEvent, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { organizacaoApi } from '@/shared/organizacao/api';
import { TeamsPage } from './TeamsPage';
import { renderComEstadoServidor } from '@/test/estadoServidor';

vi.mock('@/shared/organizacao/api', () => ({
  organizacaoApi: {
    catalogo: vi.fn(),
    criarPapel: vi.fn(),
    atualizarPapel: vi.fn(),
    definirPermissoes: vi.fn(),
    removerPapel: vi.fn(),
    membros: vi.fn(),
    atribuirPapel: vi.fn(),
    revogarPapel: vi.fn(),
    equipes: vi.fn(),
    incluirNaEquipe: vi.fn(),
    removerDaEquipe: vi.fn(),
  },
}));

const CATALOGO = {
  papeis: [
    {
      id: 'papel-sdr', codigo: 'SDR', nome: 'SDR', descricao: null,
      sistema: false, ativo: true, permissoes: ['contacts.read'],
      gerenciavel: true, atribuicoes: 0,
    },
    {
      id: 'papel-owner', codigo: 'OWNER', nome: 'Proprietário', descricao: null,
      sistema: true, ativo: true, permissoes: ['organization.manage'],
      gerenciavel: false, atribuicoes: 1,
    },
    {
      id: 'papel-auditor', codigo: 'AUDITOR', nome: 'Auditor', descricao: null,
      sistema: false, ativo: true, permissoes: ['audit.read'],
      gerenciavel: false, atribuicoes: 0,
    },
  ],
  permissoes: [
    {
      codigo: 'contacts.read',
      delegavelNoTenant: true, delegavelNaEquipe: true, delegavelProprio: true,
    },
    {
      codigo: 'audit.read',
      delegavelNoTenant: false, delegavelNaEquipe: false, delegavelProprio: false,
    },
  ],
} as const;

const MEMBROS = [
  {
    membershipId: 'vinculo-1', usuarioId: 'usuario-1', login: 'gestor', nome: 'Ana Gestora',
    atribuicoes: [{
      id: 'atrib-1', papelId: 'papel-sdr', papelCodigo: 'SDR',
      papelNome: 'SDR', alcance: 'TEAM' as const,
    }],
  },
  {
    membershipId: 'vinculo-2', usuarioId: 'usuario-2', login: 'vendedor', nome: 'Bruno Vendedor',
    atribuicoes: [],
  },
] as const;

describe('TeamsPage', () => {
  beforeEach(() => {
    vi.mocked(organizacaoApi.catalogo).mockResolvedValue(structuredClone(CATALOGO));
    vi.mocked(organizacaoApi.membros).mockResolvedValue(structuredClone(MEMBROS) as never);
    vi.mocked(organizacaoApi.equipes).mockResolvedValue([]);
  });

  it('avisa que a revogação não derruba a sessão em curso', async () => {
    // Prometer efeito imediato e entregar 15 minutos é pior que avisar: quem
    // administra precisa saber antes de agir num incidente.
    renderComEstadoServidor(<TeamsPage />);

    expect(await screen.findByText(/até 15 minutos/i)).toBeInTheDocument();
  });

  it('não oferece edição de papel de sistema nem de papel acima do privilégio', async () => {
    renderComEstadoServidor(<TeamsPage />);

    await screen.findByRole('heading', { name: 'SDR' });
    const botoes = screen.getAllByRole('button', { name: /^editar$/i });

    // Três papéis, três botões: nenhum some da tela — esconder faria a lista
    // mentir sobre a configuração da empresa. Só um está habilitado.
    expect(botoes).toHaveLength(3);
    expect(botoes.filter((botao) => !botao.hasAttribute('disabled'))).toHaveLength(1);
  });

  it('não deixa remover papel que alguém está usando', async () => {
    renderComEstadoServidor(<TeamsPage />);

    await screen.findByText('Proprietário');
    const [doSdr, doOwner, doAuditor] = screen.getAllByRole('button', { name: /^remover$/i });

    // SDR: sem ninguém usando e dentro do privilégio — some sem estrago.
    expect(doSdr).not.toBeDisabled();
    // OWNER: papel de sistema e com uma pessoa atribuída. Apagar revogaria o
    // acesso de quem está trabalhando, sem aviso.
    expect(doOwner).toBeDisabled();
    // AUDITOR: ninguém usa, mas concede um acesso que o usuário não possui.
    expect(doAuditor).toBeDisabled();
  });

  it('desabilita a permissão que o usuário não pode conceder', async () => {
    renderComEstadoServidor(<TeamsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /novo papel/i }));

    expect(screen.getByLabelText(/ver contatos/i)).not.toBeDisabled();
    // A tela é conveniência; o backend recusaria igual. Mas oferecer para
    // depois negar transforma administração de rotina em tentativa e erro.
    expect(screen.getByLabelText(/trilha de auditoria/i)).toBeDisabled();
  });

  it('o código do papel não muda depois de criado', async () => {
    renderComEstadoServidor(<TeamsPage />);

    await screen.findByRole('heading', { name: 'SDR' });
    fireEvent.click(screen.getAllByRole('button', { name: /^editar$/i })[0]);

    expect(screen.getByLabelText('Código')).toBeDisabled();
    expect(screen.getByLabelText('Código')).toHaveValue('SDR');
  });

  it('atribui papel com o alcance escolhido, não com um padrão implícito', async () => {
    vi.mocked(organizacaoApi.atribuirPapel).mockResolvedValue(undefined);
    renderComEstadoServidor(<TeamsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /pessoas/i }));
    await screen.findByText('Bruno Vendedor');

    const alcances = screen.getAllByLabelText('Alcance da atribuição');
    fireEvent.change(alcances[1], { target: { value: 'TENANT' } });
    fireEvent.click(screen.getAllByRole('button', { name: /^atribuir$/i })[1]);

    await waitFor(() => expect(organizacaoApi.atribuirPapel).toHaveBeenCalledOnce());
    expect(vi.mocked(organizacaoApi.atribuirPapel).mock.calls[0])
      .toEqual(['vinculo-2', 'papel-sdr', 'TENANT']);
  });

  it('não oferece alcance para papel que o usuário não pode atribuir', async () => {
    // AUDITOR concede audit.read, que este usuário não possui em alcance
    // nenhum. Oferecer o seletor e deixar o servidor devolver 422 é o mesmo
    // defeito de "oferece e nega" que a tela existe para evitar.
    renderComEstadoServidor(<TeamsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /pessoas/i }));
    await screen.findByText('Bruno Vendedor');

    fireEvent.change(screen.getAllByLabelText('Papel a atribuir')[1], {
      target: { value: 'papel-auditor' },
    });

    expect(screen.getAllByLabelText('Alcance da atribuição')[1]).toBeDisabled();
    expect(screen.getAllByRole('button', { name: /^atribuir$/i })[1]).toBeDisabled();
    expect(screen.getAllByText(/não possui/i).length).toBeGreaterThan(0);
  });

  it('avisa quando há papel de equipe sem composição montada', async () => {
    renderComEstadoServidor(<TeamsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /equipes/i }));

    // Papel de equipe sem equipe enxerga só o próprio responsável: correto e
    // inútil, e ninguém descobre isso sozinho.
    expect(await screen.findByText(/Nenhuma equipe montada/i)).toBeInTheDocument();
  });

  it('não oferece a própria pessoa como liderada dela mesma', async () => {
    renderComEstadoServidor(<TeamsPage />);

    fireEvent.click(await screen.findByRole('button', { name: /equipes/i }));
    const liderado = await screen.findByLabelText(/responde ao gestor/i);

    // O gestor selecionado é o primeiro da lista; ele não pode aparecer como
    // liderado de si mesmo.
    const opcoes = Array.from(liderado.querySelectorAll('option')).map((o) => o.textContent);
    expect(opcoes).not.toContain('Ana Gestora');
    expect(opcoes).toContain('Bruno Vendedor');
  });

  it('explica a ausência de acesso em vez de mostrar tela vazia', async () => {
    vi.mocked(organizacaoApi.catalogo).mockRejectedValue(
      Object.assign(new Error('forbidden'), { status: 403 }),
    );
    renderComEstadoServidor(<TeamsPage />);

    expect(await screen.findByText(/não administra acessos/i)).toBeInTheDocument();
  });
});
