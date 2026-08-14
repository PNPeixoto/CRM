import { useState } from 'react';
import {
  closestCorners,
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type Announcements,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { CalendarClock, GripVertical, Plus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Etapa, Oportunidade } from '@/shared/crm/tipos';
import { formatarDataCivil, formatarMoeda } from '@/shared/formato';
import { resolverMovimentoDoKanban } from './kanban';

const anuncios: Announcements = {
  onDragStart({ active }) {
    return `Oportunidade ${tituloDaOportunidade(active.data.current)} selecionada.`;
  },
  onDragOver({ active, over }) {
    if (!over) return `Oportunidade ${tituloDaOportunidade(active.data.current)} fora das etapas.`;
    return `Oportunidade ${tituloDaOportunidade(active.data.current)} sobre a etapa ${nomeDaEtapa(over.data.current)}.`;
  },
  onDragEnd({ active, over }) {
    if (!over) return `Movimento de ${tituloDaOportunidade(active.data.current)} cancelado.`;
    if (active.data.current?.etapaId === over.id) {
      return `Oportunidade ${tituloDaOportunidade(active.data.current)} permaneceu na etapa ${nomeDaEtapa(over.data.current)}.`;
    }
    return `Oportunidade ${tituloDaOportunidade(active.data.current)} movida para ${nomeDaEtapa(over.data.current)}.`;
  },
  onDragCancel({ active }) {
    return `Movimento de ${tituloDaOportunidade(active.data.current)} cancelado.`;
  },
};

function tituloDaOportunidade(dados: Record<string, unknown> | undefined) {
  return typeof dados?.titulo === 'string' ? dados.titulo : 'sem título';
}

function nomeDaEtapa(dados: Record<string, unknown> | undefined) {
  return typeof dados?.etapaNome === 'string' ? dados.etapaNome : 'desconhecida';
}

export function KanbanBoard({
  etapas,
  oportunidades,
  oportunidadeEmMovimento,
  aoMover,
  aoAdicionar,
}: {
  readonly etapas: readonly Etapa[];
  readonly oportunidades: readonly Oportunidade[];
  readonly oportunidadeEmMovimento: string | null;
  readonly aoMover: (oportunidadeId: string, etapaId: string) => void;
  readonly aoAdicionar: (etapaId: string) => void;
}) {
  const [oportunidadeArrastadaId, setOportunidadeArrastadaId] = useState<string | null>(null);
  const sensores = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  );
  const oportunidadeArrastada = oportunidades.find(
    (oportunidade) => oportunidade.id === oportunidadeArrastadaId,
  ) ?? null;

  function iniciarArraste(evento: DragStartEvent) {
    setOportunidadeArrastadaId(String(evento.active.id));
  }

  function terminarArraste(evento: DragEndEvent) {
    setOportunidadeArrastadaId(null);
    const oportunidade = oportunidades.find(
      (item) => item.id === String(evento.active.id),
    );
    if (!oportunidade) return;
    const movimento = resolverMovimentoDoKanban(
      oportunidade.id,
      oportunidade.etapaId,
      evento.over ? String(evento.over.id) : null,
    );
    if (movimento) aoMover(movimento.oportunidadeId, movimento.etapaId);
  }

  return (
    <DndContext
      sensors={sensores}
      collisionDetection={closestCorners}
      accessibility={{
        announcements: anuncios,
        screenReaderInstructions: {
          draggable: 'Pressione espaço para iniciar. Use as setas para escolher uma etapa e espaço novamente para soltar. Pressione escape para cancelar.',
        },
      }}
      onDragStart={iniciarArraste}
      onDragCancel={() => setOportunidadeArrastadaId(null)}
      onDragEnd={terminarArraste}
    >
      <div className="flex w-full max-w-full gap-3 overflow-x-auto pb-2">
        {etapas.map((etapa) => (
          <Coluna
            key={etapa.id}
            etapa={etapa}
            oportunidades={oportunidades.filter((item) => item.etapaId === etapa.id)}
            oportunidadeEmMovimento={oportunidadeEmMovimento}
            aoAdicionar={aoAdicionar}
          />
        ))}
      </div>

      <DragOverlay>
        {oportunidadeArrastada ? <CartaoDeArraste oportunidade={oportunidadeArrastada} /> : null}
      </DragOverlay>
    </DndContext>
  );
}

