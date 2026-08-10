import type {
  ApresentacaoWire,
  PerfilInicialWire,
  PermissoesWire,
  ResumoDeAcessoWire,
} from '@/adapters/http/contracts';
import { obrigatorio, umDe } from '@/adapters/http/mapping';
import { api } from '@/lib/api';
import type {
  ApresentacaoDoTenant,
  ResumoDeAcessoDaOrganizacao,
  EscopoDePermissao,
  PermissoesDoUsuario,
  SegmentoDeNegocio,
} from './tipos';

const SEGMENTOS = ['GENERAL_SERVICES', 'RESTAURANT', 'CONFECTIONERY', 'RENTAL'] as const;
const GRUPOS = ['operacao', 'gestao', 'plataforma'] as const;
const ESCOPOS = ['TENANT', 'OWN', 'UNIT', 'TEAM', 'NETWORK'] as const;

function mapearApresentacao(dados: ApresentacaoWire): ApresentacaoDoTenant {
  const funil = obrigatorio(dados.funilPadrao, 'apresentacao.funilPadrao');
  return {
    segmento: umDe(dados.segmento, SEGMENTOS, 'apresentacao.segmento'),
    versaoPreset: obrigatorio(dados.versaoPreset, 'apresentacao.versaoPreset'),
    onboardingConcluido: obrigatorio(
      dados.onboardingConcluido,
      'apresentacao.onboardingConcluido',
    ),
    navegacao: obrigatorio(dados.navegacao, 'apresentacao.navegacao').map((item) => ({
      routeId: obrigatorio(item.routeId, 'navegacao.routeId'),
      rotulo: obrigatorio(item.rotulo, 'navegacao.rotulo'),
      grupo: umDe(item.grupo, GRUPOS, 'navegacao.grupo'),
      ordem: obrigatorio(item.ordem, 'navegacao.ordem'),
      visivel: obrigatorio(item.visivel, 'navegacao.visivel'),
    })),
    funilPadrao: {
      nome: obrigatorio(funil.nome, 'funilPadrao.nome'),
      etapas: obrigatorio(funil.etapas, 'funilPadrao.etapas').map((etapa) => ({
        nome: obrigatorio(etapa.nome, 'funilPadrao.etapa.nome'),
        posicao: obrigatorio(etapa.posicao, 'funilPadrao.etapa.posicao'),
        ganho: obrigatorio(etapa.ganho, 'funilPadrao.etapa.ganho'),
        perda: obrigatorio(etapa.perda, 'funilPadrao.etapa.perda'),
      })),
    },
  };
}

export async function obterApresentacao(signal?: AbortSignal): Promise<ApresentacaoDoTenant> {
  return mapearApresentacao(await api.get<ApresentacaoWire>('/empresa/apresentacao', { signal }));
}

export async function obterPermissoes(signal?: AbortSignal): Promise<PermissoesDoUsuario> {
  const dados = await api.get<PermissoesWire>('/organizacao/permissoes', { signal });
  return Object.fromEntries(
    Object.entries(dados).map(([codigo, escopo]) => [
      codigo,
      umDe(escopo, ESCOPOS, `permissoes.${codigo}`),
    ]),
  ) as Readonly<Record<string, EscopoDePermissao>>;
}

export async function obterContextos(
  signal?: AbortSignal,
): Promise<ResumoDeAcessoDaOrganizacao> {
  const dados = await api.get<ResumoDeAcessoWire>('/organizacao/contextos', { signal });
  return {
    tenantId: obrigatorio(dados.tenantId, 'contextos.tenantId'),
    membershipId: obrigatorio(dados.membershipId, 'contextos.membershipId'),
    contextos: obrigatorio(dados.contexts, 'contextos.contexts').map((contexto) => ({
      id: obrigatorio(contexto.id, 'contexto.id'),
      tipo: umDe(contexto.type, ESCOPOS, 'contexto.tipo'),
      codigo: obrigatorio(contexto.code, 'contexto.codigo'),
      nome: obrigatorio(contexto.name, 'contexto.nome'),
      papeis: obrigatorio(contexto.roles, 'contexto.papeis'),
      permissoes: obrigatorio(contexto.permissions, 'contexto.permissoes'),
      escopos: obrigatorio(contexto.scopes, 'contexto.escopos').map((escopo) =>
        umDe(escopo, ESCOPOS, 'contexto.escopo'),
      ),
    })),
  };
}

export async function salvarPerfilInicial(
  segmento: SegmentoDeNegocio,
): Promise<ApresentacaoDoTenant> {
  const corpo: PerfilInicialWire = { segmento };
  return mapearApresentacao(
    await api.put<ApresentacaoWire>('/empresa/perfil-inicial', corpo),
  );
}
