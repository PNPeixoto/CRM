import { createContext, use, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { definirAccessToken, tentarRenovarSessao } from '@/lib/api';
import { limparCachesDeServidor } from '@/shared/server-state/registro';
import { criarCanalDeSessao } from './canalDeSessao';
import { authApi, type CadastroMfa, type Sessao, type Usuario } from './api';

export type { Usuario } from './api';

interface AuthContextValue {
  readonly usuario: Usuario | null;
  readonly carregando: boolean;
  readonly entrar: (
    empresa: string,
    login: string,
    senha: string,
    codigoMfa?: string,
  ) => Promise<void>;
  readonly iniciarCadastroMfa: (
    empresa: string,
    login: string,
    senha: string,
    codigoMfa?: string,
  ) => Promise<CadastroMfa>;
  readonly ativarMfa: (desafio: string, codigo: string) => Promise<readonly string[]>;
  readonly sair: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null);
  const [carregando, setCarregando] = useState(true);
  const canal = useRef<ReturnType<typeof criarCanalDeSessao> | null>(null);

  // O canal nasce e morre dentro do efeito. Criá-lo no corpo do componente
  // sobreviveria à limpeza do StrictMode em desenvolvimento — que desmonta e
  // remonta de propósito — e a remontagem herdaria um canal já fechado.
  useEffect(() => {
    const atual = criarCanalDeSessao();
    canal.current = atual;

    // Sair em uma aba encerra as demais. O token já foi invalidado no
    // servidor; isto apenas impede que as outras abas sigam exibindo uma
    // interface autenticada até esbarrarem no próximo 401.
    const cancelar = atual.aoReceber(() => {
      limparCachesDeServidor();
      definirAccessToken(null);
      setUsuario(null);
    });

    return () => {
      cancelar();
      atual.fechar();
      canal.current = null;
    };
  }, []);

  useEffect(() => {
    let cancelado = false;
    (async () => {
      try {
        const renovou = await tentarRenovarSessao();
        if (!renovou) return;
        const encontrado = await authApi.usuarioAtual();
        if (!cancelado) {
          limparCachesDeServidor();
          setUsuario(encontrado);
        }
      } catch {
        // Sessão ausente ou expirada é o estado normal antes do login.
      } finally {
        if (!cancelado) setCarregando(false);
      }
    })();
    return () => { cancelado = true; };
  }, []);

  const adotarSessao = useCallback((sessao: Sessao) => {
    limparCachesDeServidor();
    definirAccessToken(sessao.accessToken);
    setUsuario(sessao.usuario);
  }, []);

  const entrar = useCallback(async (
    empresa: string,
    login: string,
    senha: string,
    codigoMfa?: string,
  ) => {
    adotarSessao(await authApi.entrar(empresa, login, senha, codigoMfa));
  }, [adotarSessao]);

  const iniciarCadastroMfa = useCallback((
    empresa: string,
    login: string,
    senha: string,
    codigoMfa?: string,
  ) => authApi.iniciarCadastroMfa(empresa, login, senha, codigoMfa), []);

  const ativarMfa = useCallback(async (desafio: string, codigo: string) => {
    const ativacao = await authApi.ativarMfa(desafio, codigo);
    adotarSessao(ativacao.sessao);
    return ativacao.codigosRecuperacao;
  }, [adotarSessao]);

  const sair = useCallback(async () => {
    try {
      await authApi.sair();
    } finally {
      // Limpa localmente mesmo se a chamada falhar: manter a interface
      // autenticada depois de o usuário pedir para sair é pior do que
      // divergir do servidor por um instante.
      limparCachesDeServidor();
      definirAccessToken(null);
      setUsuario(null);
      canal.current?.anunciarSaida();
    }
  }, []);

  const valor = useMemo(
    () => ({ usuario, carregando, entrar, iniciarCadastroMfa, ativarMfa, sair }),
    [usuario, carregando, entrar, iniciarCadastroMfa, ativarMfa, sair],
  );
  return <AuthContext value={valor}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const contexto = use(AuthContext);
  if (!contexto) throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  return contexto;
}