function Coluna({
  etapa,
  oportunidades,
  oportunidadeEmMovimento,
  aoAdicionar,
}: {
  readonly etapa: Etapa;
  readonly oportunidades: readonly Oportunidade[];
  readonly oportunidadeEmMovimento: string | null;
  readonly aoAdicionar: (etapaId: string) => void;
}) {
  const total = oportunidades.reduce((soma, oportunidade) => soma + oportunidade.valorCentavos, 0);
  const { isOver, setNodeRef } = useDroppable({
    id: etapa.id,
    data: { etapaNome: etapa.nome },
  });

  return (
    <section
      ref={setNodeRef}
      className={cn(
        'flex min-h-[30rem] w-72 shrink-0 flex-col rounded-[var(--radius-surface)] border transition-[border-color,box-shadow,background-color]',
        isOver && 'border-[var(--brand)] bg-[var(--brand-soft)] shadow-sm',
      )}
      style={isOver ? undefined : {
        borderColor: 'var(--border-subtle)',
        backgroundColor: 'var(--surface-sunken)',
      }}
      aria-label={`Etapa ${etapa.nome}`}
    >
      <header className="border-b px-3 py-3" style={{ borderColor: 'var(--border-subtle)' }}>
        <div className="flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <span
              className="size-2 shrink-0 rounded-full"
              style={{ backgroundColor: corDaEtapa(etapa) }}
              aria-hidden="true"
            />
            <h2 className="truncate text-sm font-semibold">{etapa.nome}</h2>
          </div>
          <span
            className="min-w-6 shrink-0 rounded-full border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-1.5 py-0.5 text-center text-xs font-medium tabular-nums text-[var(--text-muted)]"
            aria-label={oportunidades.length === 1
              ? '1 oportunidade'
              : `${oportunidades.length} oportunidades`}
          >
            {oportunidades.length}
          </span>
        </div>
        <div className="mt-2 flex items-center justify-between gap-2 text-xs tabular-nums text-[var(--text-muted)]">
          <span>{formatarMoeda(total)}</span>
          <span>média {formatarMoeda(oportunidades.length ? Math.round(total / oportunidades.length) : 0)}</span>
        </div>
      </header>

      <div className="flex min-h-28 flex-1 flex-col gap-2 p-2.5">
        {oportunidades.map((oportunidade) => (
          <CartaoDeOportunidade
            key={oportunidade.id}
            oportunidade={oportunidade}
            movendo={oportunidadeEmMovimento === oportunidade.id}
            desabilitado={oportunidadeEmMovimento !== null}
          />
        ))}

        <button
          type="button"
          onClick={() => aoAdicionar(etapa.id)}
          disabled={oportunidadeEmMovimento !== null}
          className="mt-auto flex min-h-11 w-full items-center justify-center gap-1.5 rounded-[var(--radius-control)] border border-dashed border-[var(--border-strong)] text-xs font-medium text-[var(--text-muted)] hover:border-[var(--brand)] hover:bg-[var(--surface-raised)] hover:text-[var(--brand)] disabled:cursor-wait disabled:opacity-50"
          aria-label={`Adicionar oportunidade em ${etapa.nome}`}
        >
          <Plus className="size-4" aria-hidden="true" />
          Adicionar
        </button>
      </div>
    </section>
  );
}

