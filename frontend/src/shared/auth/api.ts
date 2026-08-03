import type { LoginWire, SessaoWire, UsuarioWire } from '@/adapters/http/contracts';
import { obrigatorio } from '@/adapters/http/mapping';
import { api } from '@/lib/api';

export interface Usuario {
  readonly id: string;
  readonly tenantId: string;
  readonly login: string;
  readonly nomeCompleto: string;
}

export interface Sessao {
  readonly accessToken: string;
  readonly expiraEmSegundos: number;
  readonly mfaVerificado: boolean;
  readonly usuario: Usuario;
}

function mapearUsuario(dados: UsuarioWire): Usuario {
  return {
    id: obrigatorio(dados.id, 'usuario.id'),
    tenantId: obrigatorio(dados.tenantId, 'usuario.tenantId'),
    login: obrigatorio(dados.login, 'usuario.login'),
    nomeCompleto: obrigatorio(dados.nomeCompleto, 'usuario.nomeCompleto'),
  };
}

function mapearSessao(dados: SessaoWire): Sessao {
  return {
    accessToken: obrigatorio(dados.accessToken, 'sessao.accessToken'),
    expiraEmSegundos: obrigatorio(dados.expiraEmSegundos, 'sessao.expiraEmSegundos'),
    mfaVerificado: dados.mfaVerificado ?? false,
    usuario: mapearUsuario(obrigatorio(dados.usuario, 'sessao.usuario')),
  };
}

export const authApi = {
  entrar: async (empresa: string, login: string, senha: string): Promise<Sessao> => {
    const corpo: LoginWire = { empresa, login, senha };
    return mapearSessao(await api.post<SessaoWire>('/auth/login', corpo));
  },
  usuarioAtual: async (): Promise<Usuario> =>
    mapearUsuario(await api.get<UsuarioWire>('/auth/me')),
  sair: (): Promise<void> => api.post<void>('/auth/logout'),
};
