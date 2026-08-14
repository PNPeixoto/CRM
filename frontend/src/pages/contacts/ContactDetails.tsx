import type { ReactNode } from 'react';
import {
  ArrowLeft,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  CheckCircle2,
  Circle,
  Mail,
  MessageSquare,
  Phone,
  UserRound,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { ApiError } from '@/lib/api';
import { Carregando, Pagina } from '@/shared/components/Pagina';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Oportunidade, StatusOportunidade, Tarefa } from '@/shared/crm/tipos';
import {
  formatarData,
  formatarDataCivil,
  formatarDataHora,
  formatarMoeda,
} from '@/shared/formato';
import {
  useAlternarConclusaoDeTarefa,
  useContato,
  useOportunidadesDoContato,
  useTarefas,
} from '@/shared/server-state/recursos';

export function FichaDoContato({
  contatoId,
  aoVoltar,
}: {
  readonly contatoId: string;
  readonly aoVoltar: () => void;
}) {
  const contatoQuery = useContato(contatoId);
  const oportunidadesQuery = useOportunidadesDoContato(contatoId);
  const tarefasQuery = useTarefas(false, contatoId);
  const alternarConclusao = useAlternarConclusaoDeTarefa();
  const contato = contatoQuery.data;
  const oportunidades = oportunidadesQuery.data ?? [];
  const tarefas = tarefasQuery.data ?? [];
  const oportunidadesAbertas = oportunidades.filter((item) => item.status === 'OPEN');
  const valorEmAberto = oportunidadesAbertas.reduce(
    (total, item) => total + item.valorCentavos,
    0,
  );

  const voltar = (
    <Button
      variant="fantasma"
      size="icone"
      onClick={aoVoltar}
      aria-label="Voltar para contatos"
      title="Voltar para contatos"
    >
      <ArrowLeft aria-hidden="true" />
    </Button>
  );

  if (contatoQuery.isPending) {
    return (
      <Pagina titulo="Contato" descricao="Carregando ficha" acoes={voltar}>
        <Carregando />
      </Pagina>
    );
  }

  if (!contato || contatoQuery.isError) {
    const semAcesso = contatoQuery.error instanceof ApiError
      && contatoQuery.error.kind === 'forbidden';
    return (
      <Pagina titulo="Contato" descricao="Ficha indisponível" acoes={voltar}>
        <AlertaErro>
          {semAcesso
            ? 'Você não tem permissão para ver este contato.'
            : 'Não foi possível carregar o contato.'}
        </AlertaErro>
      </Pagina>
    );
  }

  return (
    <Pagina
      titulo={contato.nome}
      descricao={contato.empresa ?? 'Contato sem empresa informada'}
      acoes={
        <div className="flex items-center gap-1">
          {voltar}
          <Button asChild variant="secundario" size="icone">
            <Link to="/inbox" aria-label="Abrir caixa de entrada" title="Abrir caixa de entrada">
              <MessageSquare aria-hidden="true" />
            </Link>
          </Button>
        </div>
      }
    >
      <div className="space-y-6">
        <section
          className="grid gap-5 border-b pb-6 md:grid-cols-[auto_minmax(0,1fr)]"
          style={{ borderColor: 'var(--border-subtle)' }}
          aria-label="Dados do contato"
        >
          <div
            className="flex size-14 items-center justify-center rounded-full text-lg font-semibold"
            style={{ backgroundColor: 'var(--brand-soft)', color: 'var(--brand)' }}
            aria-hidden="true"
          >
            {iniciaisDe(contato.nome)}
          </div>
          <div className="grid min-w-0 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <DadoDoContato icone={<Mail />} rotulo="E-mail">
              {contato.email ? <a href={`mailto:${contato.email}`}>{contato.email}</a> : 'Não informado'}
            </DadoDoContato>
            <DadoDoContato icone={<Phone />} rotulo="Telefone">
              {contato.telefone ? <a href={`tel:${contato.telefone}`}>{contato.telefone}</a> : 'Não informado'}
            </DadoDoContato>
            <DadoDoContato icone={<UserRound />} rotulo="Responsável">
              {formatarResponsavel(contato)}
            </DadoDoContato>
            <DadoDoContato icone={<Building2 />} rotulo="Cadastro">
              {formatarData(contato.criadoEm)}
            </DadoDoContato>
          </div>
          {contato.observacoes && (
            <p className="text-sm leading-6 md:col-start-2" style={{ color: 'var(--text-muted)' }}>
              {contato.observacoes}
            </p>
          )}
        </section>

        <div className="grid gap-8 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
          <section aria-labelledby="titulo-oportunidades">
            <CabecalhoDeSecao
              id="titulo-oportunidades"
              icone={<BriefcaseBusiness />}
              titulo="Oportunidades"
              destino="/funis"
              acao="Ver funil"
            />
            <div
              className="mb-4 grid grid-cols-3 border-y py-4"
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              <Indicador rotulo="Relacionadas" valor={String(oportunidades.length)} />
              <Indicador rotulo="Em aberto" valor={String(oportunidadesAbertas.length)} />
              <Indicador rotulo="Valor aberto" valor={formatarMoeda(valorEmAberto)} />
            </div>
            {oportunidadesQuery.isPending ? (
              <Carregando />
            ) : oportunidadesQuery.isError ? (
              <MensagemDeSecao texto="Não foi possível carregar as oportunidades deste contato." />
            ) : oportunidades.length === 0 ? (
              <MensagemDeSecao texto="Nenhuma oportunidade relacionada." />
            ) : (
              <div className="space-y-2">
                {oportunidades.map((oportunidade) => (
                  <OportunidadeDoContato key={oportunidade.id} oportunidade={oportunidade} />
                ))}
              </div>
            )}
          </section>

          <section
            className="xl:border-l xl:pl-8"
            style={{ borderColor: 'var(--border-subtle)' }}
            aria-labelledby="titulo-atividades"
          >
            <CabecalhoDeSecao
              id="titulo-atividades"
              icone={<CalendarDays />}
              titulo="Atividades"
              destino="/agenda"
              acao="Ver agenda"
            />
            {alternarConclusao.isError && (
              <AlertaErro className="mb-3">Não foi possível atualizar a atividade.</AlertaErro>
            )}
            {tarefasQuery.isPending ? (
              <Carregando />
            ) : tarefasQuery.isError ? (
              <MensagemDeSecao texto="Não foi possível carregar as atividades deste contato." />
            ) : tarefas.length === 0 ? (
              <MensagemDeSecao texto="Nenhuma atividade relacionada." />
            ) : (
              <div className="divide-y" style={{ borderColor: 'var(--border-subtle)' }}>
                {tarefas.map((tarefa) => (
                  <AtividadeDoContato
                    key={tarefa.id}
                    tarefa={tarefa}
                    desabilitada={alternarConclusao.isPending}
                    aoAlternar={() => alternarConclusao.mutate(tarefa.id)}
                  />
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </Pagina>
  );
}

function DadoDoContato({
  icone,
  rotulo,
  children,
}: {
  readonly icone: ReactNode;
  readonly rotulo: string;
  readonly children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      <div className="mb-1 flex items-center gap-1.5 text-xs" style={{ color: 'var(--text-muted)' }}>
        <span className="[&_svg]:size-3.5" aria-hidden="true">{icone}</span>
        <span>{rotulo}</span>
      </div>
      <div className="truncate text-sm font-medium text-[var(--text-strong)]">{children}</div>
    </div>
  );
}

function CabecalhoDeSecao({
  id,
  icone,
  titulo,
  destino,
  acao,
}: {
  readonly id: string;
  readonly icone: ReactNode;
  readonly titulo: string;
  readonly destino: string;
  readonly acao: string;
}) {
  return (
    <div className="mb-3 flex min-h-11 items-center justify-between gap-3">
      <h2 id={id} className="flex items-center gap-2 text-base font-semibold text-[var(--text-strong)]">
        <span className="[&_svg]:size-4" aria-hidden="true">{icone}</span>
        {titulo}
      </h2>
      <Button asChild variant="fantasma" size="pequeno">
        <Link to={destino}>{acao}</Link>
      </Button>
    </div>
  );
}

function Indicador({ rotulo, valor }: { readonly rotulo: string; readonly valor: string }) {
  return (
    <div className="min-w-0 border-r px-3 first:pl-0 last:border-0 last:pr-0" style={{ borderColor: 'var(--border-subtle)' }}>
      <p className="truncate text-xs" style={{ color: 'var(--text-muted)' }}>{rotulo}</p>
      <p className="mt-1 truncate text-sm font-semibold text-[var(--text-strong)]">{valor}</p>
    </div>
  );
}

function OportunidadeDoContato({ oportunidade }: { readonly oportunidade: Oportunidade }) {
  return (
    <article
      className="grid gap-2 rounded-[var(--radius-surface)] border px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="truncate text-sm font-semibold text-[var(--text-strong)]">
            {oportunidade.titulo}
          </h3>
          <EstadoDaOportunidade status={oportunidade.status} />
        </div>
        <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
          {formatarResponsavel(oportunidade)}
          {oportunidade.previsaoFechamento
            ? ` · previsão ${formatarDataCivil(oportunidade.previsaoFechamento)}`
            : ''}
        </p>
      </div>
      <strong className="text-sm text-[var(--text-strong)]">
        {formatarMoeda(oportunidade.valorCentavos)}
      </strong>
    </article>
  );
}

function EstadoDaOportunidade({ status }: { readonly status: StatusOportunidade }) {
  const estados = {
    OPEN: ['Em aberto', 'var(--warning-soft)', 'var(--warning)'],
    WON: ['Ganha', 'var(--success-soft)', 'var(--success)'],
    LOST: ['Perdida', 'var(--danger-soft)', 'var(--danger)'],
  } as const;
  const [rotulo, fundo, cor] = estados[status];
  return (
    <span className="rounded-full px-2 py-0.5 text-xs font-medium" style={{ backgroundColor: fundo, color: cor }}>
      {rotulo}
    </span>
  );
}

function AtividadeDoContato({
  tarefa,
  desabilitada,
  aoAlternar,
}: {
  readonly tarefa: Tarefa;
  readonly desabilitada: boolean;
  readonly aoAlternar: () => void;
}) {
  const concluida = Boolean(tarefa.concluidaEm);
  return (
    <div className="flex gap-3 py-3 first:pt-0">
      <button
        type="button"
        className="mt-0.5 flex size-11 shrink-0 items-center justify-center rounded-[var(--radius-control)] hover:bg-[var(--surface-sunken)] disabled:opacity-50"
        onClick={aoAlternar}
        disabled={desabilitada}
        aria-label={concluida ? `Reabrir ${tarefa.titulo}` : `Concluir ${tarefa.titulo}`}
        title={concluida ? 'Reabrir atividade' : 'Concluir atividade'}
      >
        {concluida
          ? <CheckCircle2 className="size-5 text-[var(--success)]" aria-hidden="true" />
          : <Circle className="size-5 text-[var(--text-muted)]" aria-hidden="true" />}
      </button>
      <div className="min-w-0 pt-1">
        <p className={`text-sm font-medium ${concluida ? 'line-through text-[var(--text-muted)]' : 'text-[var(--text-strong)]'}`}>
          {tarefa.titulo}
        </p>
        <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
          {tarefa.vencimentoEm ? formatarDataHora(tarefa.vencimentoEm) : 'Sem data'}
          {' · '}{formatarResponsavel(tarefa)}
        </p>
      </div>
    </div>
  );
}

function MensagemDeSecao({ texto }: { readonly texto: string }) {
  return <p className="py-6 text-sm" style={{ color: 'var(--text-muted)' }}>{texto}</p>;
}

function iniciaisDe(nome: string): string {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map((parte) => parte[0]).join('').toUpperCase();
}

