import { createContext, use, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { definirAccessToken, tentarRenovarSessao } from '@/lib/api';
import { authApi, type Usuario } from './api';

export type { Usuario } from './api';

interface AuthContextValue {
  readonly usuario: Usuario | null;
  readonly carregando: boolean;
  readonly entrar: (empresa: string, login: string, senha: string) => Promise<void>;
  readonly sair: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    let cancelado = false;
    (async () => {
      try {
        const renovou = await tentarRenovarSessao();
        if (!renovou) return;
        const encontrado = await authApi.usuarioAtual();
        if (!cancelado) setUsuario(encontrado);
      } catch {
        // Sessão ausente ou expirada é o estado normal antes do login.
      } finally {
        if (!cancelado) setCarregando(false);
      }
    })();
    return () => { cancelado = true; };
  }, []);

  const entrar = useCallback(async (empresa: string, login: string, senha: string) => {
    const sessao = await authApi.entrar(empresa, login, senha);
    definirAccessToken(sessao.accessToken);
    setUsuario(sessao.usuario);
  }, []);

  const sair = useCallback(async () => {
    try {
      await authApi.sair();
    } finally {
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
  if (!contexto) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  return contexto;
}
