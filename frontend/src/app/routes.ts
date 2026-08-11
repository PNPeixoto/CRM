import { lazy, type ComponentType, type LazyExoticComponent } from 'react';

export type StatusPagina = 'pronto' | 'em_producao';

export interface RotaApp {
  readonly id: string;
  readonly caminho: string;
  readonly rotulo: string;
  readonly icone: string;
  /** Implementada significa renderizável; não equivale a gate de produção. */
  readonly status: StatusPagina;
  readonly grupo: 'operacao' | 'gestao' | 'plataforma';
  /** Permissão atômica de leitura exigida pelo endpoint principal da página. */
  readonly permissaoNecessaria?: string;
  readonly componente: LazyExoticComponent<ComponentType>;
}

/**
 * Fonte única do build, do roteador e da navegação. Uma página nova exige uma
 * entrada aqui; apresentação e autorização apenas filtram este conjunto local.
 */
export const ROTAS: readonly RotaApp[] = [
  {
    id: 'dashboard', caminho: '/dashboard', rotulo: 'Visão geral',
    icone: 'layout-dashboard', status: 'pronto', grupo: 'operacao',
    permissaoNecessaria: 'reports.read',
    componente: lazy(() => import('@/pages/dashboard').then((m) => ({ default: m.DashboardPage }))),
  },
  {
    id: 'inbox', caminho: '/inbox', rotulo: 'Caixa de entrada',
    icone: 'messages-square', status: 'pronto', grupo: 'operacao',
    permissaoNecessaria: 'conversations.read',
    componente: lazy(() => import('@/pages/inbox').then((m) => ({ default: m.InboxPage }))),
  },
  {
    id: 'contacts', caminho: '/contatos', rotulo: 'Contatos',
    icone: 'users', status: 'pronto', grupo: 'operacao',
    permissaoNecessaria: 'contacts.read',
    componente: lazy(() => import('@/pages/contacts').then((m) => ({ default: m.ContactsPage }))),
  },
  {
    id: 'pipelines', caminho: '/funis', rotulo: 'Oportunidades',
    icone: 'git-branch', status: 'pronto', grupo: 'operacao',
    permissaoNecessaria: 'deals.read',
    componente: lazy(() => import('@/pages/pipelines').then((m) => ({ default: m.PipelinesPage }))),
  },
  {
    id: 'tasks', caminho: '/tarefas', rotulo: 'Tarefas',
    icone: 'check-square', status: 'pronto', grupo: 'operacao',
    permissaoNecessaria: 'tasks.read',
    componente: lazy(() => import('@/pages/tasks').then((m) => ({ default: m.TasksPage }))),
  },
  {
    id: 'calendar', caminho: '/agenda', rotulo: 'Agenda',
    icone: 'calendar', status: 'em_producao', grupo: 'operacao',
    componente: lazy(() => import('@/pages/calendar').then((m) => ({ default: m.CalendarPage }))),
  },
  {
    id: 'bookings', caminho: '/reservas', rotulo: 'Reservas',
    icone: 'calendar-check', status: 'em_producao', grupo: 'operacao',
    componente: lazy(() => import('@/pages/bookings').then((m) => ({ default: m.BookingsPage }))),
  },
  {
    id: 'assets', caminho: '/produtos', rotulo: 'Produtos e ativos',
    icone: 'package', status: 'em_producao', grupo: 'operacao',
    componente: lazy(() => import('@/pages/assets').then((m) => ({ default: m.AssetsPage }))),
  },
  {
    id: 'units', caminho: '/unidades', rotulo: 'Unidades',
    icone: 'building-2', status: 'em_producao', grupo: 'gestao',
    componente: lazy(() => import('@/pages/units').then((m) => ({ default: m.UnitsPage }))),
  },
  {
    id: 'teams', caminho: '/acessos', rotulo: 'Acessos',
    icone: 'users-round', status: 'pronto', grupo: 'gestao',
    componente: lazy(() => import('@/pages/teams').then((m) => ({ default: m.TeamsPage }))),
  },
  {
    id: 'automations', caminho: '/automacoes', rotulo: 'Automações',
    icone: 'workflow', status: 'em_producao', grupo: 'plataforma',
    componente: lazy(() => import('@/pages/automations').then((m) => ({ default: m.AutomationsPage }))),
  },
  {
    id: 'integrations', caminho: '/integracoes', rotulo: 'Canais e números',
    icone: 'plug', status: 'pronto', grupo: 'plataforma',
    permissaoNecessaria: 'channels.read',
    componente: lazy(() => import('@/pages/integrations').then((m) => ({ default: m.IntegrationsPage }))),
  },
  {
    id: 'campaigns', caminho: '/campanhas', rotulo: 'Campanhas',
    icone: 'megaphone', status: 'em_producao', grupo: 'gestao',
    componente: lazy(() => import('@/pages/campaigns').then((m) => ({ default: m.CampaignsPage }))),
  },
  {
    id: 'reports', caminho: '/relatorios', rotulo: 'Relatórios',
    icone: 'bar-chart-3', status: 'pronto', grupo: 'gestao',
    permissaoNecessaria: 'reports.read',
    componente: lazy(() => import('@/pages/reports').then((m) => ({ default: m.ReportsPage }))),
  },
  {
    id: 'audit', caminho: '/auditoria', rotulo: 'Auditoria',
    icone: 'scroll-text', status: 'pronto', grupo: 'plataforma',
    permissaoNecessaria: 'audit.read',
    componente: lazy(() => import('@/pages/audit').then((m) => ({ default: m.AuditPage }))),
  },
  {
    id: 'settings', caminho: '/configuracoes', rotulo: 'Configurações',
    icone: 'settings', status: 'em_producao', grupo: 'plataforma',
    componente: lazy(() => import('@/pages/settings').then((m) => ({ default: m.SettingsPage }))),
  },
] as const;
