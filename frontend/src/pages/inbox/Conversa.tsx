import { Fragment, useEffect, useRef, useState, type FormEvent } from 'react';
import { SendHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertaErro } from '@/components/ui/alert';
import { cn } from '@/lib/utils';
import type { Mensagem, StatusMensagem } from '@/shared/conversas/tipos';
import type { TipoCanal } from '@/shared/crm/tipos';
import { FUSO_DE_NEGOCIO } from '@/shared/formato';
import { IdentificacaoDoCanal } from './IdentificacaoDoCanal';
import { rotuloDoCanal } from './identificacao';

/**
 * Rótulo por status de entrega. Texto, nunca só um ícone colorido — o
 * atendente precisa saber se a mensagem saiu, e "não saiu" é a informação mais
 * importante da tela quando acontece.
 */
const STATUS_ENTREGA: Record<StatusMensagem, string> = {
  RECEIVED: '',
  PENDING: 'Enviando…',
  SENT: 'Enviada',
  DELIVERED: 'Entregue',
  READ: 'Lida',
  FAILED: 'Falhou',
};

interface Props {
  readonly mensagens: readonly Mensagem[];
  readonly carregando: boolean;
  readonly temMais: boolean;
  readonly carregandoAnteriores: boolean;
  readonly aoCarregarAnteriores: () => void;
  readonly aoEnviar: (texto: string) => Promise<void>;
  readonly contatoNome: string;
  readonly canalTipo: TipoCanal | null;
  readonly ultimaMensagemRecebidaEm: string | null;
  readonly respondendoComo: string;
}

