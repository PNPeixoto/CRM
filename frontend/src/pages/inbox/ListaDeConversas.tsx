import { useEffect, useMemo, useState } from 'react';
import { Headphones, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { cn } from '@/lib/utils';
import type { ConversaResumo, StatusConversa } from '@/shared/conversas/tipos';
import type { TipoCanal } from '@/shared/crm/tipos';
import { FUSO_DE_NEGOCIO } from '@/shared/formato';
import { IdentificacaoDoCanal } from './IdentificacaoDoCanal';
import { formatarIdentificadorDoContato } from './identificacao';

const STATUS: Record<StatusConversa, { rotulo: string; cor: string; fundo: string }> = {
  OPEN: { rotulo: 'Aberta', cor: 'var(--success)', fundo: 'var(--success-soft)' },
  PENDING: { rotulo: 'Aguardando', cor: 'var(--warning)', fundo: 'var(--warning-soft)' },
  CLOSED: { rotulo: 'Encerrada', cor: 'var(--text-muted)', fundo: 'var(--surface-sunken)' },
};

type FiltroStatus = 'ALL' | StatusConversa;
type FiltroCanal = 'ALL' | 'WHATSAPP' | Exclude<TipoCanal, 'WHATSAPP_CLOUD' | 'WHATSAPP_EVOLUTION'>;

const FILTROS_STATUS: readonly { valor: FiltroStatus; rotulo: string }[] = [
  { valor: 'ALL', rotulo: 'Todas' },
  { valor: 'OPEN', rotulo: 'Abertas' },
  { valor: 'PENDING', rotulo: 'Aguardando' },
];

interface Props {
  readonly conversas: readonly ConversaResumo[];
  readonly selecionada: string | null;
  readonly aoSelecionar: (id: string) => void;
  readonly carregando: boolean;
  readonly temMais: boolean;
  readonly carregandoMais: boolean;
  readonly aoCarregarMais: () => void;
}

export function ListaDeConversas({
  conversas,
  selecionada,
  aoSelecionar,
  carregando,
  temMais,
  carregandoMais,
  aoCarregarMais,
}: Props) {
  const [busca, setBusca] = useState('');
  const [filtroStatus, setFiltroStatus] = useState<FiltroStatus>('ALL');
  const [filtroCanal, setFiltroCanal] = useState<FiltroCanal>('ALL');
  const conversasFiltradas = useMemo(
    () => {
      const termo = normalizar(busca);
      return conversas.filter((conversa) => {
        const correspondeStatus = filtroStatus === 'ALL' || conversa.status === filtroStatus;
        const correspondeCanal = canalCorresponde(conversa.canalTipo, filtroCanal);
        const correspondeBusca = !termo || normalizar([
          conversa.contatoNome,
          conversa.contatoIdentificador,
          conversa.canalNome,
          conversa.canalIdentificador,
          conversa.atendenteNome,
        ].filter(Boolean).join(' ')).includes(termo);
        return correspondeStatus && correspondeCanal && correspondeBusca;
      });
    },
    [busca, conversas, filtroCanal, filtroStatus],
  );

  useEffect(() => {
    if (!selecionada || conversasFiltradas.length === 0) return;
    if (!conversasFiltradas.some((conversa) => conversa.id === selecionada)) {
      aoSelecionar(conversasFiltradas[0].id);
    }
  }, [aoSelecionar, conversasFiltradas, selecionada]);

  if (carregando) {
    return (
      <p className="p-4 text-sm text-[var(--text-muted)]" role="status">
        Carregando conversas...
      </p>
    );
  }

  if (conversas.length === 0) {
    return (
      <div className="p-6 text-center">
        <p className="text-sm font-medium">Nenhuma conversa ainda</p>
        <p className="mt-1 text-sm text-[var(--text-muted)]">
          As conversas aparecem aqui assim que a primeira mensagem chegar por um canal conectado.
        </p>
      </div>
    );
  }

  return (
    <>
      <div className="sticky top-0 z-10 space-y-3 border-b border-[var(--border-subtle)] bg-[var(--surface-raised)] p-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold">Conversas</h2>
            <p className="text-xs tabular-nums text-[var(--text-muted)]">
              {conversasFiltradas.length} de {conversas.length}
            </p>
          </div>
          <Select
            tamanho="compacto"
            className="w-36"
            aria-label="Filtrar por canal"
            value={filtroCanal}
            onChange={(evento) => setFiltroCanal(evento.target.value as FiltroCanal)}
          >
            <option value="ALL">Todos os canais</option>
            <option value="WHATSAPP">WhatsApp</option>
            <option value="TELEGRAM">Telegram</option>
            <option value="INSTAGRAM">Instagram</option>
            <option value="LIVE_CHAT">Chat do site</option>
          </Select>
        </div>

        <div className="relative">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[var(--text-muted)]"
            aria-hidden="true"
          />
          <Input
            type="search"
            className="pl-9"
            value={busca}
            onChange={(evento) => setBusca(evento.target.value)}
            placeholder="Buscar nome, número ou conta"
            aria-label="Buscar conversas"
          />
        </div>

        <div
          className="grid grid-cols-3 gap-1 rounded-[var(--radius-control)] bg-[var(--surface-sunken)] p-1"
          role="group"
          aria-label="Filtrar por status"
        >
          {FILTROS_STATUS.map((filtro) => {
            const ativo = filtroStatus === filtro.valor;
            const quantidade = filtro.valor === 'ALL'
              ? conversas.length
              : conversas.filter((conversa) => conversa.status === filtro.valor).length;
            return (
              <button
                key={filtro.valor}
                type="button"
                aria-pressed={ativo}
                onClick={() => setFiltroStatus(filtro.valor)}
                className={cn(
                  'flex h-8 min-w-0 items-center justify-center gap-1 rounded-[var(--radius-control)] px-1.5 text-[11px] font-medium',
                  ativo
                    ? 'bg-[var(--surface-raised)] text-[var(--text-strong)] shadow-sm'
                    : 'text-[var(--text-muted)] hover:text-[var(--text-strong)]',
                )}
              >
                <span className="truncate">{filtro.rotulo}</span>
                <span className="tabular-nums">{quantidade}</span>
              </button>
            );
          })}
        </div>
      </div>

      {conversasFiltradas.length === 0 ? (
        <p className="p-6 text-center text-sm text-[var(--text-muted)]">
          Nenhuma conversa corresponde aos filtros.
        </p>
      ) : (
        <ul aria-label="Lista de conversas">
          {conversasFiltradas.map((conversa) => {
            const estaSelecionada = conversa.id === selecionada;
            const status = STATUS[conversa.status];
            const contato = conversa.contatoNome ?? 'Contato sem nome';
            return (
              <li key={conversa.id}>
                <button
                  type="button"
                  onClick={() => aoSelecionar(conversa.id)}
                  aria-current={estaSelecionada ? 'true' : undefined}
                  className={cn(
                    'w-full border-b border-[var(--border-subtle)] px-3 py-3 text-left transition-colors',
                    'hover:bg-[var(--surface-sunken)]',
                    estaSelecionada && 'bg-[var(--brand-soft)] shadow-[inset_3px_0_0_var(--brand)]',
                  )}
                >
                  <span className="flex items-start gap-2.5">
                    <span
                      className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[var(--surface-sunken)] text-xs font-semibold text-[var(--text-muted)]"
                      aria-hidden="true"
                    >
                      {iniciaisDe(contato)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-start justify-between gap-2">
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-semibold">{contato}</span>
                          <span
                            className="mt-0.5 block truncate font-mono text-[11px] text-[var(--text-muted)]"
                            title={conversa.contatoIdentificador}
                          >
                            {formatarIdentificadorDoContato(conversa.contatoIdentificador)}
                          </span>
                        </span>
                        <time
                          className="shrink-0 text-[11px] tabular-nums text-[var(--text-muted)]"
                          dateTime={conversa.ultimaMensagemEm ?? undefined}
                        >
                          {formatarHorario(conversa.ultimaMensagemEm)}
                        </time>
                      </span>

                      <span className="mt-2 flex min-w-0 items-center justify-between gap-2">
                        <span className="flex min-w-0 items-center gap-1.5">
                          <IdentificacaoDoCanal tipo={conversa.canalTipo} />
                          {conversa.canalNome && (
                            <span className="truncate text-[11px] text-[var(--text-muted)]">
                              {conversa.canalNome}
                            </span>
                          )}
                        </span>
                        <span
                          className="shrink-0 rounded px-1.5 py-px text-[11px] font-medium"
                          style={{ color: status.cor, backgroundColor: status.fundo }}
                        >
                          {status.rotulo}
                        </span>
                      </span>

                      <span className="mt-2 flex items-center gap-1.5 text-[11px] text-[var(--text-muted)]">
                        <Headphones className="size-3.5" aria-hidden="true" />
                        {conversa.atendenteNome
                          ? `Atendente ${conversa.atendenteNome}`
                          : 'Não atribuída'}
                      </span>
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {temMais && (
        <div className="p-3">
          <Button
            type="button"
            variant="secundario"
            className="w-full"
            disabled={carregandoMais}
            onClick={aoCarregarMais}
          >
            {carregandoMais ? 'Carregando...' : 'Carregar conversas anteriores'}
          </Button>
        </div>
      )}
    </>
  );
}

function canalCorresponde(tipo: TipoCanal | null, filtro: FiltroCanal): boolean {
  if (filtro === 'ALL') return true;
  if (filtro === 'WHATSAPP') {
    return tipo === 'WHATSAPP_CLOUD' || tipo === 'WHATSAPP_EVOLUTION';
  }
  return tipo === filtro;
}

function normalizar(valor: string): string {
  return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');
}

function iniciaisDe(nome: string): string {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2)
    .map((parte) => parte[0]).join('').toUpperCase();
}

function formatarHorario(iso: string | null): string {
  if (!iso) return '';
  const data = new Date(iso);
  const hoje = new Date();
  if (chaveDaData(data) === chaveDaData(hoje)) {
    return new Intl.DateTimeFormat('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
      timeZone: FUSO_DE_NEGOCIO,
    }).format(data);
  }
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(data);
}

function chaveDaData(data: Date): string {
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(data);
}
