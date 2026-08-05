import { CheckCircle2, KeyRound } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/shared/auth/AuthContext';
import type { CadastroMfa } from '@/shared/auth/api';
import { destinoInternoSeguro } from '@/shared/auth/destinoSeguro';

const MENSAGEM_FALHA = 'Empresa, login ou senha inválidos.';

const MENSAGEM_POR_CODIGO: Readonly<Record<string, string>> = {
  MFA_NECESSARIO: 'Informe o código do autenticador ou um código de recuperação.',
  MFA_INVALIDO: 'Código de verificação inválido, expirado ou já utilizado.',
  MFA_CADASTRO_INVALIDO: 'O cadastro do segundo fator expirou. Comece de novo.',
};

type Etapa = 'credenciais' | 'codigo' | 'cadastro' | 'recuperacao';

interface EstadoDeOrigem {
  readonly de?: string;
}

function mensagemDaFalha(falha: unknown): string {
  const codigo = falha instanceof ApiError ? falha.codigo : null;
  return (codigo && MENSAGEM_POR_CODIGO[codigo]) ?? MENSAGEM_FALHA;
}

export function LoginPage() {
  const { usuario, entrar, iniciarCadastroMfa, ativarMfa } = useAuth();
  const navegar = useNavigate();
  const localizacao = useLocation();

  const [empresa, setEmpresa] = useState('');
  const [login, setLogin] = useState('');
  const [senha, setSenha] = useState('');
  const [codigo, setCodigo] = useState('');
  const [etapa, setEtapa] = useState<Etapa>('credenciais');
  const [cadastroMfa, setCadastroMfa] = useState<CadastroMfa | null>(null);
  const [codigosRecuperacao, setCodigosRecuperacao] = useState<readonly string[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const destino = destinoInternoSeguro((localizacao.state as EstadoDeOrigem | null)?.de);

  // Após ativar o MFA, a sessão já existe, mas os códigos de recuperação ainda
  // precisam ser exibidos uma única vez. Só redirecionamos depois da confirmação.
  if (usuario && etapa !== 'recuperacao') {
    return <Navigate to={destino} replace />;
  }

  async function aoEnviarCredenciais(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);

    const empresaNormalizada = empresa.trim();
    const loginNormalizado = login.trim();
    try {
      await entrar(
        empresaNormalizada,
        loginNormalizado,
        senha,
        etapa === 'codigo' ? codigo.trim() : undefined,
      );
      navegar(destino, { replace: true });
    } catch (falha) {
      const codigoDaFalha = falha instanceof ApiError ? falha.codigo : null;
      if (codigoDaFalha === 'MFA_CADASTRO_NECESSARIO') {
        try {
          // Este endpoint repete a prova da senha e só então entrega o segredo.
          // O segredo permanece apenas no estado desta página: nunca vai para
          // storage, URL, histórico, telemetria ou log do navegador.
          const cadastro = await iniciarCadastroMfa(
            empresaNormalizada,
            loginNormalizado,
            senha,
          );
          setCadastroMfa(cadastro);
          setCodigo('');
          setEtapa('cadastro');
        } catch (falhaNoCadastro) {
          setErro(mensagemDaFalha(falhaNoCadastro));
        }
      } else {
        if (codigoDaFalha === 'MFA_NECESSARIO') {
          setCodigo('');
          setEtapa('codigo');
        }
        setErro(mensagemDaFalha(falha));
      }
    } finally {
      setEnviando(false);
    }
  }

  async function aoAtivarMfa(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!cadastroMfa) return;

    setErro(null);
    setEnviando(true);
    try {
      const recuperacao = await ativarMfa(cadastroMfa.desafio, codigo.trim());
      // Remove credenciais e o segredo TOTP da memória da página assim que o
      // servidor confirma a ativação. Restam somente os códigos que o usuário
      // ainda precisa guardar antes de continuar.
      setSenha('');
      setCodigo('');
      setCadastroMfa(null);
      setCodigosRecuperacao(recuperacao);
      setEtapa('recuperacao');
    } catch (falha) {
      setErro(mensagemDaFalha(falha));
    } finally {
      setEnviando(false);
    }
  }

  function voltarParaCredenciais() {
    setSenha('');
    setCodigo('');
    setCadastroMfa(null);
    setErro(null);
    setEtapa('credenciais');
  }

  return (
    <div className="flex min-h-dvh">
      <aside
        className="hidden w-2/5 flex-col justify-between p-10 lg:flex"
        style={{ backgroundColor: 'var(--surface-shell)', color: 'var(--text-on-shell)' }}
      >
        <span className="text-lg font-semibold tracking-tight">CRM PNP</span>
        <p className="max-w-sm text-sm" style={{ color: 'var(--text-on-shell-muted)' }}>
          Atendimento de WhatsApp, Instagram, Telegram e chat ao vivo em uma única
          caixa de entrada.
        </p>
        <span className="text-xs" style={{ color: 'var(--text-on-shell-muted)' }}>
          Todos os direitos reservados
        </span>
      </aside>

      <main className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          {etapa === 'recuperacao' ? (
            <CodigosDeRecuperacao
              codigos={codigosRecuperacao}
              aoConcluir={() => navegar(destino, { replace: true })}
            />
          ) : etapa === 'cadastro' && cadastroMfa ? (
            <CadastroDoSegundoFator
              cadastro={cadastroMfa}
              codigo={codigo}
              erro={erro}
              enviando={enviando}
              aoMudarCodigo={setCodigo}
              aoEnviar={aoAtivarMfa}
              aoVoltar={voltarParaCredenciais}
            />
          ) : etapa === 'codigo' ? (
            <CodigoDoSegundoFator
              codigo={codigo}
              erro={erro}
              enviando={enviando}
              aoMudarCodigo={setCodigo}
              aoEnviar={aoEnviarCredenciais}
              aoVoltar={voltarParaCredenciais}
            />
          ) : (
            <Credenciais
              empresa={empresa}
              login={login}
              senha={senha}
              erro={erro}
              enviando={enviando}
              aoMudarEmpresa={setEmpresa}
              aoMudarLogin={setLogin}
              aoMudarSenha={setSenha}
              aoEnviar={aoEnviarCredenciais}
            />
          )}
        </div>
      </main>
    </div>
  );
}

