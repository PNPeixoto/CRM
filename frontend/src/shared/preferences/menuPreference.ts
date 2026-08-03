const PREFIXO = 'crm-pnp:ui:menu-recolhido:v1';

/**
 * A chave é versionada e escopada sem gravar o identificador do usuário.
 * O hash apenas evita expor o id no storage; autorização nunca depende dele.
 */
export function chaveDaPreferenciaDeMenu(usuarioId: string): string {
  let hash = 0x811c9dc5;
  for (const caractere of usuarioId) {
    hash ^= caractere.codePointAt(0) ?? 0;
    hash = Math.imul(hash, 0x01000193);
  }
  return `${PREFIXO}:${(hash >>> 0).toString(36)}`;
}

export function lerMenuRecolhido(usuarioId: string, storage: Storage = localStorage): boolean {
  try {
    return storage.getItem(chaveDaPreferenciaDeMenu(usuarioId)) === '1';
  } catch {
    return false;
  }
}

export function salvarMenuRecolhido(
  usuarioId: string,
  recolhido: boolean,
  storage: Storage = localStorage,
): void {
  try {
    const chave = chaveDaPreferenciaDeMenu(usuarioId);
    if (recolhido) storage.setItem(chave, '1');
    else storage.removeItem(chave);
  } catch {
    // Preferência é opcional; storage bloqueado não impede navegação.
  }
}
