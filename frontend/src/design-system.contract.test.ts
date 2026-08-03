/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const estilos = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8');

const fontesDeProducao = import.meta.glob<string>(
  ['./**/*.ts', './**/*.tsx'],
  { eager: true, import: 'default', query: '?raw' },
);

const fontesSemTestes = Object.entries(fontesDeProducao).filter(
  ([caminho]) => !caminho.includes('.test.'),
);

describe('contrato do design system', () => {
  it('mantém literais de cor restritos às primitivas de paleta', () => {
    for (const [caminho, fonte] of fontesSemTestes) {
      expect(fonte, caminho).not.toMatch(/#[\da-f]{3,8}\b|\b(?:rgb|hsl|oklch)a?\(/i);
      expect(fonte, caminho).not.toMatch(/var\(--palette-/);
      expect(fonte, caminho).not.toMatch(
        /(?:bg|text|border|ring|outline|fill|stroke|from|via|to)-(?:red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|slate|gray|zinc|neutral|stone|white|black)(?:-|\b)/,
      );
    }

    const cssSemComentarios = estilos.replace(/\/\*[\s\S]*?\*\//g, '');
    const linhasComLiteral = cssSemComentarios
      .split(/\r?\n/)
      .filter((linha) => /#[\da-f]{3,8}\b|\b(?:rgb|hsl|oklch)a?\(/i.test(linha));

    expect(linhasComLiteral.length).toBeGreaterThan(0);
    expect(linhasComLiteral.every((linha) => /^\s*--palette-[\w-]+:\s*/.test(linha))).toBe(true);
  });

  it.each(['claro', 'escuro'] as const)(
    'mantém contraste mínimo nos pares semânticos do tema %s',
    (tema) => {
      const tokens = tokensDoTema(tema);
      const paresDeTexto = [
        ['--text-strong', '--surface-base'],
        ['--text-muted', '--surface-base'],
        ['--text-on-brand', '--brand'],
        ['--text-on-brand', '--brand-hover'],
        ['--text-on-brand', '--danger'],
        ['--text-on-shell', '--surface-shell'],
        ['--text-on-shell-muted', '--surface-shell'],
        ['--success', '--success-soft'],
        ['--danger', '--danger-soft'],
        ['--warning', '--warning-soft'],
        ['--info', '--info-soft'],
      ] as const;

      for (const [frente, fundo] of paresDeTexto) {
        expect(contraste(resolveToken(frente, tokens), resolveToken(fundo, tokens)), `${frente} sobre ${fundo}`).toBeGreaterThanOrEqual(4.5);
      }

      expect(
        contraste(resolveToken('--border-control', tokens), resolveToken('--surface-raised', tokens)),
      ).toBeGreaterThanOrEqual(3);
      expect(
        contraste(resolveToken('--focus-ring', tokens), resolveToken('--surface-base', tokens)),
      ).toBeGreaterThanOrEqual(3);
    },
  );

  it('possui mapeamento escuro completo e redução de movimento', () => {
    const claro = declaracoes(bloco(':root'));
    const escuro = declaracoes(bloco(':root[data-theme="dark"]'));
    const tokensSemanticosDeCor = [...claro.keys()].filter(
      (token) =>
        !token.startsWith('--palette-') &&
        /^(--surface-|--text-|--border-|--brand|--success|--danger|--warning|--info|--focus-ring)/.test(token),
    );

    expect(tokensSemanticosDeCor.length).toBeGreaterThan(0);
    expect(tokensSemanticosDeCor.every((token) => escuro.has(token))).toBe(true);
    expect(estilos).toMatch(/@media\s*\(prefers-reduced-motion:\s*reduce\)/);
    expect(estilos).toMatch(/:focus-visible\s*\{[\s\S]*outline:\s*2px solid var\(--focus-ring\)/);
  });
});

function tokensDoTema(tema: 'claro' | 'escuro'): Map<string, string> {
  const tokens = declaracoes(bloco(':root'));
  if (tema === 'escuro') {
    for (const [nome, valor] of declaracoes(bloco(':root[data-theme="dark"]'))) {
      tokens.set(nome, valor);
    }
  }
  return tokens;
}

function bloco(seletor: string): string {
  const escapado = seletor.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const encontrado = estilos.match(new RegExp(`${escapado}\\s*\\{([\\s\\S]*?)\\}`));
  expect(encontrado, `bloco ${seletor}`).not.toBeNull();
  return encontrado?.[1] ?? '';
}

function declaracoes(css: string): Map<string, string> {
  return new Map(
    [...css.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map((resultado) => [
      resultado[1],
      resultado[2].trim(),
    ]),
  );
}

function resolveToken(nome: string, tokens: Map<string, string>, visitados = new Set<string>()): string {
  expect(visitados.has(nome), `ciclo em ${nome}`).toBe(false);
  const valor = tokens.get(nome);
  expect(valor, `token ${nome}`).toBeDefined();

  const referencia = valor?.match(/^var\((--[\w-]+)\)$/)?.[1];
  if (!referencia) return valor ?? '';

  visitados.add(nome);
  return resolveToken(referencia, tokens, visitados);
}

function contraste(frente: string, fundo: string): number {
  const luminanciaFrente = luminancia(frente);
  const luminanciaFundo = luminancia(fundo);
  return (
    (Math.max(luminanciaFrente, luminanciaFundo) + 0.05) /
    (Math.min(luminanciaFrente, luminanciaFundo) + 0.05)
  );
}

function luminancia(cor: string): number {
  expect(cor).toMatch(/^#[\da-f]{6}$/i);
  const canais = [1, 3, 5].map((inicio) => Number.parseInt(cor.slice(inicio, inicio + 2), 16) / 255);
  const lineares = canais.map((canal) =>
    canal <= 0.04045 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4,
  );
  return 0.2126 * lineares[0] + 0.7152 * lineares[1] + 0.0722 * lineares[2];
}
