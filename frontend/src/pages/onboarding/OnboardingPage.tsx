import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { caminhoInicialSeguro, resolveNavigation } from '@/shared/tenant/resolveNavigation';
import { useTenantPresentation } from '@/shared/tenant/TenantPresentationContext';
import type { SegmentoDeNegocio } from '@/shared/tenant/tipos';
import { ROTAS } from '@/app/routes';

const SEGMENTOS: readonly { id: SegmentoDeNegocio; titulo: string; descricao: string }[] = [
  { id: 'GENERAL_SERVICES', titulo: 'Serviços gerais', descricao: 'Contatos e oportunidades.' },
  { id: 'CONFECTIONERY', titulo: 'Confeitaria', descricao: 'Clientes, encomendas e produção.' },
  { id: 'RESTAURANT', titulo: 'Restaurante', descricao: 'Pedidos, cardápio e reservas.' },
  { id: 'RENTAL', titulo: 'Locação', descricao: 'Orçamentos, reservas e ativos.' },
];

export function OnboardingPage() {
  const { escolherSegmento } = useTenantPresentation();
  const [salvando, setSalvando] = useState<SegmentoDeNegocio | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const navigate = useNavigate();

  async function escolher(segmento: SegmentoDeNegocio) {
    setSalvando(segmento);
    setErro(null);
    try {
      const atualizada = await escolherSegmento(segmento);
      navigate(caminhoInicialSeguro(resolveNavigation(ROTAS, atualizada)), { replace: true });
    } catch {
      setErro('Não foi possível salvar o segmento. Tente novamente.');
    } finally {
      setSalvando(null);
    }
  }

  return (
    <main className="min-h-dvh bg-[var(--surface-base)] px-6 py-12">
      <section className="mx-auto max-w-3xl">
        <p className="text-sm font-semibold text-[var(--brand)]">Primeiro acesso</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Como sua empresa trabalha?</h1>
        <p className="mt-3 max-w-2xl text-sm text-[var(--text-muted)]">
          Isso prepara os nomes, a navegação e o primeiro funil. O CRM continua flexível e você
          poderá adaptar o processo depois.
        </p>

        {erro && <p className="mt-5 text-sm text-[var(--danger)]" role="alert">{erro}</p>}

        <div className="mt-8 grid gap-4 sm:grid-cols-2">
          {SEGMENTOS.map((segmento) => (
            <button
              key={segmento.id}
              type="button"
              disabled={salvando !== null}
              onClick={() => void escolher(segmento.id)}
              className="rounded-[var(--radius-surface)] border bg-[var(--surface-raised)] p-5 text-left transition hover:border-[var(--brand)] disabled:opacity-60"
            >
              <span className="block font-semibold">{segmento.titulo}</span>
              <span className="mt-1 block text-sm text-[var(--text-muted)]">
                {salvando === segmento.id ? 'Preparando seu CRM…' : segmento.descricao}
              </span>
            </button>
          ))}
        </div>
      </section>
    </main>
  );
}
