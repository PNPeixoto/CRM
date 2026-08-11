/** Modelos de tela da administração de acessos. */

/**
 * Alcances que a API aceita.
 *
 * `UNIT` existe no banco e não concede nada enquanto nenhuma tabela de domínio
 * declarar unidade — por isso não aparece aqui nem na tela. Ver ADR-0008.
 */
export const ALCANCES = ['TENANT', 'TEAM', 'OWN'] as const;

export type Alcance = (typeof ALCANCES)[number];

export const ROTULO_ALCANCE: Record<Alcance, string> = {
  TENANT: 'Toda a empresa',
  TEAM: 'A equipe dele',
  OWN: 'Só o que é dele',
};

export const DESCRICAO_ALCANCE: Record<Alcance, string> = {
  TENANT: 'Enxerga e altera qualquer registro da empresa.',
  TEAM: 'Enxerga o próprio trabalho e o de quem responde a ele.',
  OWN: 'Enxerga apenas os registros sob a responsabilidade dele.',
};

export interface Papel {
  readonly id: string;
  readonly codigo: string;
  readonly nome: string;
  readonly descricao: string | null;
  readonly sistema: boolean;
  readonly ativo: boolean;
  readonly permissoes: readonly string[];
  /** Falso quando o papel concede algo que o usuário atual não possui. */
  readonly gerenciavel: boolean;
  readonly atribuicoes: number;
}

export interface PermissaoDoCatalogo {
  readonly codigo: string;
  readonly delegavelNoTenant: boolean;
  readonly delegavelProprio: boolean;
}

export interface CatalogoDePapeis {
  readonly papeis: readonly Papel[];
  readonly permissoes: readonly PermissaoDoCatalogo[];
}

export interface Atribuicao {
  readonly id: string;
  readonly papelId: string;
  readonly papelCodigo: string;
  readonly papelNome: string;
  readonly alcance: Alcance;
}

export interface Membro {
  readonly membershipId: string;
  readonly usuarioId: string;
  readonly login: string;
  readonly nome: string;
  readonly atribuicoes: readonly Atribuicao[];
}

export interface Liderado {
  readonly usuarioId: string;
  readonly nome: string;
  readonly login: string;
}

export interface Equipe {
  readonly gestorId: string;
  readonly gestorNome: string;
  readonly liderados: readonly Liderado[];
}

/**
 * Rótulos das permissões do catálogo.
 *
 * O backend é a fonte da lista; isto só traduz. Código sem rótulo aparece como
 * o próprio código em vez de sumir — permissão invisível na tela é permissão
 * que ninguém revisa.
 */
export const ROTULO_PERMISSAO: Record<string, string> = {
  'dashboard.read': 'Ver o painel',
  'contacts.read': 'Ver contatos',
  'contacts.write': 'Criar e editar contatos',
  'deals.read': 'Ver oportunidades',
  'deals.write': 'Criar e editar oportunidades',
  'tasks.read': 'Ver tarefas',
  'tasks.write': 'Criar e editar tarefas',
  'conversations.read': 'Ver conversas',
  'conversations.write': 'Responder conversas',
  'channels.read': 'Ver canais',
  'channels.write': 'Configurar canais e credenciais',
  'automations.read': 'Ver automações',
  'automations.write': 'Criar e editar automações',
  'integrations.read': 'Ver integrações',
  'integrations.write': 'Configurar integrações',
  'reports.read': 'Ver relatórios',
  'audit.read': 'Ler a trilha de auditoria',
  'privacy.manage': 'Atender pedidos de titular (LGPD)',
  'organization.manage': 'Administrar papéis e acessos',
};

export function rotuloDaPermissao(codigo: string): string {
  return ROTULO_PERMISSAO[codigo] ?? codigo;
}