function CartaoDeOportunidade({
  oportunidade,
  movendo,
  desabilitado,
}: {
  readonly oportunidade: Oportunidade;
  readonly movendo: boolean;
  readonly desabilitado: boolean;
}) {
  const {
    attributes,
    isDragging,
    listeners,
    setActivatorNodeRef,
    setNodeRef,
  } = useDraggable({
    id: oportunidade.id,
    data: { titulo: oportunidade.titulo, etapaId: oportunidade.etapaId },
    disabled: desabilitado,
    attributes: { role: 'button', roleDescription: 'oportunidade arrastável' },
  });
  const idade = idadeEmDias(oportunidade.criadaEm);

  return (
    <article
      ref={setNodeRef}
      aria-busy={movendo || undefined}
      className={cn(
        'min-h-36 rounded-[var(--radius-control)] border p-3 shadow-sm transition-[border-color,box-shadow,opacity]',
        'hover:border-[var(--border-strong)] hover:shadow-md',
        isDragging && 'opacity-35',
      )}
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <p className="break-words text-sm font-semibold leading-5">{oportunidade.titulo}</p>
          <p className="mt-2 font-mono text-base font-semibold tabular-nums text-[var(--text-strong)]">
            {formatarMoeda(oportunidade.valorCentavos)}
          </p>
        </div>
        <button
          ref={setActivatorNodeRef}
          type="button"
          disabled={desabilitado}
          title={`Arrastar ${oportunidade.titulo}`}
          aria-label={`Arrastar ${oportunidade.titulo}`}
          className="flex size-9 shrink-0 cursor-grab touch-none items-center justify-center rounded-[var(--radius-control)] text-[var(--text-muted)] hover:bg-[var(--surface-sunken)] hover:text-[var(--text-strong)] active:cursor-grabbing disabled:cursor-wait disabled:opacity-50"
          {...attributes}
          {...listeners}
        >
          <GripVertical className="size-5" aria-hidden="true" />
        </button>
      </div>

      {oportunidade.previsaoFechamento && (
        <p className="mt-2 flex items-center gap-1.5 border-t border-[var(--border-subtle)] pt-2 text-xs text-[var(--text-muted)]">
          <CalendarClock className="size-3.5" aria-hidden="true" />
          Previsão {formatarDataCivil(oportunidade.previsaoFechamento)}
        </p>
      )}

      <div className="mt-2 flex items-center gap-2 border-t border-[var(--border-subtle)] pt-2 text-xs text-[var(--text-muted)]">
        <span
          className="flex size-6 shrink-0 items-center justify-center rounded-full bg-[var(--brand-soft)] text-[10px] font-semibold text-[var(--brand)]"
          aria-hidden="true"
        >
          {iniciaisDe(nomeDoResponsavel(oportunidade))}
        </span>
        <span
          className="min-w-0 flex-1 truncate"
          title={formatarResponsavel(oportunidade)}
          aria-label={`Responsável: ${formatarResponsavel(oportunidade)}`}
        >
          {nomeDoResponsavel(oportunidade)}
        </span>
        <span
          className="shrink-0 rounded bg-[var(--surface-sunken)] px-1.5 py-0.5 tabular-nums"
          aria-label={`Criada há ${idade} ${idade === 1 ? 'dia' : 'dias'}`}
        >
          {idade}d
        </span>
      </div>
    </article>
  );
}

function corDaEtapa(etapa: Etapa): string {
  if (etapa.ganho) return 'var(--success)';
  if (etapa.perda) return 'var(--danger)';
  if (etapa.posicao === 1) return 'var(--info)';
  return 'var(--brand)';
}

function nomeDoResponsavel(oportunidade: Oportunidade): string {
  return oportunidade.responsavelNome ?? oportunidade.responsavelLogin ?? 'Sem responsável';
}

function iniciaisDe(nome: string): string {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map((parte) => parte[0]).join('').toUpperCase();
}

function idadeEmDias(criadaEm: string): number {
  const diferenca = Date.now() - new Date(criadaEm).getTime();
  return Math.max(0, Math.floor(diferenca / 86_400_000));
}

function CartaoDeArraste({ oportunidade }: { readonly oportunidade: Oportunidade }) {
  return (
    <div
      className="w-72 rounded-[var(--radius-control)] border border-[var(--brand)] bg-[var(--surface-raised)] p-3 shadow-lg"
      aria-hidden="true"
    >
      <p className="break-words text-sm font-semibold">{oportunidade.titulo}</p>
      <p className="mt-2 font-mono text-base font-semibold tabular-nums text-[var(--text-strong)]">
        {formatarMoeda(oportunidade.valorCentavos)}
      </p>
    </div>
  );
}
