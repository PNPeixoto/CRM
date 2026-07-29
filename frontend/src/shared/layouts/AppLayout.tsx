import { NavLink, Outlet } from 'react-router-dom';
import { ROTAS, type RotaApp } from '@/app/routes';
import { useAuth } from '@/shared/auth/AuthContext';
import { cn } from '@/lib/utils';

const TITULO_GRUPO: Record<RotaApp['grupo'], string> = {
  operacao: 'Operação',
  gestao: 'Gestão',
  plataforma: 'Plataforma',
};

const ORDEM_GRUPOS: RotaApp['grupo'][] = ['operacao', 'gestao', 'plataforma'];

/**
 * Casca da aplicação: shell de navegação escuro sobre canvas claro, que é a
 * direção fechada em 03-decisoes.md.
 *
 * A navegação é derivada de ROTAS — nunca escrita à mão, senão o menu e o
 * roteador saem de sincronia sem ninguém perceber.
 */
export function AppLayout() {
  const { usuario, sair } = useAuth();

  return (
    <div className="flex h-dvh overflow-hidden">
      <nav
        className="flex w-60 shrink-0 flex-col"
        style={{ backgroundColor: 'var(--surface-shell)', color: 'var(--text-on-shell)' }}
        aria-label="Navegação principal"
      >
        <div className="px-5 py-5 text-base font-semibold tracking-tight">CRM PNP</div>

        {/* min-h-0 é obrigatório: sem ele, um filho com overflow dentro de um
            flex container não encolhe, e a rolagem vaza para a página toda. */}
        <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
          {ORDEM_GRUPOS.map((grupo) => (
            <section key={grupo} className="mb-4">
              <h2
                className="px-2 pb-1.5 text-[11px] font-semibold uppercase tracking-wider"
                style={{ color: 'var(--text-on-shell-muted)' }}
              >
                {TITULO_GRUPO[grupo]}
              </h2>
              <ul className="space-y-0.5">
                {ROTAS.filter((rota) => rota.grupo === grupo).map((rota) => (
                  <li key={rota.id}>
                    <ItemDeNavegacao rota={rota} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        <div
          className="border-t px-3 py-3"
          style={{ borderColor: 'var(--surface-shell-hover)' }}
        >
          <p className="truncate px-2 text-sm font-medium">{usuario?.nomeCompleto}</p>
          <p className="truncate px-2 text-xs" style={{ color: 'var(--text-on-shell-muted)' }}>
            {usuario?.login}
          </p>
          <button
            type="button"
            onClick={() => void sair()}
            className="mt-2 w-full rounded-[var(--radius-control)] px-2 py-1.5 text-left text-sm hover:bg-[var(--surface-shell-hover)]"
          >
            Sair
          </button>
        </div>
      </nav>

      <main className="min-w-0 flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}

function ItemDeNavegacao({ rota }: { readonly rota: RotaApp }) {
  return (
    <NavLink
      to={rota.caminho}
      className={({ isActive }) =>
        cn(
          'flex items-center justify-between gap-2 rounded-[var(--radius-control)] px-2 py-1.5 text-sm transition-colors',
          isActive
            ? 'bg-[var(--brand)] font-medium text-[var(--text-on-brand)]'
            : 'hover:bg-[var(--surface-shell-hover)]',
        )
      }
    >
      <span className="truncate">{rota.rotulo}</span>
      {rota.status === 'em_producao' && (
        // Texto, não só cor: o estado precisa ser legível por quem não
        // distingue as cores e por leitor de tela.
        <span
          className="shrink-0 rounded border px-1.5 py-px text-[10px] uppercase"
          style={{ borderColor: 'currentColor', opacity: 0.6 }}
        >
          em breve
        </span>
      )}
    </NavLink>
  );
}
