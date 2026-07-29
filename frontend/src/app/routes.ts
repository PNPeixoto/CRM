/**
 * Registro central de rotas.
 *
 * Fonte única da verdade: alimenta o roteador E a navegação lateral.
 * Adicionar uma página é adicionar uma linha aqui — nada além disso.
 *
 * status:
 *   'pronto'      — página implementada
 *   'em_producao' — arquivo existe, rota funciona, conteúdo é placeholder
 */
export type StatusPagina = 'pronto' | 'em_producao';

export interface RotaApp {
  readonly id: string;
  readonly caminho: string;
  readonly rotulo: string;
  readonly icone: string;
  readonly status: StatusPagina;
  readonly grupo: 'operacao' | 'gestao' | 'plataforma';
}

export const ROTAS: readonly RotaApp[] = [
  { id: 'dashboard',    caminho: '/dashboard',    rotulo: 'Visão geral',       icone: 'layout-dashboard', status: 'pronto',      grupo: 'operacao' },
  { id: 'inbox',        caminho: '/inbox',        rotulo: 'Caixa de entrada',  icone: 'messages-square',  status: 'pronto',      grupo: 'operacao' },
  { id: 'contacts',     caminho: '/contatos',     rotulo: 'Contatos',          icone: 'users',            status: 'em_producao', grupo: 'operacao' },
  { id: 'deals',        caminho: '/oportunidades',rotulo: 'Oportunidades',     icone: 'target',           status: 'em_producao', grupo: 'operacao' },
  { id: 'pipelines',    caminho: '/funis',        rotulo: 'Funis',             icone: 'git-branch',       status: 'em_producao', grupo: 'operacao' },
  { id: 'tasks',        caminho: '/tarefas',      rotulo: 'Tarefas',           icone: 'check-square',     status: 'em_producao', grupo: 'operacao' },
  { id: 'calendar',     caminho: '/agenda',       rotulo: 'Agenda',            icone: 'calendar',         status: 'em_producao', grupo: 'operacao' },
  { id: 'bookings',     caminho: '/reservas',     rotulo: 'Reservas',          icone: 'calendar-check',   status: 'em_producao', grupo: 'operacao' },
  { id: 'assets',       caminho: '/produtos',     rotulo: 'Produtos e ativos', icone: 'package',          status: 'em_producao', grupo: 'operacao' },
  { id: 'units',        caminho: '/unidades',     rotulo: 'Unidades',          icone: 'building-2',       status: 'em_producao', grupo: 'gestao'   },
  { id: 'teams',        caminho: '/equipes',      rotulo: 'Equipes',           icone: 'users-round',      status: 'em_producao', grupo: 'gestao'   },
  { id: 'automations',  caminho: '/automacoes',   rotulo: 'Automações',        icone: 'workflow',         status: 'em_producao', grupo: 'plataforma' },
  { id: 'integrations', caminho: '/integracoes',  rotulo: 'Integrações',       icone: 'plug',             status: 'em_producao', grupo: 'plataforma' },
  { id: 'campaigns',    caminho: '/campanhas',    rotulo: 'Campanhas',         icone: 'megaphone',        status: 'em_producao', grupo: 'gestao'   },
  { id: 'reports',      caminho: '/relatorios',   rotulo: 'Relatórios',        icone: 'bar-chart-3',      status: 'em_producao', grupo: 'gestao'   },
  { id: 'audit',        caminho: '/auditoria',    rotulo: 'Auditoria',         icone: 'scroll-text',      status: 'em_producao', grupo: 'plataforma' },
  { id: 'settings',     caminho: '/configuracoes',rotulo: 'Configurações',     icone: 'settings',         status: 'em_producao', grupo: 'plataforma' },
] as const;
