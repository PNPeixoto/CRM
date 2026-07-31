/**
 * Formatação de exibição.
 *
 * Regra que governa este arquivo: **dinheiro trafega em centavos, como
 * inteiro, do banco até aqui**. A divisão por 100 acontece uma única vez, na
 * borda da tela. Fazer a conversão mais cedo reintroduziria ponto flutuante no
 * caminho, que é exatamente onde o erro de arredondamento entra e se acumula.
 */

const FUSO_DE_NEGOCIO = 'America/Sao_Paulo';

export function formatarMoeda(centavos: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(centavos / 100);
}

/**
 * Converte "1.234,56" ou "1234.56" para centavos.
 *
 * Multiplicar por 100 e arredondar, em vez de manipular string: o usuário
 * digita de formas imprevisíveis, e `Math.round` resolve o caso clássico em
 * que `12.35 * 100` dá `1234.9999999999998` em ponto flutuante.
 */
export function paraCentavos(texto: string): number {
  const limpo = texto.replace(/\s/g, '').replace(/\./g, '').replace(',', '.');
  const numero = Number.parseFloat(limpo);
  return Number.isFinite(numero) ? Math.round(numero * 100) : 0;
}

/** Sempre no fuso de negócio, nunca no do navegador — ver ListaDeConversas. */
export function formatarData(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(new Date(iso));
}

export function formatarDataHora(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(new Date(iso));
}

export function formatarNumero(valor: number): string {
  return new Intl.NumberFormat('pt-BR').format(valor);
}
