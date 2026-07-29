import { useCallback, useEffect, useState } from 'react';
import { conversasApi } from '@/shared/conversas/api';
import { useTempoReal, type EstadoConexao } from '@/shared/conversas/useTempoReal';
import type { ConversaResumo, Mensagem } from '@/shared/conversas/tipos';
import { useAuth } from '@/shared/auth/AuthContext';
import { Conversa } from './Conversa';
import { ListaDeConversas } from './ListaDeConversas';

/**
 * Caixa de entrada omnichannel.
 *
 * <p>Duas fontes alimentam a tela e a hierarquia entre elas é explícita: o
 * REST é a verdade, o WebSocket é aceleração. Toda (re)conexão dispara uma
 * recarga completa — enquanto o socket esteve caído chegaram mensagens que
 * ninguém entregou, e não há como saber quantas. Reconstruir o que faltou a
 * partir de informação incompleta produz uma tela sutilmente errada; recarregar
 * é barato e sempre certo.
 */
export function InboxPage() {
  const { usuario } = useAuth();

  const [conversas, setConversas] = useState<readonly ConversaResumo[]>([]);
  const [selecionada, setSelecionada] = useState<string | null>(null);
  const [mensagens, setMensagens] = useState<readonly Mensagem[]>([]);
  const [carregandoLista, setCarregandoLista] = useState(true);
  const [carregandoMensagens, setCarregandoMensagens] = useState(false);

  const recarregarLista = useCallback(async () => {
    try {
      setConversas(await conversasApi.listar());
    } finally {
      setCarregandoLista(false);
    }
  }, []);

  const recarregarMensagens = useCallback(async (conversaId: string) => {
    setCarregandoMensagens(true);
    try {
      setMensagens(await conversasApi.mensagens(conversaId));
    } finally {
      setCarregandoMensagens(false);
    }
  }, []);

  useEffect(() => {
    void recarregarLista();
  }, [recarregarLista]);

  useEffect(() => {
    if (selecionada) void recarregarMensagens(selecionada);
    else setMensagens([]);
  }, [selecionada, recarregarMensagens]);

  /**
   * O push traz o mínimo para atualizar a tela. Em vez de montar uma Mensagem
   * a partir dele — o que exigiria adivinhar campos que o push não carrega —
   * recarregamos do REST. Custa uma requisição e elimina uma classe inteira de
   * bug em que a mensagem exibida difere da gravada.
   */
  const aoReceberMensagem = useCallback(() => {
    void recarregarLista();
    if (selecionada) void recarregarMensagens(selecionada);
  }, [recarregarLista, recarregarMensagens, selecionada]);

  const aoSincronizar = useCallback(() => {
    void recarregarLista();
    if (selecionada) void recarregarMensagens(selecionada);
  }, [recarregarLista, recarregarMensagens, selecionada]);

  const conexao = useTempoReal({
    tenantId: usuario?.tenantId ?? null,
    conversaAtiva: selecionada,
    aoReceberMensagem,
    aoSincronizar,
  });

  const enviar = useCallback(
    async (texto: string) => {
      if (!selecionada) return;
      await conversasApi.enviar(selecionada, texto);
      // Recarrega em vez de acrescentar localmente: a mensagem nasce PENDING no
      // backend e o status muda depois, quando o worker a entrega. Espelhar
      // isso à mão duplicaria a máquina de estados no frontend.
      await recarregarMensagens(selecionada);
      await recarregarLista();
    },
    [selecionada, recarregarMensagens, recarregarLista],
  );

  const conversaAberta = conversas.find((c) => c.id === selecionada);

  return (
    <div className="flex h-full flex-col">
      <header
        className="flex shrink-0 items-center justify-between border-b px-6 py-3"
        style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
      >
        <h1 className="text-base font-semibold">Caixa de entrada</h1>
        <IndicadorDeConexao estado={conexao} />
      </header>

      <div className="flex min-h-0 flex-1">
        <section
          className="flex w-80 shrink-0 flex-col overflow-y-auto border-r"
          style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
          aria-label="Conversas"
        >
          <ListaDeConversas
            conversas={conversas}
            selecionada={selecionada}
            aoSelecionar={setSelecionada}
            carregando={carregandoLista}
          />
        </section>

        <section className="flex min-w-0 flex-1 flex-col" aria-label="Conversa selecionada">
          {conversaAberta ? (
            <>
              <div
                className="shrink-0 border-b px-6 py-3"
                style={{
                  borderColor: 'var(--border-subtle)',
                  backgroundColor: 'var(--surface-raised)',
                }}
              >
                <h2 className="text-sm font-semibold">
                  {conversaAberta.contatoNome ?? 'Contato sem nome'}
                </h2>
              </div>
              <div className="min-h-0 flex-1">
                <Conversa
                  mensagens={mensagens}
                  carregando={carregandoMensagens}
                  aoEnviar={enviar}
                />
              </div>
            </>
          ) : (
            <div className="flex h-full items-center justify-center p-6 text-center">
              <p className="max-w-sm text-sm" style={{ color: 'var(--text-muted)' }}>
                Selecione uma conversa à esquerda para ver o histórico e responder.
              </p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

/**
 * Estado da conexão visível o tempo todo.
 *
 * <p>Sem isto, uma queda do WebSocket deixa a tela parada e o atendente conclui
 * que ninguém está mandando mensagem — enquanto as mensagens chegam e ficam no
 * banco. O modo de falha silencioso é pior que a falha.
 */
function IndicadorDeConexao({ estado }: { readonly estado: EstadoConexao }) {
  const textos: Record<EstadoConexao, string> = {
    conectando: 'Conectando…',
    conectado: 'Tempo real ativo',
    desconectado: 'Sem tempo real — recarregue para ver novidades',
  };

  const cores: Record<EstadoConexao, string> = {
    conectando: 'var(--warning)',
    conectado: 'var(--success)',
    desconectado: 'var(--danger)',
  };

  return (
    <span className="flex items-center gap-2 text-xs" style={{ color: cores[estado] }}>
      <span
        aria-hidden="true"
        className="inline-block size-2 rounded-full"
        style={{ backgroundColor: 'currentColor' }}
      />
      {/* O texto acompanha a bolinha: cor sozinha não comunica estado. */}
      <span role="status">{textos[estado]}</span>
    </span>
  );
}
