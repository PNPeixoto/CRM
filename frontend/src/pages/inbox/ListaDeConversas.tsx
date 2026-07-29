import { cn } from '@/lib/utils';
import type { ConversaResumo, StatusConversa } from '@/shared/conversas/tipos';

/**
 * Rótulo e cor por status.
 *
 * O texto vem sempre junto da cor. Status representado só por cor é invisível
 * para quem tem deficiência de percepção de cor e não existe para leitor de
 * tela — regra explícita do 00-projeto.md §7.
 */
const STATUS: Record<StatusConversa, { rotulo: string; cor: string; fundo: string }> = {
  OPEN: { rotulo: 'Aberta', cor: 'var(--success)', fundo: 'var(--success-soft)' },
  PENDING: { rotulo: 'Aguardando', cor: 'var(--warning)', fundo: 'var(--warning-soft)' },
  CLOSED: { rotulo: 'Encerrada', cor: 'var(--text-muted)', fundo: 'var(--surface-sunken)' },
};

interface Props {
  readonly conversas: readonly ConversaResumo[];
  readonly selecionada: string | null;
  readonly aoSelecionar: (id: string) => void;
  readonly carregando: boolean;
}

export function ListaDeConversas({ conversas, selecionada, aoSelecionar, carregando }: Props) {
  if (carregando) {
    return (
      <p className="p-4 text-sm" style={{ color: 'var(--text-muted)' }} role="status">
        Carregando conversas…
      </p>
    );
  }

  if (conversas.length === 0) {
    return (
      <div className="p-6 text-center">
        <p className="text-sm font-medium">Nenhuma conversa ainda</p>
        <p className="mt-1 text-sm" style={{ color: 'var(--text-muted)' }}>
          As conversas aparecem aqui assim que a primeira mensagem chegar por um
          canal conectado.
        </p>
      </div>
    );
  }

  return (
    // Lista semântica: leitor de tela anuncia a quantidade de itens, o que uma
    // pilha de <div> não faz.
    <ul>
      {conversas.map((conversa) => {
        const estaSelecionada = conversa.id === selecionada;
        const status = STATUS[conversa.status];

        return (
          <li key={conversa.id}>
            <button
              type="button"
              onClick={() => aoSelecionar(conversa.id)}
              aria-current={estaSelecionada ? 'true' : undefined}
              className={cn(
                'w-full border-b px-4 py-3 text-left transition-colors',
                'hover:bg-[var(--surface-sunken)]',
                estaSelecionada && 'bg-[var(--brand-soft)]',
              )}
              style={{ borderColor: 'var(--border-subtle)' }}
            >
              <div className="flex items-baseline justify-between gap-2">
                <span className="truncate text-sm font-medium">
                  {conversa.contatoNome ?? 'Contato sem nome'}
                </span>
                <time
                  className="shrink-0 text-xs tabular-nums"
                  style={{ color: 'var(--text-muted)' }}
                  dateTime={conversa.ultimaMensagemEm ?? undefined}
                >
                  {formatarHorario(conversa.ultimaMensagemEm)}
                </time>
              </div>

              <span
                className="mt-1.5 inline-block rounded px-1.5 py-px text-[11px] font-medium"
                style={{ color: status.cor, backgroundColor: status.fundo }}
              >
                {status.rotulo}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * O backend grava e devolve tudo em UTC; a exibição é em horário de Brasília,
 * conforme o 00-projeto.md. Deixar o navegador decidir o fuso faria o mesmo
 * registro aparecer com horas diferentes para atendentes em estados
 * diferentes, e o horário de uma mensagem é dado operacional.
 */
function formatarHorario(iso: string | null): string {
  if (!iso) return '';
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(iso));
}
