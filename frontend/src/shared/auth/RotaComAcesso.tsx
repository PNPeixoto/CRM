import type { ReactNode } from 'react';
import type { RotaResolvida } from '@/shared/tenant/resolveNavigation';
import { PaginaSemPermissao } from '@/shared/components/PaginaDeStatus';

export function RotaComAcesso({
  rota,
  children,
}: {
  readonly rota: RotaResolvida;
  readonly children: ReactNode;
}) {
  if (!rota.acesso.permitido) return <PaginaSemPermissao />;
  return children;
}
