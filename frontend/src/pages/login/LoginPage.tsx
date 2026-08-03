import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/shared/auth/AuthContext';

/**
 * Mensagem única para toda falha de autenticação.
 *
 * O backend já responde igual para empresa inexistente, login inexistente e
 * senha errada. Repetir esse cuidado aqui evita que a tela reintroduza a
 * distinção que o servidor tomou o trabalho de apagar — por exemplo tratando
 * um 404 de forma diferente de um 401.
 */
const MENSAGEM_FALHA = 'Empresa, login ou senha inválidos.';

interface EstadoDeOrigem {
  readonly de?: string;
}

export function LoginPage() {
  const { usuario, entrar } = useAuth();
  const navegar = useNavigate();
  const localizacao = useLocation();

  const [empresa, setEmpresa] = useState('');
  const [login, setLogin] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const destino = (localizacao.state as EstadoDeOrigem | null)?.de ?? '/dashboard';

  if (usuario) {
    return <Navigate to={destino} replace />;
  }

  async function aoEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await entrar(empresa.trim(), login.trim(), senha);
      navegar(destino, { replace: true });
    } catch {
      // O detalhe fica no log do servidor, com id de correlação. Aqui não há
      // o que diferenciar: credencial errada, conta bloqueada e empresa
      // inexistente precisam ser indistinguíveis para quem está do lado de
      // fora tentando descobrir o que existe.
      setErro(MENSAGEM_FALHA);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="flex min-h-dvh">
      {/* Painel de marca. Escondido no mobile: ocupa a tela toda sem
          informar nada, e empurra o formulário para fora da dobra. */}
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
          <h1 className="text-2xl font-semibold tracking-tight">Entrar</h1>
          <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
            Informe os dados de acesso da sua empresa.
          </p>

          <form className="mt-8 space-y-4" onSubmit={aoEnviar} noValidate>
            {erro && <AlertaErro id="erro-login">{erro}</AlertaErro>}

            <div className="space-y-1.5">
              <Label htmlFor="empresa">Empresa</Label>
              <Input
                id="empresa"
                name="empresa"
                value={empresa}
                onChange={(e) => setEmpresa(e.target.value)}
                autoComplete="organization"
                autoCapitalize="none"
                spellCheck={false}
                required
                aria-invalid={erro !== null}
                aria-describedby={erro ? 'erro-login' : undefined}
                disabled={enviando}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="login">Login</Label>
              <Input
                id="login"
                name="username"
                value={login}
                onChange={(e) => setLogin(e.target.value)}
                // `username` e `current-password` são o que faz o gerenciador
                // de senhas do navegador reconhecer e salvar as credenciais.
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                required
                aria-invalid={erro !== null}
                aria-describedby={erro ? 'erro-login' : undefined}
                disabled={enviando}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="senha">Senha</Label>
              <Input
                id="senha"
                name="password"
                type="password"
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
                autoComplete="current-password"
                required
                aria-invalid={erro !== null}
                aria-describedby={erro ? 'erro-login' : undefined}
                disabled={enviando}
              />
            </div>

            <Button type="submit" className="w-full" disabled={enviando}>
              {enviando ? 'Entrando…' : 'Entrar'}
            </Button>
          </form>
        </div>
      </main>
    </div>
  );
}
