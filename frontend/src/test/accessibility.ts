import axe from 'axe-core';
import { expect } from 'vitest';

/**
 * Auditoria semântica automatizada. Contraste permanece em auditoria visual:
 * jsdom não calcula layout nem cores como um navegador real.
 */
export async function analisarAcessibilidade(contexto: Element | Document = document) {
  return axe.run(contexto, {
    rules: {
      'color-contrast': { enabled: false },
    },
  });
}

export async function esperarSemViolacoesAcessiveis(
  contexto: Element | Document = document,
): Promise<void> {
  const resultado = await analisarAcessibilidade(contexto);
  const resumo = resultado.violations
    .map((violacao) => {
      const alvos = violacao.nodes.flatMap((node) => node.target).join(', ');
      return `${violacao.id} [${violacao.impact ?? 'sem impacto'}]: ${alvos}`;
    })
    .join('\n');

  // Não inclui HTML, texto do formulário ou payload: o diagnóstico expõe
  // apenas regra, impacto e seletor estrutural.
  expect(resultado.violations, resumo).toEqual([]);
}
