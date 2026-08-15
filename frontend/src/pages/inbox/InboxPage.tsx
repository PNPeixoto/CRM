import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Headphones, MessageSquareText } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useAuth } from '@/shared/auth/AuthContext';
import type { ConversaResumo, Mensagem, MensagemPush } from '@/shared/conversas/tipos';
import { useTempoReal, type EstadoConexao } from '@/shared/conversas/useTempoReal';
import {
  useConversas,
  useEnviarMensagem,
  useMensagens,
  useSincronizacaoDaInbox,
} from '@/shared/server-state/recursos';
import { Conversa } from './Conversa';
import { ListaDeConversas } from './ListaDeConversas';
import { IdentificacaoDoCanal } from './IdentificacaoDoCanal';
import { formatarIdentificadorDoContato } from './identificacao';

/** Caixa de entrada omnichannel: REST é a verdade e o WebSocket a aceleração. */
export function InboxPage() {
  const { usuario } = useAuth();
  const [selecionada, setSelecionada] = useState<string | null>(null);
  const conversasQuery = useConversas();
  const mensagensQuery = useMensagens(selecionada);
  const enviarMensagem = useEnviarMensagem();
  const sincronizarInbox = useSincronizacaoDaInbox();

  const conversas = useMemo(
    () => deduplicarPorVersao(
      conversasQuery.data?.pages.flatMap((pagina) => pagina.itens) ?? [],
    ),
    [conversasQuery.data],
  );
  const mensagens = useMemo(
    () => deduplicarPorVersao(
      [...(mensagensQuery.data?.pages ?? [])].reverse().flatMap((pagina) => pagina.itens),
    ),
    [mensagensQuery.data],
  );
  const sequenciaInicial = Math.max(
    0,
    ...(conversasQuery.data?.pages.map((pagina) => pagina.sequenciaDoStream) ?? []),
    ...(mensagensQuery.data?.pages.map((pagina) => pagina.sequenciaDoStream) ?? []),
  );

  const aoReceberMensagem = useCallback((push: MensagemPush) => {
    sincronizarInbox(push.conversationId === selecionada ? selecionada : null);
  }, [sincronizarInbox, selecionada]);

  const aoSincronizar = useCallback(() => {
    sincronizarInbox(selecionada);
  }, [sincronizarInbox, selecionada]);

  const conexao = useTempoReal({
    tenantId: usuario?.tenantId ?? null,
    conversaAtiva: selecionada,
    sequenciaInicial,
    aoReceberMensagem,
    aoSincronizar,
  });

  const enviar = useCallback(async (texto: string) => {
    if (!selecionada) return;
    await enviarMensagem.mutateAsync({ conversaId: selecionada, texto });
  }, [selecionada, enviarMensagem]);

  const conversaAberta = conversas.find((conversa) => conversa.id === selecionada);
  const emAtendimento = conversas.filter(
    (conversa) => conversa.status === 'OPEN' || conversa.status === 'PENDING',
  ).length;

  useEffect(() => {
    if (selecionada || conversas.length === 0) return;
    if (window.matchMedia('(min-width: 768px)').matches) {
      setSelecionada(conversas[0].id);
    }
  }, [conversas, selecionada]);

  return (
    <div className="flex h-full flex-col">
      <header
        className="flex shrink-0 items-center justify-between gap-4 border-b px-4 py-3 sm:px-6"
        style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
      >
        <div className="min-w-0">
          <h1 className="text-base font-semibold">Caixa de entrada</h1>
          <p className="truncate text-xs text-[var(--text-muted)]">
            {conversas.length} {conversas.length === 1 ? 'conversa' : 'conversas'} · {emAtendimento} em atendimento
          </p>
        </div>
        <IndicadorDeConexao estado={conexao} />
      </header>

      <div className="flex min-h-0 flex-1">
        <section
          className={cn(
            'w-full shrink-0 flex-col overflow-y-auto border-r md:flex md:w-[22rem] lg:w-[23rem]',
            selecionada ? 'hidden' : 'flex',
          )}
          style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
          aria-label="Conversas"
        >
          <ListaDeConversas
            conversas={conversas}
            selecionada={selecionada}
            aoSelecionar={setSelecionada}
            carregando={conversasQuery.isPending}
            temMais={conversasQuery.hasNextPage}
            carregandoMais={conversasQuery.isFetchingNextPage}
            aoCarregarMais={() => void conversasQuery.fetchNextPage()}
          />
        </section>

        <section
          className={cn('min-w-0 flex-1 flex-col md:flex', selecionada ? 'flex' : 'hidden')}
          aria-label="Conversa selecionada"
        >
          {conversaAberta ? (
            <>
              <div
                className="shrink-0 border-b px-4 py-3 sm:px-6"
                style={{
                  borderColor: 'var(--border-subtle)',
                  backgroundColor: 'var(--surface-raised)',
                }}
              >
                <div className="flex items-start gap-3">
                  <Button
                    variant="fantasma"
                    size="icone"
                    className="md:hidden"
                    onClick={() => setSelecionada(null)}
                    title="Voltar às conversas"
                  >
                    <ArrowLeft aria-hidden="true" />
                    <span className="sr-only">Voltar às conversas</span>
                  </Button>
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-[var(--brand-soft)] text-xs font-semibold text-[var(--brand)]" aria-hidden="true">
                    {iniciaisDe(conversaAberta.contatoNome ?? 'Contato')}
                  </div>
                  <div className="min-w-0 flex-1">
                    <h2 className="truncate text-sm font-semibold">
                      {conversaAberta.contatoNome ?? 'Contato sem nome'}
                    </h2>
                    <p className="mt-0.5 truncate font-mono text-xs text-[var(--text-muted)]">
                      {formatarIdentificadorDoContato(conversaAberta.contatoIdentificador)}
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-[var(--text-muted)]">
                      <IdentificacaoDoCanal tipo={conversaAberta.canalTipo} />
                      {conversaAberta.canalNome && <span>{conversaAberta.canalNome}</span>}
                      {conversaAberta.canalIdentificador && (
                        <span className="font-mono">
                          Conta: {formatarIdentificadorDoContato(conversaAberta.canalIdentificador)}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="hidden shrink-0 items-center gap-2 border-l border-[var(--border-subtle)] pl-4 text-xs lg:flex">
                    <Headphones className="size-4 text-[var(--text-muted)]" aria-hidden="true" />
                    <span className="min-w-0">
                      <span className="block text-[var(--text-muted)]">Atendente responsável</span>
                      <strong className="font-medium text-[var(--text-strong)]">
                        {conversaAberta.atendenteNome ?? 'Não atribuído'}
                      </strong>
                    </span>
                  </div>
                </div>
              </div>
              <div className="min-h-0 flex-1">
                <Conversa
                  mensagens={mensagens}
                  carregando={mensagensQuery.isPending}
                  temMais={mensagensQuery.hasNextPage}
                  carregandoAnteriores={mensagensQuery.isFetchingNextPage}
                  aoCarregarAnteriores={() => void mensagensQuery.fetchNextPage()}
                  aoEnviar={enviar}
                  contatoNome={conversaAberta.contatoNome ?? 'Contato'}
                  canalTipo={conversaAberta.canalTipo}
                  respondendoComo={usuario?.nomeCompleto ?? usuario?.login ?? 'Equipe'}
                />
              </div>
            </>
          ) : (
            <div className="flex h-full items-center justify-center p-6 text-center">
              <div className="max-w-sm text-[var(--text-muted)]">
                <MessageSquareText className="mx-auto size-8" aria-hidden="true" />
                <p className="mt-3 text-sm">
                  Selecione uma conversa à esquerda para ver o histórico e responder.
                </p>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function IndicadorDeConexao({ estado }: { readonly estado: EstadoConexao }) {
  const textos: Record<EstadoConexao, string> = {
    conectando: 'Conectando…',
    conectado: 'Tempo real ativo',
    desconectado: 'Reconectando — sincronização automática ativa',
  };
  const cores: Record<EstadoConexao, string> = {
    conectando: 'var(--warning)',
    conectado: 'var(--success)',
    desconectado: 'var(--danger)',
  };

  return (
    <span
      className="flex shrink-0 items-center gap-2 rounded-[var(--radius-control)] bg-[var(--surface-sunken)] px-2.5 py-1.5 text-xs"
      style={{ color: cores[estado] }}
    >
      <span
        aria-hidden="true"
        className="inline-block size-2 rounded-full"
        style={{ backgroundColor: 'currentColor' }}
      />
      <span role="status" aria-live="polite">{textos[estado]}</span>
    </span>
  );
}

function iniciaisDe(nome: string): string {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2)
    .map((parte) => parte[0]).join('').toUpperCase();
}

function deduplicarPorVersao<T extends ConversaResumo | Mensagem>(itens: readonly T[]): readonly T[] {
  const unicos = new Map<string, T>();
  for (const item of itens) {
    const anterior = unicos.get(item.id);
    if (!anterior || item.versao >= anterior.versao) unicos.set(item.id, item);
  }
  return [...unicos.values()];
}
