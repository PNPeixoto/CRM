import { describe, expect, it } from 'vitest';
import { ROTAS } from '@/app/routes';
import { caminhoInicialSeguro, resolveNavigation } from './resolveNavigation';
import type { ApresentacaoDoTenant } from './tipos';

function presentation(
  navigation: ApresentacaoDoTenant['navegacao'],
): ApresentacaoDoTenant {
  return {
    segmento: 'CONFECTIONERY',
    versaoPreset: 1,
    onboardingConcluido: true,
    navegacao: navigation,
    funilPadrao: { nome: 'Encomendas', etapas: [] },
  };
}

describe('resolveNavigation', () => {
  it('applies labels, groups, order and visibility', () => {
    const routes = resolveNavigation(ROTAS, presentation([
      { routeId: 'contacts', rotulo: 'Clientes', grupo: 'gestao', ordem: 20, visivel: true },
      { routeId: 'pipelines', rotulo: 'Encomendas', grupo: 'operacao', ordem: 10, visivel: false },
    ]));

    expect(routes.find((route) => route.id === 'contacts')).toMatchObject({
      rotulo: 'Clientes', grupo: 'gestao', ordem: 20, visivel: true,
    });
    expect(routes.find((route) => route.id === 'pipelines')).toMatchObject({
      rotulo: 'Encomendas', ordem: 10, visivel: false,
    });
    expect(routes.findIndex((route) => route.id === 'pipelines'))
      .toBeLessThan(routes.findIndex((route) => route.id === 'contacts'));
  });

  it('ignores unknown route ids and retains defaults', () => {
    const routes = resolveNavigation(ROTAS, presentation([
      { routeId: 'future-module', rotulo: 'Futuro', grupo: 'operacao', ordem: 0, visivel: true },
    ]));

    expect(routes).toHaveLength(ROTAS.length);
    expect(routes.some((route) => route.id === 'future-module')).toBe(false);
    expect(routes.find((route) => route.id === 'contacts')?.rotulo).toBe('Contatos');
  });

  it('keeps a safe initial route when every override is hidden', () => {
    const routes = resolveNavigation(ROTAS, presentation(
      ROTAS.map((route, index) => ({
        routeId: route.id,
        rotulo: route.rotulo,
        grupo: route.grupo,
        ordem: index,
        visivel: false,
      })),
    ));

    expect(caminhoInicialSeguro(routes)).toBe('/dashboard');
    expect(routes.find((route) => route.id === 'dashboard')?.visivel).toBe(true);
  });

  it('intersecta capability, entitlement e permissão separadamente', () => {
    const routes = resolveNavigation(ROTAS, presentation([]), {
      capabilities: ['dashboard', 'contacts'],
      entitlements: ['dashboard'],
      permissoes: ['reports.read', 'contacts.read'],
    });

    expect(routes.find((route) => route.id === 'dashboard')?.acesso).toEqual({
      capability: 'permitido',
      entitlement: 'permitido',
      permissao: 'permitido',
      permitido: true,
    });
    expect(routes.find((route) => route.id === 'contacts')?.acesso).toEqual({
      capability: 'permitido',
      entitlement: 'negado',
      permissao: 'permitido',
      permitido: false,
    });
    expect(routes.find((route) => route.id === 'contacts')?.visivel).toBe(false);
  });

  it('uses the atomic backend codes instead of route ids as permissions', () => {
    const routes = resolveNavigation(ROTAS, presentation([]), {
      permissoes: ['contacts.read'],
    });

    expect(routes.find((route) => route.id === 'contacts')?.acesso.permissao).toBe('permitido');
    expect(routes.find((route) => route.id === 'dashboard')?.acesso.permissao).toBe('negado');
    expect(routes.find((route) => route.id === 'calendar')?.acesso.permissao).toBe('negado');
  });

  it('não reexibe rota negada como fallback', () => {
    const routes = resolveNavigation(ROTAS, presentation([]), {
      capabilities: [],
      entitlements: [],
      permissoes: [],
    });

    expect(routes.every((route) => !route.visivel)).toBe(true);
    expect(caminhoInicialSeguro(routes)).toBe('/acesso-negado');
  });
});
