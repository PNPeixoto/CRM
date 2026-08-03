/**
 * Valida o destino de retorno pós-login.
 *
 * <b>Por que isto existe.</b> O destino vem de `location.state`, gravado pelo
 * guarda de rota a partir do caminho que o usuário tentou abrir. Parece
 * interno por construção, e não é: o estado do histórico é gravável por
 * qualquer código da página, e um caminho como `//exemplo.invalido` é
 * *protocolo-relativo* — o navegador o resolve como host externo. O resultado
 * é um redirecionamento aberto que sai de uma tela de login legítima, que é
 * exatamente o que dá credibilidade a um phishing.
 *
 * A validação é por allowlist de forma, não por lista negra de padrões
 * perigosos: lista negra erra sempre que alguém inventa uma codificação nova.
 */

export const DESTINO_PADRAO = '/dashboard';

/** Rotas que nunca são destino de retorno, sob pena de laço. */
const NUNCA_RETORNAR_PARA = new Set(['/login']);

/** Controle e DEL. Alguns parsers os descartam, e o descarte pode revelar `//`. */
function temCaractereDeControle(valor: string): boolean {
  for (const caractere of valor) {
    const codigo = caractere.codePointAt(0) ?? 0;
    if (codigo < 0x20 || codigo === 0x7f) return true;
  }
  return false;
}

export function destinoInternoSeguro(
  bruto: unknown,
  origem: string = globalThis.location?.origin ?? 'http://localhost',
): string {
  if (typeof bruto !== 'string') return DESTINO_PADRAO;

  const candidato = bruto.trim();
  if (candidato === '') return DESTINO_PADRAO;
  if (temCaractereDeControle(candidato)) return DESTINO_PADRAO;

  // Precisa começar com uma única barra. Isso já elimina URL absoluta
  // (`https://…`), protocolo-relativa (`//host`) e esquema executável
  // (`javascript:`). A barra invertida entra na checagem porque o navegador a
  // normaliza para barra: `/\host` vira `//host`.
  if (!candidato.startsWith('/')) return DESTINO_PADRAO;
  if (candidato.startsWith('//') || candidato.startsWith('/\\')) return DESTINO_PADRAO;

  let url: URL;
  try {
    url = new URL(candidato, origem);
  } catch {
    return DESTINO_PADRAO;
  }

  // Segunda barreira, depois da normalização do próprio parser: se o que saiu
  // dele aponta para outra origem, o candidato mentiu sobre a forma.
  let origemEsperada: string;
  try {
    origemEsperada = new URL(origem).origin;
  } catch {
    return DESTINO_PADRAO;
  }
  if (url.origin !== origemEsperada) return DESTINO_PADRAO;

  if (NUNCA_RETORNAR_PARA.has(url.pathname)) return DESTINO_PADRAO;

  return `${url.pathname}${url.search}${url.hash}`;
}
