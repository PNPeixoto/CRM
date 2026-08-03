import type { RotaApp } from '@/app/routes';
import type { ApresentacaoDoTenant, GrupoDeNavegacao } from './tipos';

export type ResultadoDoSinalDeAcesso = 'nao-publicado' | 'permitido' | 'negado';

export interface RotaResolvida extends RotaApp {
  readonly ordem: number;
  readonly visivel: boolean;
  readonly acesso: {
    readonly capability: ResultadoDoSinalDeAcesso;
    readonly entitlement: ResultadoDoSinalDeAcesso;
    readonly permissao: ResultadoDoSinalDeAcesso;
    readonly permitido: boolean;
  };
}

/**
 * View model da autorização de navegação. Cada lista usa os ids estáveis de
 * rota depois que um adaptador validar o contrato publicado pelo backend.
 * Ausência significa que a dimensão ainda não foi publicada, nunca que o
 * frontend concedeu autorização: os endpoints continuam protegidos no servidor.
 */
export interface SinaisDeAcessoDaNavegacao {
  readonly capabilities?: readonly string[];
  readonly entitlements?: readonly string[];
  readonly permissoes?: readonly string[];
}

const GRUPOS = new Set<GrupoDeNavegacao>(['operacao', 'gestao', 'plataforma']);

/**
 * Intersecta separadamente o build local, os sinais de acesso do backend e a
 * apresentação do segmento. O resultado dirige UX; segurança continua no API.
 */
export function resolveNavigation(
  routes: readonly RotaApp[],
  presentation: ApresentacaoDoTenant | null,
  sinais: SinaisDeAcessoDaNavegacao = {},
): readonly RotaResolvida[] {
  const knownIds = new Set(routes.map((route) => route.id));
  const overrides = new Map(
    (presentation?.navegacao ?? [])
      .filter((item) => knownIds.has(item.routeId))
      .map((item) => [item.routeId, item] as const),
  );

  const resolved = routes.map((route, index) => {
    const item = overrides.get(route.id);
    const capability = resolverSinal(sinais.capabilities, route.id);
    const entitlement = resolverSinal(sinais.entitlements, route.id);
    const permissao = resolverSinal(sinais.permissoes, route.id);
    const permitido = ![capability, entitlement, permissao].includes('negado');

    return {
      ...route,
      rotulo: item?.rotulo.trim() || route.rotulo,
      grupo: item && GRUPOS.has(item.grupo) ? item.grupo : route.grupo,
      ordem: item && Number.isFinite(item.ordem) ? item.ordem : (index + 1) * 10,
      visivel: (item?.visivel ?? true) && permitido,
      acesso: { capability, entitlement, permissao, permitido },
    } satisfies RotaResolvida;
  });

  // Um preset de apresentação malformado não prende o usuário sem destino, mas
  // este fallback jamais reexibe rota negada por qualquer sinal de acesso.
  if (!resolved.some((route) => route.visivel)) {
    const permitidas = resolved.filter((route) => route.acesso.permitido);
    const safeRoute = permitidas.find((route) => route.id === 'dashboard') ?? permitidas[0];
    const safeIndex = safeRoute ? resolved.findIndex((route) => route.id === safeRoute.id) : -1;
    if (safeIndex >= 0) {
      resolved[safeIndex] = { ...resolved[safeIndex], visivel: true, ordem: 0 };
    }
  }

  return resolved.toSorted((left, right) => left.ordem - right.ordem);
}

export function caminhoInicialSeguro(routes: readonly RotaResolvida[]): string {
  return routes.find((route) => route.visivel)?.caminho ?? '/acesso-negado';
}

function resolverSinal(
  rotasPermitidas: readonly string[] | undefined,
  routeId: string,
): ResultadoDoSinalDeAcesso {
  if (!rotasPermitidas) return 'nao-publicado';
  return rotasPermitidas.includes(routeId) ? 'permitido' : 'negado';
}
