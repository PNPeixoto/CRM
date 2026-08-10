import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { AlertaErro } from '@/components/ui/alert';
import { cn } from '@/lib/utils';
import type { Mensagem, StatusMensagem } from '@/shared/conversas/tipos';

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
}

export function Conversa({
  mensagens,
  carregando,
  temMais,
  carregandoAnteriores,
  aoCarregarAnteriores,
  aoEnviar,
}: Props) {
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const fimDaListaRef = useRef<HTMLDivElement>(null);
  const ultimaMensagemId = mensagens.at(-1)?.id;

  // Rola para a última mensagem quando chega algo novo. `behavior: auto` e não
  // `smooth`: com várias mensagens chegando juntas, a animação suave enfileira
  // e a lista fica visivelmente atrasada em relação ao conteúdo.
  useEffect(() => {
    fimDaListaRef.current?.scrollIntoView({ behavior: 'auto', block: 'end' });
  }, [ultimaMensagemId]);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const conteudo = texto.trim();
    if (!conteudo) return;

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
        className="min-h-0 flex-1 overflow-y-auto px-6 py-4"
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
            {mensagens.map((mensagem) => (
              <li key={mensagem.id}>
                <Balao mensagem={mensagem} />
              </li>
            ))}
          </ol>
        )}
        <div ref={fimDaListaRef} />
      </div>

      <form
        onSubmit={enviar}
        className="border-t px-6 py-4"
        style={{ borderColor: 'var(--border-subtle)' }}
      >
        {erro && <AlertaErro className="mb-3">{erro}</AlertaErro>}

        <div className="flex items-end gap-2">
          <div className="flex-1">
            {/* Rótulo visualmente escondido, presente para leitor de tela: um
                campo sem nome acessível é anunciado apenas como "caixa de
                texto". */}
            <Label htmlFor="composer" className="sr-only">
              Escreva uma mensagem
            </Label>
            <Input
              id="composer"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Escreva uma mensagem…"
              autoComplete="off"
              disabled={enviando}
            />
          </div>
          <Button type="submit" disabled={enviando || texto.trim().length === 0}>
            {enviando ? 'Enviando…' : 'Enviar'}
          </Button>
        </div>
      </form>
    </div>
  );
}

function Balao({ mensagem }: { readonly mensagem: Mensagem }) {
  const daEquipe = mensagem.direcao === 'OUTBOUND';
  const falhou = mensagem.status === 'FAILED';

  return (
    <div className={cn('flex', daEquipe ? 'justify-end' : 'justify-start')}>
      <div className="max-w-[min(38rem,75%)]">
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
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(iso));
}
