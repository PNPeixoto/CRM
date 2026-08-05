import type {
  LoginWire,
  MfaActivationRequestWire,
  MfaActivationResponseWire,
  MfaEnrollmentWire,
  SessaoWire,
  UsuarioWire,
} from '@/adapters/http/contracts';
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

export interface CadastroMfa {
  readonly desafio: string;
  readonly segredo: string;
  readonly otpauthUri: string;
  readonly expiraEmSegundos: number;
}

export interface AtivacaoMfa {
  readonly sessao: Sessao;
  readonly codigosRecuperacao: readonly string[];
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

function mapearCadastroMfa(dados: MfaEnrollmentWire): CadastroMfa {
  return {
    desafio: obrigatorio(dados.desafio, 'mfa.desafio'),
    segredo: obrigatorio(dados.segredo, 'mfa.segredo'),
    otpauthUri: obrigatorio(dados.otpauthUri, 'mfa.otpauthUri'),
    expiraEmSegundos: obrigatorio(dados.expiraEmSegundos, 'mfa.expiraEmSegundos'),
  };
}

function mapearAtivacaoMfa(dados: MfaActivationResponseWire): AtivacaoMfa {
  return {
    sessao: mapearSessao(obrigatorio(dados.sessao, 'mfa.sessao')),
    codigosRecuperacao: [...obrigatorio(
      dados.codigosRecuperacao,
      'mfa.codigosRecuperacao',
    )],
  };
}

export const authApi = {
  entrar: async (
    empresa: string,
    login: string,
    senha: string,
    codigoMfa?: string,
  ): Promise<Sessao> => {
    const corpo: LoginWire = { empresa, login, senha, codigoMfa };
    return mapearSessao(await api.post<SessaoWire>('/auth/login', corpo));
  },
  iniciarCadastroMfa: async (
    empresa: string,
    login: string,
    senha: string,
    codigoMfa?: string,
  ): Promise<CadastroMfa> => {
    const corpo: LoginWire = { empresa, login, senha, codigoMfa };
    return mapearCadastroMfa(
      await api.post<MfaEnrollmentWire>('/auth/mfa/enrollment', corpo),
    );
  },
  ativarMfa: async (desafio: string, codigo: string): Promise<AtivacaoMfa> => {
    const corpo: MfaActivationRequestWire = { desafio, codigo };
    return mapearAtivacaoMfa(
      await api.post<MfaActivationResponseWire>('/auth/mfa/activation', corpo),
    );
  },
  usuarioAtual: async (): Promise<Usuario> =>
    mapearUsuario(await api.get<UsuarioWire>('/auth/me')),
  sair: (): Promise<void> => api.post<void>('/auth/logout'),
};