interface CredenciaisProps {
  readonly empresa: string;
  readonly login: string;
  readonly senha: string;
  readonly erro: string | null;
  readonly enviando: boolean;
  readonly aoMudarEmpresa: (valor: string) => void;
  readonly aoMudarLogin: (valor: string) => void;
  readonly aoMudarSenha: (valor: string) => void;
  readonly aoEnviar: (evento: FormEvent<HTMLFormElement>) => void;
}

function Credenciais(props: CredenciaisProps) {
  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Entrar</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
        Informe os dados de acesso da sua empresa.
      </p>

      <form className="mt-8 space-y-4" onSubmit={props.aoEnviar} noValidate>
        {props.erro && <AlertaErro id="erro-login">{props.erro}</AlertaErro>}

        <div className="space-y-1.5">
          <Label htmlFor="empresa">Empresa</Label>
          <Input
            id="empresa"
            name="empresa"
            value={props.empresa}
            onChange={(evento) => props.aoMudarEmpresa(evento.target.value)}
            autoComplete="organization"
            autoCapitalize="none"
            spellCheck={false}
            required
            aria-invalid={props.erro !== null}
            aria-describedby={props.erro ? 'erro-login' : undefined}
            disabled={props.enviando}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="login">Login</Label>
          <Input
            id="login"
            name="username"
            value={props.login}
            onChange={(evento) => props.aoMudarLogin(evento.target.value)}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            required
            aria-invalid={props.erro !== null}
            aria-describedby={props.erro ? 'erro-login' : undefined}
            disabled={props.enviando}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="senha">Senha</Label>
          <Input
            id="senha"
            name="password"
            type="password"
            value={props.senha}
            onChange={(evento) => props.aoMudarSenha(evento.target.value)}
            autoComplete="current-password"
            required
            aria-invalid={props.erro !== null}
            aria-describedby={props.erro ? 'erro-login' : undefined}
            disabled={props.enviando}
          />
        </div>

        <Button type="submit" className="w-full" disabled={props.enviando}>
          {props.enviando ? 'Entrando…' : 'Entrar'}
        </Button>
      </form>
    </>
  );
}

interface CodigoProps {
  readonly codigo: string;
  readonly erro: string | null;
  readonly enviando: boolean;
  readonly aoMudarCodigo: (valor: string) => void;
  readonly aoEnviar: (evento: FormEvent<HTMLFormElement>) => void;
  readonly aoVoltar: () => void;
}

