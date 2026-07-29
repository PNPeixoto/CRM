import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, definirAccessToken, tentarRenovarSessao } from '@/lib/api';

export interface Usuario {
  readonly id: string;
  readonly login: string;
  readonly nomeCompleto: string;
}

interface SessaoResponse {
  readonly accessToken: string;
  readonly expiraEmSegundos: number;
  readonly usuario: Usuario;
}

interface AuthContextValue {
  readonly usuario: Usuario | null;
  /** Indefinido enquanto a sessão inicial não foi verificada com o servidor. */
  readonly carregando: boolean;
  readonly entrar: (empresa: string, login: string, senha: string) => Promise<void>;
  readonly sair: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);

  /**
   * Ao abrir a aplicação, o access token não existe — ele vive só em memória e
   * a memória morreu junto com a aba anterior. O que sobrevive é o cookie de
   * refresh, HttpOnly, que o JavaScript não consegue ler mas o navegador
   * envia. Então a recuperação de sessão é: pedir um access token novo e, se
   * vier, perguntar ao servidor quem somos.
   *
   * Perguntar ao servidor é o ponto. Guardar o usuário em localStorage
   * mostraria a interface de alguém que talvez já não tenha acesso — a
   * fronteira é o backend, e ele é quem responde.
   */
  useEffect(() => {
    let cancelado = false;

    (async () => {
      try {
        const renovou = await tentarRenovarSessao();
        if (!renovou) return;
        const encontrado = await api.get<Usuario>('/auth/me');
        if (!cancelado) setUsuario(encontrado);
      } catch {
        // Sessão ausente ou expirada não é erro a exibir: é o estado normal
        // de quem ainda não entrou.
      } finally {
        if (!cancelado) setCarregando(false);
      }
    })();

    return () => {
      cancelado = true;
    };
  }, []);

  const entrar = useCallback(async (empresa: string, login: string, senha: string) => {
    const sessao = await api.post<SessaoResponse>('/auth/login', { empresa, login, senha });
    definirAccessToken(sessao.accessToken);
    setUsuario(sessao.usuario);
  }, []);

  const sair = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } finally {
      // Limpa o estado local mesmo se a chamada falhar. Deixar a interface
      // logada porque o servidor não respondeu seria pior: o usuário pediu
      // para sair, e do lado dele a sessão precisa acabar.
      definirAccessToken(null);
      setUsuario(null);
    }
  }, []);

  const valor = useMemo(
    () => ({ usuario, carregando, entrar, sair }),
    [usuario, carregando, entrar, sair],
  );

  return <AuthContext value={valor}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const contexto = use(AuthContext);
  if (!contexto) {
    throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  }
  return contexto;
}
