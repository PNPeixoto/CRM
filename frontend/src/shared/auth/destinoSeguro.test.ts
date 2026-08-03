import { describe, expect, it } from 'vitest';
import { DESTINO_PADRAO, destinoInternoSeguro } from './destinoSeguro';

const ORIGEM = 'https://crm.exemplo.test';

describe('destino interno seguro', () => {
  it('preserva caminho interno com consulta e âncora', () => {
    expect(destinoInternoSeguro('/contatos', ORIGEM)).toBe('/contatos');
    expect(destinoInternoSeguro('/contatos?pagina=2', ORIGEM)).toBe('/contatos?pagina=2');
    expect(destinoInternoSeguro('/funis#ganhos', ORIGEM)).toBe('/funis#ganhos');
  });

  it.each([
    ['protocolo-relativo', '//exemplo.invalido'],
    ['protocolo-relativo com barra invertida', '/\\exemplo.invalido'],
    ['absoluto http', 'https://exemplo.invalido/painel'],
    ['esquema executável', 'javascript:alert(1)'],
    ['esquema de dados', 'data:text/html,<script>alert(1)</script>'],
    ['sem barra inicial', 'exemplo.invalido'],
    ['barra invertida dupla', '\\\\exemplo.invalido'],
  ])('recusa destino externo — %s', (_caso, entrada) => {
    expect(destinoInternoSeguro(entrada, ORIGEM)).toBe(DESTINO_PADRAO);
  });

  it('recusa caminho com caractere de controle', () => {
    // A tabulação é descartada por alguns parsers de URL, e o que sobra é
    // `//exemplo.invalido` — externo.
    expect(destinoInternoSeguro('/\texemplo.invalido', ORIGEM)).toBe(DESTINO_PADRAO);
    expect(destinoInternoSeguro('/\nexemplo', ORIGEM)).toBe(DESTINO_PADRAO);
    expect(destinoInternoSeguro('/con' + String.fromCharCode(1) + 'tatos', ORIGEM)).toBe(DESTINO_PADRAO);
  });

  it('recusa valor que não é texto', () => {
    expect(destinoInternoSeguro(undefined, ORIGEM)).toBe(DESTINO_PADRAO);
    expect(destinoInternoSeguro(null, ORIGEM)).toBe(DESTINO_PADRAO);
    expect(destinoInternoSeguro(42, ORIGEM)).toBe(DESTINO_PADRAO);
    expect(destinoInternoSeguro({ toString: () => '/contatos' }, ORIGEM)).toBe(DESTINO_PADRAO);
    expect(destinoInternoSeguro('   ', ORIGEM)).toBe(DESTINO_PADRAO);
  });

  it('não devolve a própria tela de login, sob pena de laço', () => {
    expect(destinoInternoSeguro('/login', ORIGEM)).toBe(DESTINO_PADRAO);
  });

  it('nunca devolve destino de outra origem, qualquer que seja a entrada', () => {
    const entradas = [
      '//exemplo.invalido',
      'https://exemplo.invalido',
      '/\\/exemplo.invalido',
      '/caminho/../../fora',
      '/%2f%2fexemplo.invalido',
    ];
    for (const entrada of entradas) {
      const resultado = destinoInternoSeguro(entrada, ORIGEM);
      expect(new URL(resultado, ORIGEM).origin, entrada).toBe(ORIGEM);
    }
  });
});
