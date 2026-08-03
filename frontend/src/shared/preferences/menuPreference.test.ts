import { describe, expect, it } from 'vitest';
import {
  chaveDaPreferenciaDeMenu,
  lerMenuRecolhido,
  salvarMenuRecolhido,
} from './menuPreference';

describe('preferência do menu', () => {
  it('usa chave versionada por usuário sem expor o identificador', () => {
    const usuarioId = 'usuario-sintetico-123';
    const chave = chaveDaPreferenciaDeMenu(usuarioId);

    expect(chave).toMatch(/^crm-pnp:ui:menu-recolhido:v1:[a-z0-9]+$/);
    expect(chave).not.toContain(usuarioId);
    expect(chaveDaPreferenciaDeMenu('outro-usuario')).not.toBe(chave);
  });

  it('persiste somente o marcador não sensível e tolera storage bloqueado', () => {
    const usuarioId = 'usuario-sintetico-456';
    salvarMenuRecolhido(usuarioId, true);

    expect(lerMenuRecolhido(usuarioId)).toBe(true);
    expect(localStorage.getItem(chaveDaPreferenciaDeMenu(usuarioId))).toBe('1');

    const bloqueado = {
      getItem: () => { throw new Error('bloqueado'); },
      setItem: () => { throw new Error('bloqueado'); },
      removeItem: () => { throw new Error('bloqueado'); },
    } as unknown as Storage;
    expect(lerMenuRecolhido(usuarioId, bloqueado)).toBe(false);
    expect(() => salvarMenuRecolhido(usuarioId, true, bloqueado)).not.toThrow();
  });
});
