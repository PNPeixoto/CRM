import { lazy, Suspense, type ComponentType, type LazyExoticComponent } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ROTAS } from './routes';
import { AppLayout } from '@/shared/layouts/AppLayout';
import { LoginPage } from '@/pages/login';
import { AuthProvider } from '@/shared/auth/AuthContext';
import { RotaProtegida } from '@/shared/auth/RotaProtegida';

/**
 * Mapa explícito rota -> página.
 * Proposital: um id sem página correspondente quebra o build, não a produção.
 */
const PAGINAS: Record<string, LazyExoticComponent<ComponentType>> = {
  dashboard: lazy(() => import('@/pages/dashboard').then((m) => ({ default: m.DashboardPage }))),
  inbox: lazy(() => import('@/pages/inbox').then((m) => ({ default: m.InboxPage }))),
  contacts: lazy(() => import('@/pages/contacts').then((m) => ({ default: m.ContactsPage }))),
  deals: lazy(() => import('@/pages/deals').then((m) => ({ default: m.DealsPage }))),
  pipelines: lazy(() => import('@/pages/pipelines').then((m) => ({ default: m.PipelinesPage }))),
  tasks: lazy(() => import('@/pages/tasks').then((m) => ({ default: m.TasksPage }))),
  calendar: lazy(() => import('@/pages/calendar').then((m) => ({ default: m.CalendarPage }))),
  bookings: lazy(() => import('@/pages/bookings').then((m) => ({ default: m.BookingsPage }))),
  assets: lazy(() => import('@/pages/assets').then((m) => ({ default: m.AssetsPage }))),
  units: lazy(() => import('@/pages/units').then((m) => ({ default: m.UnitsPage }))),
  teams: lazy(() => import('@/pages/teams').then((m) => ({ default: m.TeamsPage }))),
  automations: lazy(() => import('@/pages/automations').then((m) => ({ default: m.AutomationsPage }))),
  integrations: lazy(() => import('@/pages/integrations').then((m) => ({ default: m.IntegrationsPage }))),
  campaigns: lazy(() => import('@/pages/campaigns').then((m) => ({ default: m.CampaignsPage }))),
  reports: lazy(() => import('@/pages/reports').then((m) => ({ default: m.ReportsPage }))),
  audit: lazy(() => import('@/pages/audit').then((m) => ({ default: m.AuditPage }))),
  settings: lazy(() => import('@/pages/settings').then((m) => ({ default: m.SettingsPage }))),
};

export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<div className="p-6 text-sm">Carregando…</div>}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            {/*
              Toda rota da aplicação nasce dentro da RotaProtegida. Deixar a
              proteção para ser adicionada rota a rota garante que uma página
              nova entre desprotegida — e ninguém percebe, porque ela funciona.
            */}
            <Route element={<RotaProtegida />}>
              <Route element={<AppLayout />}>
                {ROTAS.map(({ id, caminho }) => {
                  const Pagina = PAGINAS[id];
                  return <Route key={id} path={caminho} element={<Pagina />} />;
                })}
                {/*
                  Rota legada, fora de ROTAS de propósito: existe só para não
                  quebrar link salvo de quando /oportunidades era uma página
                  própria. Não deve aparecer na navegação.
                */}
                <Route path="/oportunidades" element={<PAGINAS.deals />} />
              </Route>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Route>
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}
