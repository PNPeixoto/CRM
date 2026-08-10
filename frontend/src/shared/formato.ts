/**
 * Formatação de exibição.
 *
 * Regra que governa este arquivo: **dinheiro trafega em centavos, como
 * inteiro, do banco até aqui**. A divisão por 100 acontece uma única vez, na
 * borda da tela. Fazer a conversão mais cedo reintroduziria ponto flutuante no
 * caminho, que é exatamente onde o erro de arredondamento entra e se acumula.
 */

export const FUSO_DE_NEGOCIO = 'America/Sao_Paulo';

export interface ValorMonetario {
  readonly amountMinor: number;
  readonly currency: string;
}

export function formatarMoeda(centavos: number, currency = 'BRL'): string {
  if (!Number.isSafeInteger(centavos)) {
    throw new RangeError('O valor monetário precisa ser um inteiro seguro.');
  }
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency,
  }).format(centavos / 100);
}

/**
 * Converte "1.234,56" ou "1234.56" para centavos.
 *
 * A conversão é textual e termina em BigInt. Nenhum valor fracionário entra
 * no transporte, inclusive nos casos clássicos em que ponto flutuante arredonda
 * um centavo para o lado errado.
 */
export function paraCentavos(texto: string): number {
  return lerValorMonetario(texto, 'BRL').amountMinor;
}

export function lerValorMonetario(texto: string, currency: string): ValorMonetario {
  const limpo = texto.trim().replace(/[\s\u00a0]/g, '');
  if (!limpo) return { amountMinor: 0, currency };

  const separadorDecimal = limpo.includes(',')
    ? ','
    : /\.\d{1,2}$/.test(limpo) ? '.' : null;
  const partes = separadorDecimal ? limpo.split(separadorDecimal) : [limpo];
  if (partes.length > 2) throw new RangeError('Informe um valor monetário válido.');

  const inteiro = partes[0].replace(/[.,]/g, '');
  const fracao = partes[1] ?? '';
  if (!/^\d+$/.test(inteiro) || !/^\d{0,2}$/.test(fracao)) {
    throw new RangeError('Informe um valor monetário válido.');
  }

  const minor = BigInt(inteiro) * 100n + BigInt(fracao.padEnd(2, '0') || '0');
  if (minor > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new RangeError('O valor excede o limite seguro desta operação.');
  }
  return { amountMinor: Number(minor), currency };
}

/** Data civil permanece texto YYYY-MM-DD e nunca passa por conversão de fuso. */
export function normalizarDataCivil(valor: string): string {
  const encontrado = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor);
  if (!encontrado) throw new RangeError('Informe uma data no formato AAAA-MM-DD.');
  const [, ano, mes, dia] = encontrado;
  const data = new Date(Date.UTC(Number(ano), Number(mes) - 1, Number(dia)));
  if (data.toISOString().slice(0, 10) !== valor) throw new RangeError('Informe uma data válida.');
  return valor;
}

function partesNoFuso(instante: Date, timeZone: string): Record<string, number> {
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(instante);
  return Object.fromEntries(partes
    .filter((parte) => parte.type !== 'literal')
    .map((parte) => [parte.type, Number(parte.value)]));
}

/** Converte horário local usando o fuso IANA informado, não o fuso implícito do navegador. */
export function instanteDeHorarioLocal(valor: string, timeZone: string): string {
  const encontrado = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(valor);
  if (!encontrado) throw new RangeError('Informe data e horário local válidos.');
  const [, ano, mes, dia, hora, minuto, segundo = '00'] = encontrado;
  const desejado = [ano, mes, dia, hora, minuto, segundo].map(Number);
  const comoUtc = Date.UTC(desejado[0], desejado[1] - 1, desejado[2], desejado[3], desejado[4], desejado[5]);
  let instante = comoUtc;

  // Duas passagens acomodam mudanças de offset próximas à data escolhida.
  for (let tentativa = 0; tentativa < 2; tentativa += 1) {
    const partes = partesNoFuso(new Date(instante), timeZone);
    const exibidoComoUtc = Date.UTC(
      partes.year, partes.month - 1, partes.day, partes.hour, partes.minute, partes.second,
    );
    instante -= exibidoComoUtc - comoUtc;
  }

  const confirmacao = partesNoFuso(new Date(instante), timeZone);
  const atual = [confirmacao.year, confirmacao.month, confirmacao.day,
    confirmacao.hour, confirmacao.minute, confirmacao.second];
  if (!atual.every((parte, indice) => parte === desejado[indice])) {
    throw new RangeError('Esse horário não existe no fuso selecionado.');
  }
  return new Date(instante).toISOString();
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
