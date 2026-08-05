import { Suspense, useMemo } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { ROTAS } from './routes';
import { AppLayout } from '@/shared/layouts/AppLayout';
import { LoginPage } from '@/pages/login';
import { OnboardingPage } from '@/pages/onboarding';
import { AuthProvider, useAuth } from '@/shared/auth/AuthContext';
import { RotaProtegida } from '@/shared/auth/RotaProtegida';
import { RotaComAcesso } from '@/shared/auth/RotaComAcesso';
import { PaginaNaoEncontrada, PaginaSemPermissao } from '@/shared/components/PaginaDeStatus';
import { TenantPresentationProvider, useTenantPresentation } from '@/shared/tenant/TenantPresentationContext';
import { TenantOnboardingGuard } from '@/shared/tenant/TenantOnboardingGuard';
import { caminhoInicialSeguro, resolveNavigation } from '@/shared/tenant/resolveNavigation';

export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <TenantPresentationProvider>
          <Suspense
            fallback={
              <div className="p-[var(--space-page)]">
                <EstadoDeConteudo tipo="carregando" titulo="Carregando módulo…" />
              </div>
            }
          >
            <ApplicationRoutes />
          </Suspense>
        </TenantPresentationProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

function ApplicationRoutes() {
  const { usuario } = useAuth();
  const { apresentacao, permissoes } = useTenantPresentation();
  const navigation = useMemo(
    () => resolveNavigation(ROTAS, apresentacao, {
      permissoes: permissoes ? Object.keys(permissoes) : undefined,
    }),
    [apresentacao, permissoes],
  );
  const safePath = caminhoInicialSeguro(navigation);
  const contextosAutorizados = usuario
    ? [{ id: usuario.tenantId, rotulo: 'Empresa atual' }]
    : [];

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RotaProtegida />}>
        <Route element={<TenantOnboardingGuard safePath={safePath} />}>
          <Route path="/primeiro-acesso" element={<OnboardingPage />} />
          <Route
            element={
              <AppLayout
                navigation={navigation}
                contextosAutorizados={contextosAutorizados}
                contextoAtivoId={usuario?.tenantId ?? ''}
              />
            }
          >
            {navigation.map((rota) => {
              const { id, caminho } = rota;
              const Page = rota.componente;
              return (
                <Route
                  key={id}
                  path={caminho}
                  element={
                    <RotaComAcesso rota={rota}>
                      <Page />
                    </RotaComAcesso>
                  }
                />
              );
            })}
            <Route path="/oportunidades" element={<Navigate to="/funis" replace />} />
            <Route path="/acesso-negado" element={<PaginaSemPermissao />} />
            <Route path="*" element={<PaginaNaoEncontrada />} />
          </Route>
          <Route path="/" element={<Navigate to={safePath} replace />} />
        </Route>
      </Route>
    </Routes>
  );
}