function CodigoDoSegundoFator(props: CodigoProps) {
  return (
    <>
      <KeyRound className="mb-4 size-8 text-[var(--brand)]" aria-hidden="true" />
      <h1 className="text-2xl font-semibold tracking-tight">Verificação em duas etapas</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
        Informe o código do aplicativo autenticador ou um código de recuperação.
      </p>

      <form className="mt-8 space-y-4" onSubmit={props.aoEnviar} noValidate>
        {props.erro && <AlertaErro id="erro-mfa">{props.erro}</AlertaErro>}
        <div className="space-y-1.5">
          <Label htmlFor="codigo-mfa">Código de verificação</Label>
          <Input
            id="codigo-mfa"
            name="one-time-code"
            value={props.codigo}
            onChange={(evento) => props.aoMudarCodigo(evento.target.value)}
            autoComplete="one-time-code"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={64}
            required
            autoFocus
            aria-invalid={props.erro !== null}
            aria-describedby={props.erro ? 'erro-mfa' : undefined}
            disabled={props.enviando}
          />
        </div>
        <Button type="submit" className="w-full" disabled={props.enviando}>
          {props.enviando ? 'Verificando…' : 'Verificar e entrar'}
        </Button>
        <Button
          type="button"
          variant="fantasma"
          className="w-full"
          onClick={props.aoVoltar}
          disabled={props.enviando}
        >
          Voltar
        </Button>
      </form>
    </>
  );
}

interface CadastroProps extends CodigoProps {
  readonly cadastro: CadastroMfa;
}

function CadastroDoSegundoFator(props: CadastroProps) {
  const minutos = Math.max(1, Math.ceil(props.cadastro.expiraEmSegundos / 60));
  return (
    <>
      <KeyRound className="mb-4 size-8 text-[var(--brand)]" aria-hidden="true" />
      <h1 className="text-2xl font-semibold tracking-tight">Proteja sua conta</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
        No aplicativo autenticador, adicione uma chave manual e informe o código gerado.
        Este cadastro expira em {minutos} minutos.
      </p>

      <div className="mt-6 rounded-[var(--radius-control)] border border-[var(--border-control)] bg-[var(--surface-sunken)] p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-[var(--text-muted)]">
          Chave de configuração
        </span>
        <code
          className="mt-2 block break-all font-mono text-sm font-semibold text-[var(--text-strong)]"
          aria-label="Chave de configuração do autenticador"
        >
          {props.cadastro.segredo}
        </code>
      </div>

      <form className="mt-6 space-y-4" onSubmit={props.aoEnviar} noValidate>
        {props.erro && <AlertaErro id="erro-ativacao-mfa">{props.erro}</AlertaErro>}
        <div className="space-y-1.5">
          <Label htmlFor="codigo-ativacao-mfa">Código de 6 dígitos</Label>
          <Input
            id="codigo-ativacao-mfa"
            name="one-time-code"
            value={props.codigo}
            onChange={(evento) => props.aoMudarCodigo(evento.target.value.replace(/\D/g, ''))}
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]{6}"
            minLength={6}
            maxLength={6}
            required
            autoFocus
            aria-invalid={props.erro !== null}
            aria-describedby={props.erro ? 'erro-ativacao-mfa' : undefined}
            disabled={props.enviando}
          />
        </div>
        <Button type="submit" className="w-full" disabled={props.enviando}>
          {props.enviando ? 'Ativando…' : 'Ativar e entrar'}
        </Button>
        <Button
          type="button"
          variant="fantasma"
          className="w-full"
          onClick={props.aoVoltar}
          disabled={props.enviando}
        >
          Voltar
        </Button>
      </form>
    </>
  );
}

function CodigosDeRecuperacao({
  codigos,
  aoConcluir,
}: {
  readonly codigos: readonly string[];
  readonly aoConcluir: () => void;
}) {
  return (
    <>
      <CheckCircle2 className="mb-4 size-8 text-[var(--success)]" aria-hidden="true" />
      <h1 className="text-2xl font-semibold tracking-tight">Verificação ativada</h1>
      <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
        Guarde estes códigos em local seguro. Cada código funciona uma única vez e não
        será mostrado novamente.
      </p>
      <ul
        className="mt-6 grid grid-cols-2 gap-2 rounded-[var(--radius-control)] border border-[var(--border-control)] bg-[var(--surface-sunken)] p-4"
        aria-label="Códigos de recuperação"
      >
        {codigos.map((item) => (
          <li key={item} className="font-mono text-sm text-[var(--text-strong)]">
            {item}
          </li>
        ))}
      </ul>
      <Button type="button" className="mt-6 w-full" onClick={aoConcluir}>
        Guardei os códigos — continuar
      </Button>
    </>
  );
}
