import { useMemo, useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Clock3, Plus } from 'lucide-react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { FormularioDeTarefa } from '@/pages/tasks/FormularioDeTarefa';
import { Pagina } from '@/shared/components/Pagina';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Tarefa } from '@/shared/crm/tipos';
import { FUSO_DE_NEGOCIO } from '@/shared/formato';
import {
  useAlternarConclusaoDeTarefa,
  useCriarTarefa,
  useTarefas,
} from '@/shared/server-state/recursos';

const DIAS_DA_SEMANA = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'] as const;
const SEM_TAREFAS: readonly Tarefa[] = [];

interface MesVisivel {
  readonly ano: number;
  readonly mes: number;
}

export function CalendarPage() {
  const hoje = hojeNoNegocio();
  const [mesVisivel, setMesVisivel] = useState<MesVisivel>(() => mesDaChave(hoje));
  const [diaSelecionado, setDiaSelecionado] = useState(hoje);
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const tarefasQuery = useTarefas(false);
  const criarTarefa = useCriarTarefa();
  const alternarConclusao = useAlternarConclusaoDeTarefa();
  const tarefas = tarefasQuery.data ?? SEM_TAREFAS;

  const porDia = useMemo(() => agruparPorDia(tarefas), [tarefas]);
  const dias = useMemo(() => diasDaGrade(mesVisivel), [mesVisivel]);
  const tarefasDoMes = tarefas
    .filter((tarefa) => tarefa.vencimentoEm
      && pertenceAoMes(chaveNoFuso(tarefa.vencimentoEm), mesVisivel))
    .sort(compararTarefas);
  const diasComTarefa = [...porDia.entries()]
    .filter(([dia]) => pertenceAoMes(dia, mesVisivel))
    .sort(([a], [b]) => a.localeCompare(b));
  const tarefasDoDia = porDia.get(diaSelecionado) ?? [];
  const atrasadas = tarefas.filter((tarefa) => estaAtrasada(tarefa)).length;
  const abertasNoMes = tarefasDoMes.filter((tarefa) => !tarefa.concluidaEm).length;
  const semData = tarefas.filter((tarefa) => !tarefa.vencimentoEm && !tarefa.concluidaEm).length;

  function navegarMes(diferenca: number) {
    const proximo = new Date(Date.UTC(mesVisivel.ano, mesVisivel.mes + diferenca, 1));
    const mes = { ano: proximo.getUTCFullYear(), mes: proximo.getUTCMonth() };
    setMesVisivel(mes);
    setDiaSelecionado(chaveUtc(proximo));
  }

  function irParaHoje() {
    setMesVisivel(mesDaChave(hoje));
    setDiaSelecionado(hoje);
  }

  async function alternar(tarefa: Tarefa) {
    try {
      setErro(null);
      await alternarConclusao.mutateAsync(tarefa.id);
    } catch {
      setErro('Não foi possível atualizar o compromisso.');
    }
  }

  return (
    <Pagina
      titulo="Agenda"
      descricao="Compromissos da equipe"
      acoes={(
          <Button onClick={() => setFormAberto((aberto) => !aberto)}>
            <Plus aria-hidden="true" />
            {formAberto ? 'Cancelar' : (
              <>
                <span className="sm:hidden">Novo</span>
                <span className="hidden sm:inline">Novo compromisso</span>
              </>
            )}
        </Button>
      )}
    >
      <div className="space-y-5">
        {erro && <AlertaErro>{erro}</AlertaErro>}

        {formAberto && (
          <FormularioDeTarefa
            key={diaSelecionado}
            vencimentoInicial={`${diaSelecionado}T09:00`}
            rotuloBotao="Adicionar à agenda"
            aoSalvar={async (dados) => {
              try {
                setErro(null);
                await criarTarefa.mutateAsync(dados);
                setFormAberto(false);
              } catch {
                setErro('Não foi possível adicionar o compromisso.');
              }
            }}
          />
        )}

        <section aria-label="Resumo da agenda" className="grid grid-cols-3 border-y border-[var(--border-subtle)]">
          <Metrica valor={abertasNoMes} rotulo="abertos no mês" />
          <Metrica valor={atrasadas} rotulo="atrasados" destaque={atrasadas > 0} />
          <Metrica valor={semData} rotulo="sem data" />
        </section>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold capitalize">{rotuloDoMes(mesVisivel)}</h2>
          <div className="flex items-center gap-1" aria-label="Navegação da agenda">
            <Button variant="fantasma" size="icone" onClick={() => navegarMes(-1)} title="Mês anterior">
              <ChevronLeft aria-hidden="true" />
              <span className="sr-only">Mês anterior</span>
            </Button>
            <Button variant="secundario" size="pequeno" onClick={irParaHoje}>Hoje</Button>
            <Button variant="fantasma" size="icone" onClick={() => navegarMes(1)} title="Próximo mês">
              <ChevronRight aria-hidden="true" />
              <span className="sr-only">Próximo mês</span>
            </Button>
          </div>
        </div>

        {tarefasQuery.isPending ? (
          <p role="status" className="py-12 text-center text-sm text-[var(--text-muted)]">
            Carregando agenda…
          </p>
        ) : (
          <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
            <div className="min-w-0">
              <CalendarioMensal
                dias={dias}
                mes={mesVisivel}
                hoje={hoje}
                selecionado={diaSelecionado}
                porDia={porDia}
                aoSelecionar={setDiaSelecionado}
              />
              <ListaMensalCompacta
                dias={diasComTarefa}
                selecionado={diaSelecionado}
                aoSelecionar={setDiaSelecionado}
              />
            </div>

            <PainelDoDia
              dia={diaSelecionado}
              tarefas={tarefasDoDia}
              aoAlternar={alternar}
              aoAdicionar={() => setFormAberto(true)}
            />
          </div>
        )}
      </div>
    </Pagina>
  );
}

