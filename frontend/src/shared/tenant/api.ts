import type {
  ApresentacaoWire,
  PerfilInicialWire,
  PermissoesWire,
} from '@/adapters/http/contracts';
import { obrigatorio, umDe } from '@/adapters/http/mapping';
import { api } from '@/lib/api';
import type {
  ApresentacaoDoTenant,
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

export async function obterApresentacao(): Promise<ApresentacaoDoTenant> {
  return mapearApresentacao(await api.get<ApresentacaoWire>('/empresa/apresentacao'));
}

export async function obterPermissoes(): Promise<PermissoesDoUsuario> {
  const dados = await api.get<PermissoesWire>('/organizacao/permissoes');
  return Object.fromEntries(
    Object.entries(dados).map(([codigo, escopo]) => [
      codigo,
      umDe(escopo, ESCOPOS, `permissoes.${codigo}`),
    ]),
  ) as Readonly<Record<string, EscopoDePermissao>>;
}

export async function salvarPerfilInicial(
  segmento: SegmentoDeNegocio,
): Promise<ApresentacaoDoTenant> {
  const corpo: PerfilInicialWire = { segmento };
  return mapearApresentacao(
    await api.put<ApresentacaoWire>('/empresa/perfil-inicial', corpo),
  );
}
