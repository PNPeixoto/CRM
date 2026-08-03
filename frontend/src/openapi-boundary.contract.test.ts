import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

function fontes(diretorio: string): string[] {
  return readdirSync(diretorio, { withFileTypes: true }).flatMap((item) => {
    const caminho = join(diretorio, item.name);
    if (item.isDirectory()) return fontes(caminho);
    return /\.(ts|tsx)$/.test(item.name) ? [caminho] : [];
  });
}

describe('fronteira OpenAPI', () => {
  it('mantém o contrato gerado restrito à camada adaptadora', () => {
    const raiz = join(process.cwd(), 'src');
    const referenciaGerada = ['@', 'generated', 'api-schema'].join('/');
    const importadores = fontes(raiz)
      .filter((arquivo) => !arquivo.includes(`${join('src', 'generated')}`))
      .filter((arquivo) => readFileSync(arquivo, 'utf8').includes(referenciaGerada))
      .map((arquivo) => relative(process.cwd(), arquivo).replaceAll('\\', '/'));

    expect(importadores).toEqual(['src/adapters/http/contracts.ts']);
  });

  it('fixa a versão do gerador sem intervalo semântico', () => {
    const pacote = JSON.parse(readFileSync(join(process.cwd(), 'package.json'), 'utf8')) as {
      devDependencies: Record<string, string>;
    };
    expect(pacote.devDependencies['openapi-typescript']).toBe('7.13.0');
  });
});
