import { describe, expect, it, vi } from 'vitest';

describe('ambiente de teste', () => {
  it('permite controlar o relógio sem depender da máquina', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-15T12:00:00Z'));

    expect(new Date().toISOString()).toBe('2026-01-15T12:00:00.000Z');
  });

  it('bloqueia rede não simulada sem registrar query', async () => {
    const erro = await fetch('https://externo.invalid/contatos?dado=omitido')
      .catch((falha: unknown) => falha);

    expect(erro).toBeInstanceOf(Error);
    expect((erro as Error).message).toBe('Rede não simulada: GET /contatos');
  });
});
