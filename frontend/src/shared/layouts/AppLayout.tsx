import {
  BarChart3,
  Building2,
  Calendar,
  CalendarCheck,
  CheckSquare,
  GitBranch,
  LayoutDashboard,
  Megaphone,
  Menu,
  MessagesSquare,
  Package,
  PanelLeftClose,
  PanelLeftOpen,
  Plug,
  ScrollText,
  Settings,
  Users,
  UsersRound,
  Workflow,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useRef, useState, type ChangeEvent, type Ref } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import type { RotaApp } from '@/app/routes';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { MarcaFinUp } from '@/shared/components/MarcaFinUp';
import type { RotaResolvida } from '@/shared/tenant/resolveNavigation';
import { useAuth } from '@/shared/auth/AuthContext';
import {
  lerMenuRecolhido,
  salvarMenuRecolhido,
} from '@/shared/preferences/menuPreference';
import { cn } from '@/lib/utils';

const TITULO_GRUPO: Record<RotaApp['grupo'], string> = {
  operacao: 'Operação',
  gestao: 'Gestão',
  plataforma: 'Plataforma',
};

const ORDEM_GRUPOS: RotaApp['grupo'][] = ['operacao', 'gestao', 'plataforma'];

const ICONES: Record<string, LucideIcon> = {
  'layout-dashboard': LayoutDashboard,
  'messages-square': MessagesSquare,
  users: Users,
  'git-branch': GitBranch,
  'check-square': CheckSquare,
  calendar: Calendar,
  'calendar-check': CalendarCheck,
  package: Package,
  'building-2': Building2,
  'users-round': UsersRound,
  workflow: Workflow,
  plug: Plug,
  megaphone: Megaphone,
  'bar-chart-3': BarChart3,
  'scroll-text': ScrollText,
  settings: Settings,
};

export interface ContextoAutorizado {
  readonly id: string;
  readonly rotulo: string;
}

interface AppLayoutProps {
  readonly navigation: readonly RotaResolvida[];
  readonly contextosAutorizados: readonly ContextoAutorizado[];
  readonly contextoAtivoId: string;
  /** Resolve limpeza de dados e ativação no servidor antes de concluir. */
  readonly aoTrocarContexto?: (contextoId: string) => Promise<void>;
}

