interface MovimentoDoKanban {
  readonly oportunidadeId: string;
  readonly etapaId: string;
}

export function resolverMovimentoDoKanban(
  oportunidadeId: string,
  etapaAtualId: string,
  etapaDestinoId: string | null,
): MovimentoDoKanban | null {
  if (!etapaDestinoId || etapaDestinoId === etapaAtualId) return null;
  return { oportunidadeId, etapaId: etapaDestinoId };
}