export function Conversa({
  mensagens,
  carregando,
  temMais,
  carregandoAnteriores,
  aoCarregarAnteriores,
  aoEnviar,
  contatoNome,
  canalTipo,
  ultimaMensagemRecebidaEm,
  respondendoComo,
}: Props) {
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const fimDaListaRef = useRef<HTMLDivElement>(null);
  const ultimaMensagemId = mensagens.at(-1)?.id;
  const janelaDoInstagramEncerrada = canalTipo === 'INSTAGRAM'
    && !estaNasUltimas24Horas(ultimaMensagemRecebidaEm);

  // Rola para a última mensagem quando chega algo novo. `behavior: auto` e não
  // `smooth`: com várias mensagens chegando juntas, a animação suave enfileira
  // e a lista fica visivelmente atrasada em relação ao conteúdo.
  useEffect(() => {
    fimDaListaRef.current?.scrollIntoView?.({ behavior: 'auto', block: 'end' });
  }, [ultimaMensagemId]);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const conteudo = texto.trim();
    if (!conteudo || janelaDoInstagramEncerrada) return;

    setErro(null);
    setEnviando(true);
    try {
      await aoEnviar(conteudo);
      // Só limpa depois do sucesso. Limpar antes perderia o que o atendente
      // escreveu se o envio falhasse — e ele teria de digitar tudo de novo.
      setTexto('');
    } catch {
      setErro('Não foi possível enviar a mensagem. Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="flex h-full flex-col">
      <div
        className="min-h-0 flex-1 overflow-y-auto bg-[var(--surface-base)] px-4 py-5 sm:px-6"
        role="log"
        aria-live="polite"
        aria-label="Mensagens da conversa"
      >
        {temMais && (
          <div className="mb-4 flex justify-center">
            <Button
              type="button"
              variant="secundario"
              disabled={carregandoAnteriores}
              onClick={aoCarregarAnteriores}
            >
              {carregandoAnteriores ? 'Carregando…' : 'Carregar mensagens anteriores'}
            </Button>
          </div>
        )}
        {carregando ? (
          <p className="text-sm" style={{ color: 'var(--text-muted)' }} role="status">
            Carregando mensagens…
          </p>
        ) : mensagens.length === 0 ? (
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            Nenhuma mensagem nesta conversa.
          </p>
        ) : (
          <ol className="space-y-3">
            {mensagens.map((mensagem, indice) => (
              <Fragment key={mensagem.id}>
                {(indice === 0 || chaveDaData(mensagem.criadaEm) !== chaveDaData(mensagens[indice - 1].criadaEm)) && (
                  <li className="flex items-center gap-3 py-1" aria-hidden="true">
                    <span className="h-px flex-1 bg-[var(--border-subtle)]" />
                    <time className="text-[11px] font-medium text-[var(--text-muted)]">
                      {formatarDia(mensagem.criadaEm)}
                    </time>
                    <span className="h-px flex-1 bg-[var(--border-subtle)]" />
                  </li>
                )}
                <li>
                  <Balao mensagem={mensagem} contatoNome={contatoNome} />
                </li>
              </Fragment>
            ))}
          </ol>
        )}
        <div ref={fimDaListaRef} />
      </div>

      <form
        onSubmit={enviar}
        className="border-t border-[var(--border-subtle)] bg-[var(--surface-raised)] px-4 py-3 sm:px-6"
      >
        {erro && <AlertaErro className="mb-3">{erro}</AlertaErro>}

        {janelaDoInstagramEncerrada && (
          <p className="mb-3 text-xs text-[var(--warning)]" role="status">
            A janela de 24 horas do Instagram foi encerrada. Aguarde uma nova mensagem do contato.
          </p>
        )}

        <div
          className="mb-2 flex flex-wrap items-center gap-2 text-xs"
          aria-label={`Respondendo como ${respondendoComo} via ${rotuloDoCanal(canalTipo)}`}
        >
          <span
            className="flex size-7 shrink-0 items-center justify-center rounded-full bg-[var(--brand-soft)] text-[10px] font-semibold text-[var(--brand)]"
            aria-hidden="true"
          >
            {iniciaisDe(respondendoComo)}
          </span>
          <span className="text-[var(--text-muted)]">
            Respondendo como <strong className="font-medium text-[var(--text-strong)]">{respondendoComo}</strong>
          </span>
          <span className="text-[var(--text-muted)]">via</span>
          <IdentificacaoDoCanal tipo={canalTipo} />
        </div>

        <div className="flex items-end gap-2">
          <div className="flex-1">
            {/* Rótulo visualmente escondido, presente para leitor de tela: um
                campo sem nome acessível é anunciado apenas como "caixa de
                texto". */}
            <Label htmlFor="composer" className="sr-only">
              Escreva uma mensagem
            </Label>
            <textarea
              id="composer"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              onKeyDown={(evento) => {
                if (evento.key === 'Enter' && !evento.shiftKey) {
                  evento.preventDefault();
                  evento.currentTarget.form?.requestSubmit();
                }
              }}
              placeholder={janelaDoInstagramEncerrada
                ? 'Aguardando uma nova mensagem no Instagram…'
                : 'Escreva uma mensagem…'}
              autoComplete="off"
              disabled={enviando || janelaDoInstagramEncerrada}
              rows={2}
              className="min-h-12 max-h-32 w-full resize-y rounded-[var(--radius-control)] border border-[var(--border-control)] bg-[var(--surface-raised)] px-3 py-2 text-sm text-[var(--text-strong)] placeholder:text-[var(--text-muted)] disabled:opacity-50"
            />
          </div>
          <Button
            type="submit"
            disabled={enviando || janelaDoInstagramEncerrada || texto.trim().length === 0}
          >
            <SendHorizontal aria-hidden="true" />
            {enviando ? 'Enviando…' : 'Enviar'}
          </Button>
        </div>
      </form>
    </div>
  );
}

function Balao({ mensagem, contatoNome }: { readonly mensagem: Mensagem; readonly contatoNome: string }) {
  const daEquipe = mensagem.direcao === 'OUTBOUND';
  const falhou = mensagem.status === 'FAILED';

  return (
    <div className={cn('flex', daEquipe ? 'justify-end' : 'justify-start')}>
      <div className="max-w-[min(38rem,88%)] sm:max-w-[min(38rem,75%)]">
        <p
          className={cn(
            'mb-1 text-[11px] font-medium',
            daEquipe ? 'text-right' : 'text-left',
          )}
          style={{ color: 'var(--text-muted)' }}
        >
          {daEquipe ? (mensagem.autorNome ?? 'Equipe') : contatoNome}
        </p>
        <div
          className={cn(
            'rounded-[var(--radius-surface)] px-3.5 py-2 text-sm whitespace-pre-wrap break-words',
            daEquipe ? 'text-[var(--text-on-brand)]' : 'text-[var(--text-strong)]',
          )}
          style={{
            backgroundColor: falhou
              ? 'var(--danger-soft)'
              : daEquipe
                ? 'var(--brand)'
                : 'var(--surface-sunken)',
            color: falhou ? 'var(--danger)' : undefined,
          }}
        >
          {mensagem.texto ?? <em>Mensagem sem texto</em>}
        </div>

        <div
          className={cn(
            'mt-1 flex items-center gap-2 text-[11px] tabular-nums',
            daEquipe ? 'justify-end' : 'justify-start',
          )}
          style={{ color: falhou ? 'var(--danger)' : 'var(--text-muted)' }}
        >
          <time dateTime={mensagem.criadaEm}>{formatarHorario(mensagem.criadaEm)}</time>
          {daEquipe && STATUS_ENTREGA[mensagem.status] && (
            <span>{STATUS_ENTREGA[mensagem.status]}</span>
          )}
        </div>
      </div>
    </div>
  );
}

function formatarHorario(iso: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(new Date(iso));
}

function formatarDia(iso: string): string {
  const data = new Date(iso);
  const diaEMes = new Intl.DateTimeFormat('pt-BR', {
    day: 'numeric',
    month: 'long',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(data);
  if (chaveDaData(iso) === chaveDaData(new Date().toISOString())) {
    return `Hoje · ${diaEMes}`;
  }
  if (chaveDaData(iso) === chaveDaData(new Date(Date.now() - 86_400_000).toISOString())) {
    return `Ontem · ${diaEMes}`;
  }
  return new Intl.DateTimeFormat('pt-BR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(data);
}

function chaveDaData(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: FUSO_DE_NEGOCIO,
  }).format(new Date(iso));
}

function iniciaisDe(nome: string): string {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2)
    .map((parte) => parte[0]).join('').toUpperCase();
}

function estaNasUltimas24Horas(instante: string | null): boolean {
  if (!instante) return false;
  const milissegundos = Date.parse(instante);
  return Number.isFinite(milissegundos) && milissegundos >= Date.now() - 24 * 60 * 60 * 1000;
}
