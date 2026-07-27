import { NavLink, Outlet } from 'react-router-dom';
import { ROTAS } from '@/app/routes';

/**
 * Casca da aplicação. A navegação é derivada de ROTAS — nunca escrita à mão,
 * senão o menu e o roteador saem de sincronia sem ninguém perceber.
 */
export function AppLayout() {
  return (
    <div className="flex min-h-screen">
      <nav className="w-60 shrink-0 border-r p-3">
        <ul className="space-y-1">
          {ROTAS.map((rota) => (
            <li key={rota.id}>
              <NavLink
                to={rota.caminho}
                className={({ isActive }) =>
                  `flex items-center justify-between rounded px-3 py-2 text-sm ${
                    isActive ? 'font-medium' : ''
                  }`
                }
              >
                <span>{rota.rotulo}</span>
                {rota.status === 'em_producao' && (
                  <span className="rounded border px-1.5 py-0.5 text-[10px] uppercase">
                    em breve
                  </span>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
