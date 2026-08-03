/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Contrato de segurança do navegador.
 *
 * Estas regras existem porque cada uma delas é fácil de reintroduzir sem má
 * intenção: um `dangerouslySetInnerHTML` para renderizar negrito, um
 * `target="_blank"` num link de ajuda, um token guardado em `localStorage`
 * "só para não perder a sessão no F5". Nenhum desses aparece em revisão como
 * problema de segurança — aparece como conveniência.
 *
 * O teste é grosseiro de propósito: varre o texto-fonte. Um analisador de AST
 * seria mais preciso e mais fácil de contornar sem perceber.
 */

const raiz = process.cwd();

const fontes = Object.entries(
  import.meta.glob<string>(['./**/*.ts', './**/*.tsx'], {
    eager: true,
    import: 'default',
    query: '?raw',
  }),
).filter(([caminho]) => !caminho.includes('.test.') && !caminho.includes('/test/'));

function semComentarios(fonte: string): string {
  return fonte.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

describe('contrato de segurança do navegador', () => {
  it('não admite execução de código gerado em tempo de execução', () => {
    for (const [caminho, fonte] of fontes) {
      const codigo = semComentarios(fonte);
      expect(codigo, `${caminho}: eval`).not.toMatch(/\beval\s*\(/);
      expect(codigo, `${caminho}: new Function`).not.toMatch(/\bnew\s+Function\s*\(/);
      expect(codigo, `${caminho}: setTimeout com string`).not.toMatch(
        /\bset(?:Timeout|Interval)\s*\(\s*['"`]/,
      );
    }
  });

  it('não injeta HTML arbitrário no documento', () => {
    for (const [caminho, fonte] of fontes) {
      const codigo = semComentarios(fonte);
      expect(codigo, `${caminho}: dangerouslySetInnerHTML`).not.toMatch(
        /dangerouslySetInnerHTML/,
      );
      expect(codigo, `${caminho}: innerHTML`).not.toMatch(/\.(?:inner|outer)HTML\s*=/);
      expect(codigo, `${caminho}: insertAdjacentHTML`).not.toMatch(/insertAdjacentHTML/);
      expect(codigo, `${caminho}: document.write`).not.toMatch(/document\.write/);
    }
  });

  it('não abre nova aba sem cortar o vínculo com a janela de origem', () => {
    // Coleta em vez de assertar dentro do laço: hoje não existe nenhum link
    // externo, e um teste que só assere quando encontra algo passaria sem
    // verificar nada — inclusive depois que alguém acrescentasse o primeiro.
    const violacoes: string[] = [];

    for (const [caminho, fonte] of fontes) {
      const codigo = semComentarios(fonte);
      // `window.opener` na aba nova permite reescrever a aba de origem:
      // phishing a partir de um link legítimo.
      for (const marcacao of codigo.match(/<a\b[^>]*target\s*=\s*["{]_blank[^>]*>/g) ?? []) {
        const rel = marcacao.match(/rel\s*=\s*["{]([^"}]*)/)?.[1] ?? '';
        if (!rel.includes('noopener') || !rel.includes('noreferrer')) {
          violacoes.push(`${caminho}: target="_blank" sem rel="noopener noreferrer"`);
        }
      }
      for (const chamada of codigo.match(/window\.open\s*\([^)]*\)/g) ?? []) {
        if (!chamada.includes('noopener')) {
          violacoes.push(`${caminho}: window.open sem noopener`);
        }
      }
    }

    expect(violacoes).toEqual([]);
  });

  it('não usa esquema de URL executável', () => {
    for (const [caminho, fonte] of fontes) {
      const codigo = semComentarios(fonte);
      expect(codigo, `${caminho}: javascript:`).not.toMatch(/["'`]\s*javascript:/i);
      expect(codigo, `${caminho}: data:text/html`).not.toMatch(/data:text\/html/i);
    }
  });

  it('mantém o access token fora de qualquer persistência do navegador', () => {
    for (const [caminho, fonte] of fontes) {
      const codigo = semComentarios(fonte);
      const persiste = /\b(?:localStorage|sessionStorage|indexedDB)\b/.test(codigo);
      if (!persiste) continue;

      // Só a preferência de interface pode tocar armazenamento. Qualquer outro
      // arquivo que passe a persistir precisa de decisão explícita, não de um
      // teste que se ajusta sozinho.
      expect(caminho, `${caminho} passou a usar armazenamento do navegador`).toMatch(
        /preferences\/menuPreference\.ts$/,
      );
    }
  });

  it('não deriva chave de armazenamento de dado identificável em claro', () => {
    const preferencia = readFileSync(
      resolve(raiz, 'src/shared/preferences/menuPreference.ts'),
      'utf8',
    );
    // A chave passa por hash: quem inspeciona o armazenamento não colhe o id.
    expect(preferencia).toMatch(/hash/i);
    expect(preferencia).not.toMatch(/\$\{usuarioId\}/);
  });

  it('declara CSP sem execução genérica e sem confiar apenas na meta', () => {
    const html = readFileSync(resolve(raiz, 'index.html'), 'utf8');
    const meta = html.match(
      /http-equiv\s*=\s*"Content-Security-Policy"\s*\n?\s*content\s*=\s*"([^"]+)"/,
    );
    expect(meta, 'index.html precisa declarar CSP em meta').not.toBeNull();

    const politica = meta![1];
    expect(politica).toMatch(/default-src 'self'/);
    expect(politica).toMatch(/object-src 'none'/);
    expect(politica).toMatch(/base-uri 'self'/);
    expect(politica).toMatch(/form-action 'self'/);

    const scriptSrc = politica.match(/script-src ([^;]+)/)?.[1] ?? '';
    expect(scriptSrc, 'script-src não pode admitir execução genérica').not.toMatch(
      /unsafe-eval|unsafe-inline/,
    );

    // `frame-ancestors` é ignorado em meta pelo navegador. Declará-la aqui
    // criaria a impressão de proteção que não existe; a exigência é do
    // cabeçalho, e está documentada.
    expect(politica, 'frame-ancestors em meta é ignorada pelo navegador').not.toMatch(
      /frame-ancestors/,
    );
    const documento = readFileSync(resolve(raiz, 'SEGURANCA-NAVEGADOR.md'), 'utf8');
    expect(documento).toMatch(/frame-ancestors/);
  });

  it('não publica source map de produção', () => {
    const config = readFileSync(resolve(raiz, 'vite.config.ts'), 'utf8');
    expect(config).toMatch(/sourcemap:\s*false/);
  });

  it('fixa runtime e gerenciador de pacotes', () => {
    const pacote = JSON.parse(readFileSync(resolve(raiz, 'package.json'), 'utf8')) as {
      engines?: { node?: string };
      packageManager?: string;
    };
    expect(pacote.engines?.node).toBeTruthy();
    expect(pacote.packageManager).toMatch(/^npm@\d+\.\d+\.\d+$/);
  });
});