function Metrica({
  valor,
  rotulo,
  destaque = false,
}: {
  readonly valor: number;
  readonly rotulo: string;
  readonly destaque?: boolean;
}) {
  return (
    <div className="min-w-0 px-3 py-3 text-center sm:px-5 sm:text-left">
      <strong className="block text-xl tabular-nums" style={{ color: destaque ? 'var(--danger)' : undefined }}>
        {valor}
      </strong>
      <span className="block text-xs text-[var(--text-muted)] sm:text-sm">{rotulo}</span>
    </div>
  );
}

function CalendarioMensal({
  dias,
  mes,
  hoje,
  selecionado,
  porDia,
  aoSelecionar,
}: {
  readonly dias: readonly string[];
  readonly mes: MesVisivel;
  readonly hoje: string;
  readonly selecionado: string;
  readonly porDia: ReadonlyMap<string, readonly Tarefa[]>;
  readonly aoSelecionar: (dia: string) => void;
}) {
  return (
    <div className="hidden overflow-hidden rounded-[var(--radius-surface)] border border-[var(--border-subtle)] md:block">
      <div className="grid grid-cols-7 border-b border-[var(--border-subtle)] bg-[var(--surface-sunken)]">
        {DIAS_DA_SEMANA.map((dia) => (
          <span key={dia} className="px-2 py-2 text-center text-xs font-medium text-[var(--text-muted)]">
            {dia}
          </span>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {dias.map((dia, indice) => {
          const tarefas = porDia.get(dia) ?? [];
          const noMes = pertenceAoMes(dia, mes);
          const selecionadoAgora = dia === selecionado;
          return (
            <div
              key={dia}
              className={cn(
                'min-h-28 border-b border-r border-[var(--border-subtle)] p-2',
                indice % 7 === 6 && 'border-r-0',
                indice >= 35 && 'border-b-0',
                !noMes && 'bg-[var(--surface-sunken)]',
                selecionadoAgora && 'bg-[var(--brand-soft)]',
              )}
            >
              <button
                type="button"
                onClick={() => aoSelecionar(dia)}
                aria-label={formatarDiaCompleto(dia)}
                aria-pressed={selecionadoAgora}
                className={cn(
                  'flex size-8 items-center justify-center rounded-full text-xs tabular-nums',
                  dia === hoje && 'bg-[var(--brand)] font-semibold text-[var(--text-on-brand)]',
                  !noMes && dia !== hoje && 'text-[var(--text-muted)]',
                )}
              >
                {Number(dia.slice(-2))}
              </button>
              <ul className="mt-1 space-y-1">
                {tarefas.slice(0, 3).map((tarefa) => (
                  <li
                    key={tarefa.id}
                    className={cn(
                      'truncate rounded px-1.5 py-1 text-[11px]',
                      tarefa.concluidaEm
                        ? 'bg-[var(--surface-sunken)] text-[var(--text-muted)] line-through'
                        : estaAtrasada(tarefa)
                          ? 'bg-[var(--danger-soft)] text-[var(--danger)]'
                          : 'bg-[var(--surface-raised)] text-[var(--text-strong)]',
                    )}
                    title={tarefa.titulo}
                  >
                    {horaDaTarefa(tarefa)} {tarefa.titulo}
                  </li>
                ))}
              </ul>
              {tarefas.length > 3 && (
                <p className="mt-1 text-[11px] text-[var(--text-muted)]">+{tarefas.length - 3} itens</p>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ListaMensalCompacta({
  dias,
  selecionado,
  aoSelecionar,
}: {
  readonly dias: readonly (readonly [string, readonly Tarefa[]])[];
  readonly selecionado: string;
  readonly aoSelecionar: (dia: string) => void;
}) {
  if (dias.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-[var(--text-muted)] md:hidden">
        Nenhum compromisso neste mês.
      </p>
    );
  }
  return (
    <ul className="divide-y divide-[var(--border-subtle)] border-y border-[var(--border-subtle)] md:hidden">
      {dias.map(([dia, tarefas]) => (
        <li key={dia}>
          <button
            type="button"
            onClick={() => aoSelecionar(dia)}
            aria-pressed={dia === selecionado}
            className={cn('w-full px-2 py-3 text-left', dia === selecionado && 'bg-[var(--brand-soft)]')}
          >
            <span className="text-sm font-semibold capitalize">{formatarDiaCurto(dia)}</span>
            <span className="mt-1 flex min-w-0 items-center justify-between gap-2 text-sm text-[var(--text-muted)]">
              <span className={cn(
                'truncate',
                tarefas[0]?.concluidaEm && 'line-through',
                !tarefas[0]?.concluidaEm && estaAtrasada(tarefas[0]) && 'text-[var(--danger)]',
              )}>
                {horaDaTarefa(tarefas[0])} {tarefas[0]?.titulo}
              </span>
              {tarefas.length > 1 && <span className="shrink-0">+{tarefas.length - 1}</span>}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

function PainelDoDia({
  dia,
  tarefas,
  aoAlternar,
  aoAdicionar,
}: {
  readonly dia: string;
  readonly tarefas: readonly Tarefa[];
  readonly aoAlternar: (tarefa: Tarefa) => void;
  readonly aoAdicionar: () => void;
}) {
  return (
    <aside className="border-t border-[var(--border-subtle)] pt-5 xl:border-l xl:border-t-0 xl:pl-6 xl:pt-0" aria-label="Compromissos do dia">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium uppercase text-[var(--text-muted)]">Dia selecionado</p>
          <h3 className="mt-1 text-base font-semibold capitalize">{formatarDiaCompleto(dia)}</h3>
        </div>
        <Button variant="fantasma" size="icone" title="Adicionar neste dia" onClick={aoAdicionar}>
          <Plus aria-hidden="true" />
          <span className="sr-only">Adicionar neste dia</span>
        </Button>
      </div>

      {tarefas.length === 0 ? (
        <div className="py-10 text-center">
          <CalendarDays className="mx-auto size-6 text-[var(--text-muted)]" aria-hidden="true" />
          <p className="mt-2 text-sm font-medium">Dia livre</p>
          <p className="mt-1 text-sm text-[var(--text-muted)]">Nenhum compromisso agendado.</p>
        </div>
      ) : (
        <ul className="mt-4 divide-y divide-[var(--border-subtle)]">
          {[...tarefas].sort(compararTarefas).map((tarefa) => (
            <li key={tarefa.id} className="flex gap-3 py-3">
              <input
                type="checkbox"
                checked={Boolean(tarefa.concluidaEm)}
                onChange={() => aoAlternar(tarefa)}
                className="mt-0.5 size-5 shrink-0"
                aria-label={tarefa.concluidaEm ? `Reabrir ${tarefa.titulo}` : `Concluir ${tarefa.titulo}`}
              />
              <div className="min-w-0">
                <p className={cn('text-sm font-medium', tarefa.concluidaEm && 'text-[var(--text-muted)] line-through')}>
                  {tarefa.titulo}
                </p>
                <p className="mt-1 flex items-center gap-1 text-xs text-[var(--text-muted)]">
                  <Clock3 className="size-3" aria-hidden="true" />
                  {horaDaTarefa(tarefa)} · {formatarResponsavel(tarefa)}
                </p>
                {tarefa.descricao && (
                  <p className="mt-1 text-xs text-[var(--text-muted)]">{tarefa.descricao}</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}

function agruparPorDia(tarefas: readonly Tarefa[]): Map<string, readonly Tarefa[]> {
  const agrupadas = new Map<string, Tarefa[]>();
  for (const tarefa of tarefas) {
    if (!tarefa.vencimentoEm) continue;
    const chave = chaveNoFuso(tarefa.vencimentoEm);
    agrupadas.set(chave, [...(agrupadas.get(chave) ?? []), tarefa]);
  }
  return agrupadas;
}

function diasDaGrade(mes: MesVisivel): readonly string[] {
  const primeiro = new Date(Date.UTC(mes.ano, mes.mes, 1));
  const deslocamento = (primeiro.getUTCDay() + 6) % 7;
  const inicio = new Date(primeiro);
  inicio.setUTCDate(1 - deslocamento);
  return Array.from({ length: 42 }, (_, indice) => {
    const dia = new Date(inicio);
    dia.setUTCDate(inicio.getUTCDate() + indice);
    return chaveUtc(dia);
  });
}

function hojeNoNegocio(): string {
  return chaveNoFuso(new Date().toISOString());
}

function chaveNoFuso(iso: string): string {
  const partes = new Intl.DateTimeFormat('en', {
    timeZone: FUSO_DE_NEGOCIO,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(iso));
  const valor = (tipo: string) => partes.find((parte) => parte.type === tipo)?.value ?? '';
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}

function chaveUtc(data: Date): string {
  return data.toISOString().slice(0, 10);
}

function mesDaChave(chave: string): MesVisivel {
  return { ano: Number(chave.slice(0, 4)), mes: Number(chave.slice(5, 7)) - 1 };
}

function pertenceAoMes(chave: string, mes: MesVisivel): boolean {
  const partes = mesDaChave(chave);
  return partes.ano === mes.ano && partes.mes === mes.mes;
}

function dataDaChave(chave: string): Date {
  return new Date(`${chave}T12:00:00Z`);
}

function rotuloDoMes(mes: MesVisivel): string {
  return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(Date.UTC(mes.ano, mes.mes, 1)));
}

function formatarDiaCompleto(chave: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'long', day: '2-digit', month: 'long', timeZone: 'UTC',
  }).format(dataDaChave(chave));
}

function formatarDiaCurto(chave: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    weekday: 'short', day: '2-digit', month: 'short', timeZone: 'UTC',
  }).format(dataDaChave(chave));
}

function horaDaTarefa(tarefa: Tarefa): string {
  if (!tarefa.vencimentoEm) return 'Sem horário';
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit', minute: '2-digit', timeZone: FUSO_DE_NEGOCIO,
  }).format(new Date(tarefa.vencimentoEm));
}

function compararTarefas(a: Tarefa, b: Tarefa): number {
  return (a.vencimentoEm ?? '').localeCompare(b.vencimentoEm ?? '');
}

function estaAtrasada(tarefa: Tarefa): boolean {
  return !tarefa.concluidaEm
    && Boolean(tarefa.vencimentoEm)
    && new Date(tarefa.vencimentoEm ?? 0) < new Date();
}
