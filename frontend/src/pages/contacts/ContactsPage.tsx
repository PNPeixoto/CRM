import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ApiError } from '@/lib/api';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Contato } from '@/shared/crm/tipos';
import { formatarData } from '@/shared/formato';
import { focarPrimeiroCampoInvalido, mapearFalhaDeFormulario } from '@/shared/forms/erros';
import { Carregando, Pagina, Vazio } from '@/shared/components/Pagina';
import {
  useContatos,
  useCriarContato,
  useExcluirContato,
} from '@/shared/server-state/recursos';

export function ContactsPage() {
  const [parametros, setParametros] = useSearchParams();
  const buscaInicial = parametros.get('busca') ?? '';
  const paginaInformada = Number.parseInt(parametros.get('pagina') ?? '0', 10);
  const pagina = Number.isInteger(paginaInformada) && paginaInformada >= 0 ? paginaInformada : 0;
  const tamanhoDaPagina = 20;
  const [busca, setBusca] = useState(buscaInicial);
  const [buscaEstavel, setBuscaEstavel] = useState(buscaInicial.trim());
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const contatosQuery = useContatos(buscaEstavel, pagina, tamanhoDaPagina);
  const criarContato = useCriarContato();
  const excluirContato = useExcluirContato();
  const contatos = contatosQuery.data ?? [];
  const erroDaLista = contatosQuery.error instanceof ApiError
    && contatosQuery.error.kind === 'forbidden'
    ? 'Você não tem permissão para ver os contatos desta empresa.'
    : 'Não foi possível carregar os contatos. Tente novamente.';

  // Debounce da busca: sem ele cada tecla dispara uma requisição, e as
  // respostas podem chegar fora de ordem — a lista passa a exibir o resultado
  // de um termo que o usuário já apagou.
  useEffect(() => {
    const temporizador = setTimeout(() => {
      const termo = busca.trim();
      setBuscaEstavel(termo);
      setParametros((atuais) => {
        const proximos = new URLSearchParams(atuais);
        if (termo) proximos.set('busca', termo);
        else proximos.delete('busca');
        if ((atuais.get('busca') ?? '') !== termo) proximos.delete('pagina');
        return proximos;
      }, { replace: true });
    }, 300);
    return () => clearTimeout(temporizador);
  }, [busca, setParametros]);

  function irParaPagina(proxima: number) {
    setParametros((atuais) => {
      const novos = new URLSearchParams(atuais);
      if (proxima <= 0) novos.delete('pagina');
      else novos.set('pagina', String(proxima));
      return novos;
    });
  }

  async function salvar(dados: NovoContato) {
    setErro(null);
    await criarContato.mutateAsync(dados);
    setFormAberto(false);
  }

  async function excluir(contato: Contato) {
    if (!window.confirm(`Excluir o contato "${contato.nome}"?`)) return;
    try {
      setErro(null);
      await excluirContato.mutateAsync(contato.id);
    } catch {
      setErro('Não foi possível excluir o contato.');
    }
  }

  return (
    <Pagina
      titulo="Contatos"
      descricao={`Página ${pagina + 1} · ${contatos.length} ${contatos.length === 1 ? 'registro' : 'registros'}`}
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
            type="search"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por nome, e-mail ou empresa…"
            autoComplete="off"
          />
        </div>

        {(erro || contatosQuery.isError) && (
          <AlertaErro>{erro ?? erroDaLista}</AlertaErro>
        )}
        {formAberto && <FormularioDeContato aoSalvar={salvar} />}

        {contatosQuery.isPending ? (
          <Carregando />
        ) : contatos.length === 0 ? (
          <Vazio
            tipo={busca ? 'sem-resultados' : 'vazio-inicial'}
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

        {(pagina > 0 || contatos.length === tamanhoDaPagina) && (
          <nav className="flex items-center gap-2" aria-label="Paginação de contatos">
            <Button
              variant="secundario"
              onClick={() => irParaPagina(pagina - 1)}
              disabled={pagina === 0 || contatosQuery.isFetching}
            >
              Página anterior
            </Button>
            <span className="text-sm" style={{ color: 'var(--text-muted)' }}>
              Página {pagina + 1}
            </span>
            <Button
              variant="secundario"
              onClick={() => irParaPagina(pagina + 1)}
              disabled={contatos.length < tamanhoDaPagina || contatosQuery.isFetching}
            >
              Próxima página
            </Button>
          </nav>
        )}
      </div>
    </Pagina>
  );
}

export interface NovoContato {
  nome: string;
  email?: string;
  telefone?: string;
  empresa?: string;
}

const CAMPOS_CONTATO = ['nome', 'email', 'telefone', 'empresa'] as const;
type CampoContato = typeof CAMPOS_CONTATO[number];
type ErrosContato = Partial<Record<CampoContato, string>>;

function validarNovoContato(dados: Record<CampoContato, string>): ErrosContato {
  const erros: ErrosContato = {};
  if (!dados.nome.trim()) erros.nome = 'Informe o nome do contato.';
  else if (dados.nome.trim().length > 200) erros.nome = 'Use no máximo 200 caracteres.';
  if (dados.email.trim().length > 200) erros.email = 'Use no máximo 200 caracteres.';
  else if (dados.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(dados.email.trim())) {
    erros.email = 'Informe um e-mail válido, como nome@empresa.com.';
  }
  if (dados.telefone.trim().length > 40) erros.telefone = 'Use no máximo 40 caracteres.';
  if (dados.empresa.trim().length > 200) erros.empresa = 'Use no máximo 200 caracteres.';
  return erros;
}

export function FormularioDeContato({
  aoSalvar,
}: {
  readonly aoSalvar: (dados: NovoContato) => Promise<void>;
}) {
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [telefone, setTelefone] = useState('');
  const [empresa, setEmpresa] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erros, setErros] = useState<ErrosContato>({});
  const [mensagem, setMensagem] = useState<string | null>(null);
  const formularioRef = useRef<HTMLFormElement>(null);
  const envioEmAndamento = useRef(false);

  useEffect(() => {
    if (formularioRef.current) {
      focarPrimeiroCampoInvalido(formularioRef.current, CAMPOS_CONTATO, erros);
    }
  }, [erros]);

  function mudar(campo: CampoContato, valor: string) {
    if (campo === 'nome') setNome(valor);
    else if (campo === 'email') setEmail(valor);
    else if (campo === 'telefone') setTelefone(valor);
    else setEmpresa(valor);
    if (erros[campo]) setErros((atuais) => ({ ...atuais, [campo]: undefined }));
  }

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (envioEmAndamento.current) return;

    const valores = { nome, email, telefone, empresa };
    const errosLocais = validarNovoContato(valores);
    if (Object.keys(errosLocais).length > 0) {
      setErros(errosLocais);
      setMensagem('Revise os campos destacados e tente novamente.');
      return;
    }

    envioEmAndamento.current = true;
    setEnviando(true);
    setErros({});
    setMensagem(null);
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
    } catch (falha) {
      const mapeada = mapearFalhaDeFormulario(falha, CAMPOS_CONTATO);
      setErros(mapeada.campos);
      setMensagem(mapeada.mensagem);
    } finally {
      envioEmAndamento.current = false;
      setEnviando(false);
    }
  }

  return (
    <form
      ref={formularioRef}
      onSubmit={enviar}
      noValidate
      className="grid gap-3 rounded-[var(--radius-surface)] border p-4 sm:grid-cols-2"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      {mensagem && <AlertaErro className="sm:col-span-2">{mensagem}</AlertaErro>}
      <Campo campo="nome" rotulo="Nome" valor={nome} aoMudar={mudar} erro={erros.nome} />
      <Campo campo="email" rotulo="E-mail" valor={email} aoMudar={mudar} tipo="email" erro={erros.email} />
      <Campo campo="telefone" rotulo="Telefone" valor={telefone} aoMudar={mudar} erro={erros.telefone} />
      <Campo campo="empresa" rotulo="Empresa" valor={empresa} aoMudar={mudar} erro={erros.empresa} />
      <div className="sm:col-span-2">
        <Button type="submit" disabled={enviando}>
          {enviando ? 'Salvando…' : 'Salvar contato'}
        </Button>
      </div>
    </form>
  );
}

function Campo({
  campo,
  rotulo,
  valor,
  aoMudar,
  tipo = 'text',
  erro,
}: {
  readonly campo: CampoContato;
  readonly rotulo: string;
  readonly valor: string;
  readonly aoMudar: (campo: CampoContato, valor: string) => void;
  readonly tipo?: string;
  readonly erro?: string;
}) {
  const id = `contato-${campo}`;
  const erroId = `${id}-erro`;
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{rotulo}</Label>
      <Input
        id={id}
        name={campo}
        type={tipo}
        value={valor}
        onChange={(e) => aoMudar(campo, e.target.value)}
        aria-invalid={erro ? 'true' : undefined}
        aria-describedby={erro ? erroId : undefined}
        maxLength={campo === 'telefone' ? 40 : 200}
        autoComplete="off"
      />
      {erro && (
        <p id={erroId} className="text-sm" style={{ color: 'var(--danger)' }}>
          {erro}
        </p>
      )}
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
            <Th>Responsável</Th>
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
              <Td>{formatarResponsavel(contato)}</Td>
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
