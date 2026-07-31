import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { contatosApi } from '@/shared/crm/api';
import type { Contato } from '@/shared/crm/tipos';
import { formatarData } from '@/shared/formato';
import { Carregando, Pagina, Vazio } from '@/shared/components/Pagina';

export function ContactsPage() {
  const [contatos, setContatos] = useState<readonly Contato[]>([]);
  const [busca, setBusca] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async (termo: string) => {
    setCarregando(true);
    try {
      setContatos(await contatosApi.listar(termo || undefined));
    } finally {
      setCarregando(false);
    }
  }, []);

  // Debounce da busca: sem ele cada tecla dispara uma requisição, e as
  // respostas podem chegar fora de ordem — a lista passa a exibir o resultado
  // de um termo que o usuário já apagou.
  useEffect(() => {
    const temporizador = setTimeout(() => void carregar(busca.trim()), 300);
    return () => clearTimeout(temporizador);
  }, [busca, carregar]);

  async function salvar(dados: NovoContato) {
    setErro(null);
    try {
      await contatosApi.criar(dados);
      setFormAberto(false);
      await carregar(busca.trim());
    } catch {
      // Mensagem genérica; o detalhe fica no log com id de correlação. O caso
      // comum é e-mail já usado por outro contato do mesmo tenant.
      setErro('Não foi possível salvar. Verifique se o e-mail já está em uso.');
    }
  }

  async function excluir(contato: Contato) {
    if (!window.confirm(`Excluir o contato "${contato.nome}"?`)) return;
    await contatosApi.excluir(contato.id);
    await carregar(busca.trim());
  }

  return (
    <Pagina
      titulo="Contatos"
      descricao={`${contatos.length} ${contatos.length === 1 ? 'registro' : 'registros'}`}
      acoes={
        <Button onClick={() => setFormAberto((aberto) => !aberto)}>
          {formAberto ? 'Cancelar' : 'Novo contato'}
        </Button>
      }
    >
      <div className="space-y-4">
        <div className="max-w-sm">
          <Label htmlFor="busca" className="sr-only">
            Buscar contatos
          </Label>
          <Input
            id="busca"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por nome, e-mail ou empresa…"
            autoComplete="off"
          />
        </div>

        {erro && <AlertaErro>{erro}</AlertaErro>}
        {formAberto && <FormularioDeContato aoSalvar={salvar} />}

        {carregando ? (
          <Carregando />
        ) : contatos.length === 0 ? (
          <Vazio
            titulo={busca ? 'Nenhum contato encontrado' : 'Nenhum contato ainda'}
            descricao={
              busca
                ? 'Tente outro termo de busca.'
                : 'Cadastre o primeiro contato, ou aguarde a primeira conversa por um canal conectado.'
            }
          />
        ) : (
          <TabelaDeContatos contatos={contatos} aoExcluir={excluir} />
        )}
      </div>
    </Pagina>
  );
}

interface NovoContato {
  nome: string;
  email?: string;
  telefone?: string;
  empresa?: string;
}

function FormularioDeContato({
  aoSalvar,
}: {
  readonly aoSalvar: (dados: NovoContato) => Promise<void>;
}) {
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [telefone, setTelefone] = useState('');
  const [empresa, setEmpresa] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setEnviando(true);
    try {
      await aoSalvar({
        nome: nome.trim(),
        email: email.trim() || undefined,
        telefone: telefone.trim() || undefined,
        empresa: empresa.trim() || undefined,
      });
      setNome('');
      setEmail('');
      setTelefone('');
      setEmpresa('');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form
      onSubmit={enviar}
      className="grid gap-3 rounded-[var(--radius-surface)] border p-4 sm:grid-cols-2"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <Campo id="nome" rotulo="Nome" valor={nome} aoMudar={setNome} obrigatorio />
      <Campo id="email" rotulo="E-mail" valor={email} aoMudar={setEmail} tipo="email" />
      <Campo id="telefone" rotulo="Telefone" valor={telefone} aoMudar={setTelefone} />
      <Campo id="empresa" rotulo="Empresa" valor={empresa} aoMudar={setEmpresa} />
      <div className="sm:col-span-2">
        <Button type="submit" disabled={enviando || nome.trim().length === 0}>
          {enviando ? 'Salvando…' : 'Salvar contato'}
        </Button>
      </div>
    </form>
  );
}

function Campo({
  id,
  rotulo,
  valor,
  aoMudar,
  tipo = 'text',
  obrigatorio = false,
}: {
  readonly id: string;
  readonly rotulo: string;
  readonly valor: string;
  readonly aoMudar: (valor: string) => void;
  readonly tipo?: string;
  readonly obrigatorio?: boolean;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{rotulo}</Label>
      <Input
        id={id}
        type={tipo}
        value={valor}
        onChange={(e) => aoMudar(e.target.value)}
        required={obrigatorio}
        autoComplete="off"
      />
    </div>
  );
}

function TabelaDeContatos({
  contatos,
  aoExcluir,
}: {
  readonly contatos: readonly Contato[];
  readonly aoExcluir: (contato: Contato) => void;
}) {
  return (
    // A tabela rola dentro do próprio contêiner. Sem isso, uma coluna larga
    // empurra a página inteira e a navegação sai da tela no mobile.
    <div
      className="overflow-x-auto rounded-[var(--radius-surface)] border"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b" style={{ borderColor: 'var(--border-subtle)' }}>
            <Th>Nome</Th>
            <Th>E-mail</Th>
            <Th>Telefone</Th>
            <Th>Empresa</Th>
            <Th>Criado em</Th>
            <Th>Ações</Th>
          </tr>
        </thead>
        <tbody>
          {contatos.map((contato) => (
            <tr
              key={contato.id}
              className="border-b last:border-0"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              <Td>
                <span className="font-medium">{contato.nome}</span>
              </Td>
              <Td>{contato.email ?? '—'}</Td>
              <Td>{contato.telefone ?? '—'}</Td>
              <Td>{contato.empresa ?? '—'}</Td>
              <Td>{formatarData(contato.criadoEm)}</Td>
              <Td>
                <Button variant="fantasma" size="pequeno" onClick={() => aoExcluir(contato)}>
                  Excluir
                </Button>
              </Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Th({ children }: { readonly children: ReactNode }) {
  return (
    <th
      className="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wide"
      style={{ color: 'var(--text-muted)' }}
    >
      {children}
    </th>
  );
}

function Td({ children }: { readonly children: ReactNode }) {
  return <td className="px-4 py-2.5">{children}</td>;
}
