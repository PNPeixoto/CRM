interface ResponsavelIdentificavel {
  readonly responsavelLogin: string | null;
  readonly responsavelNome: string | null;
}

/**
 * Exibe uma identidade humana sem esconder o login, que continua sendo a
 * referência inequívoca quando duas pessoas têm o mesmo nome.
 */
export function formatarResponsavel(registro: ResponsavelIdentificavel): string {
  const { responsavelLogin: login, responsavelNome: nome } = registro;

  if (nome && login) return `${nome} (@${login})`;
  if (nome) return nome;
  if (login) return `@${login}`;
  return 'Sem responsável';
}