/** Casca responsiva derivada do mesmo registro usado pelo roteador. */
export function AppLayout({
  navigation,
  contextosAutorizados,
  contextoAtivoId,
  aoTrocarContexto,
}: AppLayoutProps) {
  const { usuario, sair } = useAuth();
  const [menuMobileAberto, setMenuMobileAberto] = useState(false);
  const [menuRecolhido, setMenuRecolhido] = useState(false);
  const [trocandoContexto, setTrocandoContexto] = useState(false);
  const [erroDeContexto, setErroDeContexto] = useState(false);
  const botaoMenuRef = useRef<HTMLButtonElement>(null);
  const primeiroLinkRef = useRef<HTMLAnchorElement>(null);
  const primeiraRotaVisivel = navigation.find((rota) => rota.visivel)?.id;

  useEffect(() => {
    if (usuario) setMenuRecolhido(lerMenuRecolhido(usuario.id));
  }, [usuario]);

  useEffect(() => {
    if (menuMobileAberto) primeiroLinkRef.current?.focus();
  }, [menuMobileAberto]);

  useEffect(() => {
    if (!menuMobileAberto) return;
    const fecharComEscape = (evento: KeyboardEvent) => {
      if (evento.key !== 'Escape') return;
      setMenuMobileAberto(false);
      botaoMenuRef.current?.focus();
    };
    document.addEventListener('keydown', fecharComEscape);
    return () => document.removeEventListener('keydown', fecharComEscape);
  }, [menuMobileAberto]);

  function alternarMenuRecolhido() {
    if (!usuario) return;
    const proximo = !menuRecolhido;
    setMenuRecolhido(proximo);
    salvarMenuRecolhido(usuario.id, proximo);
  }

  async function trocarContexto(evento: ChangeEvent<HTMLSelectElement>) {
    if (!aoTrocarContexto || evento.target.value === contextoAtivoId) return;
    setMenuMobileAberto(false);
    setErroDeContexto(false);
    setTrocandoContexto(true);
    try {
      // Enquanto a Promise não termina, o Outlet anterior fica desmontado.
      // O chamador limpa dados/cache antes de confirmar o novo contexto.
      await aoTrocarContexto(evento.target.value);
    } catch {
      setErroDeContexto(true);
    } finally {
      setTrocandoContexto(false);
    }
  }

  return (
    <div className="flex h-dvh flex-col overflow-hidden lg:flex-row">
      <a
        href="#conteudo-principal"
        className="fixed left-3 top-3 z-50 inline-flex min-h-11 -translate-y-20 items-center rounded-[var(--radius-control)] bg-[var(--surface-raised)] px-3 py-2 text-sm font-medium text-[var(--text-strong)] focus:translate-y-0"
      >
        Pular para o conteúdo
      </a>

      <header className="flex shrink-0 items-center justify-between border-b border-[var(--border-subtle)] bg-[var(--surface-raised)] px-4 py-3 lg:hidden">
        <MarcaFinUp tema="claro" />
        <button
          ref={botaoMenuRef}
          type="button"
          aria-controls="navegacao-principal"
          aria-expanded={menuMobileAberto}
          aria-label={menuMobileAberto ? 'Fechar menu' : 'Abrir menu'}
          onClick={() => setMenuMobileAberto((aberto) => !aberto)}
          className="inline-flex size-11 items-center justify-center rounded-[var(--radius-control)] border border-[var(--border-control)]"
        >
          {menuMobileAberto ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
        </button>
      </header>

      <nav
        id="navegacao-principal"
        className={cn(
          'min-h-0 shrink-0 flex-col bg-[var(--surface-shell)] text-[var(--text-on-shell)]',
          menuMobileAberto ? 'flex flex-1' : 'hidden',
          'lg:flex lg:h-dvh lg:flex-none',
          menuRecolhido ? 'lg:w-16' : 'lg:w-60',
        )}
        aria-label="Navegação principal"
      >
        <div
          className={cn(
            'hidden py-3 lg:flex',
            menuRecolhido
              ? 'flex-col items-center gap-1 px-2'
              : 'items-center justify-between px-3',
          )}
        >
          <MarcaFinUp recolhida={menuRecolhido} className={menuRecolhido ? undefined : 'px-2'} />
          <button
            type="button"
            onClick={alternarMenuRecolhido}
            aria-label={menuRecolhido ? 'Expandir menu' : 'Recolher menu'}
            className="inline-flex size-11 items-center justify-center rounded-[var(--radius-control)] hover:bg-[var(--surface-shell-hover)]"
          >
            {menuRecolhido ? (
              <PanelLeftOpen aria-hidden="true" />
            ) : (
              <PanelLeftClose aria-hidden="true" />
            )}
          </button>
        </div>

        {contextosAutorizados.length > 1 && (
          <div className="px-3 pb-4">
            <Label htmlFor="contexto-ativo" className="sr-only">
              Empresa ou unidade ativa
            </Label>
            <Select
              id="contexto-ativo"
              className={cn('w-full', menuRecolhido && 'lg:hidden')}
              value={contextoAtivoId}
              onChange={(evento) => void trocarContexto(evento)}
              disabled={trocandoContexto || !aoTrocarContexto}
            >
              {contextosAutorizados.map((contexto) => (
                <option key={contexto.id} value={contexto.id}>
                  {contexto.rotulo}
                </option>
              ))}
            </Select>
          </div>
        )}

        <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
          {ORDEM_GRUPOS.map((grupo) => {
            const rotasDoGrupo = navigation.filter(
              (rota) => rota.visivel && rota.grupo === grupo,
            );
            if (rotasDoGrupo.length === 0) return null;

            return (
              <section key={grupo} className="mb-4">
                <h2
                  className={cn(
                    'px-2 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-[var(--text-on-shell-muted)]',
                    menuRecolhido && 'lg:sr-only',
                  )}
                >
                  {TITULO_GRUPO[grupo]}
                </h2>
                <ul className="space-y-0.5">
                  {rotasDoGrupo.map((rota) => (
                    <li key={rota.id}>
                      <ItemDeNavegacao
                        rota={rota}
                        recolhido={menuRecolhido}
                        ref={rota.id === primeiraRotaVisivel ? primeiroLinkRef : undefined}
                        aoNavegar={() => setMenuMobileAberto(false)}
                      />
                    </li>
                  ))}
                </ul>
              </section>
            );
          })}
        </div>

        <div className="border-t border-[var(--surface-shell-hover)] px-3 py-3">
          <p className={cn('truncate px-2 text-sm font-medium', menuRecolhido && 'lg:sr-only')}>
            {usuario?.nomeCompleto}
          </p>
          <p
            className={cn(
              'truncate px-2 text-xs text-[var(--text-on-shell-muted)]',
              menuRecolhido && 'lg:sr-only',
            )}
          >
            {usuario?.login}
          </p>
          <button
            type="button"
            onClick={() => void sair()}
            className={cn(
              'mt-2 min-h-11 w-full rounded-[var(--radius-control)] px-2 py-1.5 text-left text-sm hover:bg-[var(--surface-shell-hover)]',
              menuRecolhido && 'lg:text-center',
            )}
            aria-label="Sair"
          >
            <span className={cn(menuRecolhido && 'lg:sr-only')}>Sair</span>
            {menuRecolhido && <span className="hidden lg:inline" aria-hidden="true">↪</span>}
          </button>
        </div>
      </nav>

      <main
        id="conteudo-principal"
        tabIndex={-1}
        className={cn(
          'min-h-0 min-w-0 flex-1 overflow-hidden',
          menuMobileAberto && 'hidden lg:block',
        )}
      >
        {trocandoContexto ? (
          <div className="p-[var(--space-page)]">
            <EstadoDeConteudo tipo="carregando" titulo="Trocando contexto…" />
          </div>
        ) : erroDeContexto ? (
          <div className="p-[var(--space-page)]">
            <EstadoDeConteudo
              tipo="erro"
              titulo="Não foi possível trocar o contexto"
              descricao="O contexto anterior foi mantido. Tente novamente."
            />
          </div>
        ) : (
          <Outlet />
        )}
      </main>
    </div>
  );
}

interface ItemDeNavegacaoProps {
  readonly rota: RotaResolvida;
  readonly recolhido: boolean;
  readonly aoNavegar: () => void;
}

function ItemDeNavegacao({
  rota,
  recolhido,
  aoNavegar,
  ref,
}: ItemDeNavegacaoProps & { readonly ref?: Ref<HTMLAnchorElement> }) {
  const Icone = ICONES[rota.icone] ?? Package;
  return (
    <NavLink
      ref={ref}
      to={rota.caminho}
      onClick={aoNavegar}
      title={recolhido ? rota.rotulo : undefined}
      className={({ isActive }) =>
        cn(
          'flex min-h-11 items-center gap-2 rounded-[var(--radius-control)] border-l-2 px-2 py-1.5 text-sm transition-colors',
          recolhido ? 'lg:justify-center' : 'justify-between',
          isActive
            ? 'border-[var(--brand-hover)] bg-[var(--surface-shell-hover)] font-semibold text-[var(--text-on-shell)]'
            : 'border-transparent hover:bg-[var(--surface-shell-hover)]',
        )
      }
    >
      <span className="flex min-w-0 items-center gap-2">
        <Icone className="size-4 shrink-0" aria-hidden="true" />
        <span className={cn('truncate', recolhido && 'lg:sr-only')}>{rota.rotulo}</span>
      </span>
      {rota.status === 'em_producao' && (
        <span
          className={cn(
            'shrink-0 rounded border border-current px-1.5 py-px text-[10px] uppercase opacity-60',
            recolhido && 'lg:sr-only',
          )}
        >
          em breve
        </span>
      )}
    </NavLink>
  );
}
