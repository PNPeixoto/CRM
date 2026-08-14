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
import { GripVertical } from 'lucide-react';
import { Select } from '@/components/ui/select';
import { cn } from '@/lib/utils';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Etapa, Oportunidade } from '@/shared/crm/tipos';
import { formatarMoeda } from '@/shared/formato';
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
}: {
  readonly etapas: readonly Etapa[];
  readonly oportunidades: readonly Oportunidade[];
  readonly oportunidadeEmMovimento: string | null;
  readonly aoMover: (oportunidadeId: string, etapaId: string) => void;
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
            etapas={etapas}
            oportunidadeEmMovimento={oportunidadeEmMovimento}
            aoMover={aoMover}
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
  etapas,
  oportunidadeEmMovimento,
  aoMover,
}: {
  readonly etapa: Etapa;
  readonly oportunidades: readonly Oportunidade[];
  readonly etapas: readonly Etapa[];
  readonly oportunidadeEmMovimento: string | null;
  readonly aoMover: (oportunidadeId: string, etapaId: string) => void;
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
        'flex w-72 shrink-0 flex-col rounded-[var(--radius-surface)] border transition-[border-color,box-shadow,background-color]',
        isOver && 'border-[var(--brand)] bg-[var(--brand-soft)] shadow-sm',
      )}
      style={isOver ? undefined : {
        borderColor: 'var(--border-subtle)',
        backgroundColor: 'var(--surface-sunken)',
      }}
      aria-label={`Etapa ${etapa.nome}`}
    >
      <header className="border-b px-3 py-2" style={{ borderColor: 'var(--border-subtle)' }}>
        <div className="flex items-center justify-between gap-2">
          <h2 className="truncate text-sm font-semibold">{etapa.nome}</h2>
          {(etapa.ganho || etapa.perda) && (
            <span
              className="shrink-0 rounded px-1.5 py-px text-[10px] font-medium uppercase"
              style={{
                color: etapa.ganho ? 'var(--success)' : 'var(--danger)',
                backgroundColor: etapa.ganho ? 'var(--success-soft)' : 'var(--danger-soft)',
              }}
            >
              {etapa.ganho ? 'ganho' : 'perda'}
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs tabular-nums" style={{ color: 'var(--text-muted)' }}>
          {oportunidades.length} · {formatarMoeda(total)}
        </p>
      </header>

      <div className="flex min-h-28 flex-1 flex-col gap-2 p-2">
        {oportunidades.map((oportunidade) => (
          <CartaoDeOportunidade
            key={oportunidade.id}
            oportunidade={oportunidade}
            etapas={etapas}
            movendo={oportunidadeEmMovimento === oportunidade.id}
            desabilitado={oportunidadeEmMovimento !== null}
            aoMover={aoMover}
          />
        ))}
      </div>
    </section>
  );
}

function CartaoDeOportunidade({
  oportunidade,
  etapas,
  movendo,
  desabilitado,
  aoMover,
}: {
  readonly oportunidade: Oportunidade;
  readonly etapas: readonly Etapa[];
  readonly movendo: boolean;
  readonly desabilitado: boolean;
  readonly aoMover: (oportunidadeId: string, etapaId: string) => void;
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

  return (
    <article
      ref={setNodeRef}
      aria-busy={movendo || undefined}
      className={cn(
        'rounded-[var(--radius-control)] border p-2.5 shadow-sm transition-[border-color,box-shadow,opacity]',
        isDragging && 'opacity-35',
      )}
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <p className="break-words text-sm font-medium">{oportunidade.titulo}</p>
          <p className="mt-0.5 text-sm tabular-nums" style={{ color: 'var(--text-muted)' }}>
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
      <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
        Responsável: {formatarResponsavel(oportunidade)}
      </p>

      <label className="sr-only" htmlFor={`mover-${oportunidade.id}`}>
        Mover {oportunidade.titulo} para outra etapa
      </label>
      <Select
        id={`mover-${oportunidade.id}`}
        tamanho="compacto"
        className="mt-2 w-full"
        value={oportunidade.etapaId}
        disabled={desabilitado}
        onChange={(evento) => aoMover(oportunidade.id, evento.target.value)}
      >
        {etapas.map((destino) => (
          <option key={destino.id} value={destino.id}>
            {destino.nome}
          </option>
        ))}
      </Select>
    </article>
  );
}

function CartaoDeArraste({ oportunidade }: { readonly oportunidade: Oportunidade }) {
  return (
    <div
      className="w-72 rounded-[var(--radius-control)] border border-[var(--brand)] bg-[var(--surface-raised)] p-3 shadow-lg"
      aria-hidden="true"
    >
      <p className="break-words text-sm font-semibold">{oportunidade.titulo}</p>
      <p className="mt-1 text-sm tabular-nums text-[var(--text-muted)]">
        {formatarMoeda(oportunidade.valorCentavos)}
      </p>
    </div>
  );
}
