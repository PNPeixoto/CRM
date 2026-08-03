import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { useTenantPresentation } from './TenantPresentationContext';

export function TenantOnboardingGuard({ safePath }: { readonly safePath: string }) {
  const { apresentacao, carregando } = useTenantPresentation();
  const location = useLocation();
  const isOnboarding = location.pathname === '/primeiro-acesso';

  if (carregando || !apresentacao) {
    return (
      <div className="mx-auto flex min-h-dvh max-w-2xl items-center px-6">
        <EstadoDeConteudo
          tipo="carregando"
          titulo="Carregando apresentação…"
          className="w-full"
        />
      </div>
    );
  }

  if (!apresentacao.onboardingConcluido && !isOnboarding) {
    return <Navigate to="/primeiro-acesso" replace />;
  }

  if (apresentacao.onboardingConcluido && isOnboarding) {
    return <Navigate to={safePath} replace />;
  }

  return <Outlet />;
}
