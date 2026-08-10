import { describe, expect, it } from 'vitest';
import {
  instanteDeHorarioLocal,
  lerValorMonetario,
  normalizarDataCivil,
  paraCentavos,
} from './formato';

describe('fronteiras temporais e monetárias', () => {
  it('converte dinheiro para inteiro sem ponto flutuante', () => {
    expect(paraCentavos('0,29')).toBe(29);
    expect(paraCentavos('12.35')).toBe(1235);
    expect(paraCentavos('1.234,56')).toBe(123456);
    expect(lerValorMonetario('10,00', 'BRL')).toEqual({ amountMinor: 1000, currency: 'BRL' });
    expect(() => paraCentavos('10,999')).toThrow(/valor monetário válido/i);
  });

  it('recusa valor acima do inteiro seguro', () => {
    expect(() => paraCentavos('90071992547409,92')).toThrow(/limite seguro/i);
  });

  it('mantém data civil sem conversão de fuso', () => {
    expect(normalizarDataCivil('2026-08-08')).toBe('2026-08-08');
    expect(() => normalizarDataCivil('2026-02-30')).toThrow(/data válida/i);
  });

  it('converte o mesmo horário local com fusos explícitos distintos', () => {
    expect(instanteDeHorarioLocal('2026-08-08T12:00', 'America/Sao_Paulo'))
      .toBe('2026-08-08T15:00:00.000Z');
    expect(instanteDeHorarioLocal('2026-08-08T12:00', 'America/New_York'))
      .toBe('2026-08-08T16:00:00.000Z');
  });
});
