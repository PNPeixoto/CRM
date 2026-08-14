import {
  AlertTriangle,
  ArrowRight,
  CheckSquare,
  CircleDollarSign,
  GitBranch,
  MessageSquare,
  Radio,
  TrendingUp,
  Users,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { cn } from '@/lib/utils';
import { Cartao, Carregando, Pagina } from '@/shared/components/Pagina';
import { formatarMoeda, formatarNumero } from '@/shared/formato';
import { useVisaoGeral } from '@/shared/server-state/recursos';

const ESTILO_DO_ICONE = {
  marca: 'bg-[var(--brand-soft)] text-[var(--brand)]',
  perigo: 'bg-[var(--danger-soft)] text-[var(--danger)]',
  sucesso: 'bg-[var(--success-soft)] text-[var(--success)]',
  informacao: 'bg-[var(--info-soft)] text-[var(--info)]',
} as const;

export function DashboardPage() {
  const dadosQuery = useVisaoGeral();
  const dados = dadosQuery.data;

  return (
    <Pagina titulo="Visão geral" descricao="Prioridades e desempenho da operação">
      {dadosQuery.isPending ? (
        <Carregando />
      ) : dadosQuery.isError || !dados ? (
        <EstadoDeConteudo
          tipo="erro"
          titulo="Não foi possível carregar os indicadores"
          descricao="Atualize a página para tentar novamente."
        />
      ) : (
        <div className="space-y-6">
          <section aria-label="Indicadores prioritários">
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <IndicadorPrincipal
                icone={MessageSquare}
                tom="informacao"
                rotulo="Conversas abertas"
                valor={formatarNumero(dados.conversasAbertas)}
                apoio={`${formatarNumero(dados.conversasAguardando)} aguardando retorno`}
                destino="/inbox"
              />
              <IndicadorPrincipal
                icone={AlertTriangle}
                tom="perigo"
                rotulo="Tarefas atrasadas"
                valor={formatarNumero(dados.tarefasAtrasadas)}
                apoio={`${formatarNumero(dados.tarefasAbertas)} tarefas abertas`}
                destino="/tarefas"
              />
              <IndicadorPrincipal
                icone={CircleDollarSign}
                tom="marca"
                rotulo="Pipeline em aberto"
                valor={formatarMoeda(dados.valorAbertoCentavos)}
                apoio={`${formatarNumero(dados.oportunidadesAbertas)} oportunidades`}
                destino="/funis"
              />
              <IndicadorPrincipal
                icone={TrendingUp}
                tom="sucesso"
                rotulo="Conversão"
                valor={`${String(dados.taxaDeConversaoPercentual).replace('.', ',')}%`}
                apoio={`${formatarNumero(dados.oportunidadesGanhas)} oportunidades ganhas`}
              />
            </div>
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <SecaoDeResumo
              titulo="Atendimento e rotina"
              descricao="Volume que pede acompanhamento hoje"
            >
              <LinhaDeResumo
                icone={MessageSquare}
                rotulo="Conversas abertas"
                valor={formatarNumero(dados.conversasAbertas)}
                destino="/inbox"
              />
              <LinhaDeResumo
                icone={Radio}
                rotulo="Mensagens recebidas hoje"
                valor={formatarNumero(dados.mensagensRecebidasHoje)}
              />
              <LinhaDeResumo
                icone={CheckSquare}
                rotulo="Tarefas em aberto"
                valor={formatarNumero(dados.tarefasAbertas)}
                destino="/tarefas"
              />
              <LinhaDeResumo
                icone={AlertTriangle}
                rotulo="Tarefas atrasadas"
                valor={formatarNumero(dados.tarefasAtrasadas)}
                destino="/tarefas"
                alerta={dados.tarefasAtrasadas > 0}
              />
            </SecaoDeResumo>

            <SecaoDeResumo
              titulo="Resumo comercial"
              descricao="Posição atual do funil"
            >
              <LinhaDeResumo
                icone={GitBranch}
                rotulo="Oportunidades em aberto"
                valor={formatarNumero(dados.oportunidadesAbertas)}
                destino="/funis"
              />
              <LinhaDeResumo
                icone={CircleDollarSign}
                rotulo="Valor em aberto"
                valor={formatarMoeda(dados.valorAbertoCentavos)}
                destino="/funis"
              />
              <LinhaDeResumo
                icone={TrendingUp}
                rotulo="Receita ganha"
                valor={formatarMoeda(dados.valorGanhoCentavos)}
              />
              <LinhaDeResumo
                icone={TrendingUp}
                rotulo="Ganhas e perdidas"
                valor={`${formatarNumero(dados.oportunidadesGanhas)} / ${formatarNumero(dados.oportunidadesPerdidas)}`}
              />
            </SecaoDeResumo>
          </div>

          <section
            aria-labelledby="base-ativa"
            className="border-y border-[var(--border-subtle)] bg-[var(--surface-raised)] px-4 py-4"
          >
            <h2 id="base-ativa" className="text-sm font-semibold">
              Base ativa
            </h2>
            <div className="mt-3 grid gap-4 sm:grid-cols-3">
              <ResumoDaBase
                icone={Users}
                rotulo="Contatos cadastrados"
                valor={formatarNumero(dados.totalDeContatos)}
                destino="/contatos"
              />
              <ResumoDaBase
                icone={Radio}
                rotulo="Canais ativos"
                valor={formatarNumero(dados.canaisAtivos)}
              />
              <ResumoDaBase
                icone={MessageSquare}
                rotulo="Mensagens hoje"
                valor={formatarNumero(dados.mensagensRecebidasHoje)}
              />
            </div>
          </section>
        </div>
      )}
    </Pagina>
  );
}

function IndicadorPrincipal({
  icone: Icone,
  tom,
  rotulo,
  valor,
  apoio,
  destino,
}: {
  readonly icone: LucideIcon;
  readonly tom: keyof typeof ESTILO_DO_ICONE;
  readonly rotulo: string;
  readonly valor: string;
  readonly apoio: string;
  readonly destino?: string;
}) {
  const conteudo = (
    <Cartao className="h-full min-w-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-xs font-medium text-[var(--text-muted)]">{rotulo}</p>
          <p className="mt-1 truncate text-2xl font-semibold tabular-nums">{valor}</p>
        </div>
        <span
          className={cn(
            'flex size-9 shrink-0 items-center justify-center rounded-[var(--radius-control)]',
            ESTILO_DO_ICONE[tom],
          )}
        >
          <Icone className="size-4" aria-hidden="true" />
        </span>
      </div>
      <p className="mt-3 text-xs text-[var(--text-muted)]">{apoio}</p>
    </Cartao>
  );

  return destino ? (
    <Link
      to={destino}
      className="block rounded-[var(--radius-surface)] transition-colors hover:outline hover:outline-1 hover:outline-[var(--brand)]"
    >
      {conteudo}
    </Link>
  ) : conteudo;
}

function SecaoDeResumo({
  titulo,
  descricao,
  children,
}: {
  readonly titulo: string;
  readonly descricao: string;
  readonly children: ReactNode;
}) {
  const id = `resumo-${titulo.toLocaleLowerCase('pt-BR').replaceAll(' ', '-')}`;
  return (
    <section aria-labelledby={id} className="border-y border-[var(--border-subtle)]">
      <header className="bg-[var(--surface-raised)] px-4 py-3">
        <h2 id={id} className="text-sm font-semibold">{titulo}</h2>
        <p className="mt-0.5 text-xs text-[var(--text-muted)]">{descricao}</p>
      </header>
      <div>{children}</div>
    </section>
  );
}

function LinhaDeResumo({
  icone: Icone,
  rotulo,
  valor,
  destino,
  alerta = false,
}: {
  readonly icone: LucideIcon;
  readonly rotulo: string;
  readonly valor: string;
  readonly destino?: string;
  readonly alerta?: boolean;
}) {
  const conteudo = (
    <>
      <span className="flex min-w-0 items-center gap-2.5">
        <Icone className="size-4 shrink-0 text-[var(--text-muted)]" aria-hidden="true" />
        <span className="truncate text-sm text-[var(--text-muted)]">{rotulo}</span>
      </span>
      <span className="flex shrink-0 items-center gap-2">
        <strong
          className={cn(
            'text-sm font-semibold tabular-nums',
            alerta && 'text-[var(--danger)]',
          )}
        >
          {valor}
        </strong>
        {destino && <ArrowRight className="size-4 text-[var(--text-muted)]" aria-hidden="true" />}
      </span>
    </>
  );
  const classe = 'flex min-h-11 items-center justify-between gap-4 border-t border-[var(--border-subtle)] px-4 py-2';

  return destino ? (
    <Link to={destino} className={`${classe} hover:bg-[var(--brand-soft)]`}>
      {conteudo}
    </Link>
  ) : (
    <div className={classe}>{conteudo}</div>
  );
}

function ResumoDaBase({
  icone: Icone,
  rotulo,
  valor,
  destino,
}: {
  readonly icone: LucideIcon;
  readonly rotulo: string;
  readonly valor: string;
  readonly destino?: string;
}) {
  const conteudo = (
    <div className="flex min-w-0 items-center gap-3">
      <Icone className="size-4 shrink-0 text-[var(--brand)]" aria-hidden="true" />
      <div className="min-w-0">
        <span className="block truncate text-xs text-[var(--text-muted)]">{rotulo}</span>
        <span className="block text-lg font-semibold tabular-nums">{valor}</span>
      </div>
    </div>
  );

  return destino ? <Link to={destino}>{conteudo}</Link> : conteudo;
}
