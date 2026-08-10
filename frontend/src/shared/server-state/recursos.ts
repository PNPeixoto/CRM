import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
  type QueryKey,
  type InfiniteData,
} from '@tanstack/react-query';
import { conversasApi } from '@/shared/conversas/api';
import type { CursorTemporal, Mensagem, PaginaMensagens } from '@/shared/conversas/tipos';
import { canaisApi, contatosApi, funilApi, relatoriosApi, tarefasApi } from '@/shared/crm/api';
import type { Tarefa } from '@/shared/crm/tipos';
import {
  obterApresentacao,
  obterContextos,
  obterPermissoes,
  salvarPerfilInicial,
} from '@/shared/tenant/api';
import type { SegmentoDeNegocio } from '@/shared/tenant/tipos';
import { auditoriaApi, type FiltrosAuditoria } from '@/shared/audit/api';
import {
  chaveDoRecurso,
  contextoSeguro,
  prefixoDoRecurso,
  type ContextoDeEstadoServidor,
  type RecursoDeServidor,
} from './chaves';
import { useContextoEstadoServidor } from './contexto';

function contextoObrigatorio(contexto: ContextoDeEstadoServidor | null) {
  if (!contexto) throw new Error('A operação exige uma sessão autenticada.');
  return contexto;
}

async function invalidar(
  cliente: QueryClient,
  contexto: ContextoDeEstadoServidor,
  recursos: readonly RecursoDeServidor[],
) {
  await Promise.all(recursos.map((recurso) => cliente.invalidateQueries({
    queryKey: prefixoDoRecurso(contexto, recurso),
  })));
}

export function useApresentacaoDoTenant() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'apresentacao'),
    queryFn: ({ signal }) => obterApresentacao(signal),
    enabled: Boolean(contexto),
    staleTime: 5 * 60_000,
  });
}

export function usePermissoesDoUsuario() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'permissoes'),
    queryFn: ({ signal }) => obterPermissoes(signal),
    enabled: Boolean(contexto),
    staleTime: 30_000,
  });
}

export function useContextosOrganizacionais() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'contextos-organizacionais'),
    queryFn: ({ signal }) => obterContextos(signal),
    enabled: Boolean(contexto),
    staleTime: 30_000,
  });
}

export function useSalvarPerfilInicial() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: (segmento: SegmentoDeNegocio) => salvarPerfilInicial(segmento),
    onSuccess: (apresentacao) => {
      const seguro = contextoObrigatorio(contexto);
      cliente.setQueryData(chaveDoRecurso(seguro, 'apresentacao'), apresentacao);
    },
  });
}

export function useContatos(busca: string, pagina?: number, tamanho?: number) {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  const parametros = pagina === undefined ? { busca } : { busca, pagina, tamanho };
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'contatos', parametros),
    queryFn: ({ signal }) => contatosApi.listar(busca || undefined, signal, pagina, tamanho),
    enabled: Boolean(contexto),
  });
}

export function useCriarContato() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: contatosApi.criar,
    onSuccess: () => invalidar(cliente, contextoObrigatorio(contexto), ['contatos', 'visao-geral']),
  });
}

export function useExcluirContato() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: contatosApi.excluir,
    onSuccess: () => invalidar(cliente, contextoObrigatorio(contexto), ['contatos', 'visao-geral']),
  });
}

export function useFunis() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'funis'),
    queryFn: ({ signal }) => funilApi.listarFunis(signal),
    enabled: Boolean(contexto),
    staleTime: 60_000,
  });
}

export function useOportunidades(funilId: string | null) {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'oportunidades', { funilId }),
    queryFn: ({ signal }) => funilApi.listarOportunidades(funilId ?? '', signal),
    enabled: Boolean(contexto && funilId),
  });
}

export function useCriarOportunidade() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: funilApi.criar,
    onSuccess: () => invalidar(
      cliente,
      contextoObrigatorio(contexto),
      ['oportunidades', 'visao-geral'],
    ),
  });
}

export function useMoverOportunidade() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: ({ id, etapaId }: { readonly id: string; readonly etapaId: string }) =>
      funilApi.mover(id, etapaId),
    onSuccess: () => invalidar(
      cliente,
      contextoObrigatorio(contexto),
      ['oportunidades', 'visao-geral'],
    ),
  });
}

export function useTarefas(apenasAbertas: boolean) {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'tarefas', { apenasAbertas }),
    queryFn: ({ signal }) => tarefasApi.listar(apenasAbertas, signal),
    enabled: Boolean(contexto),
  });
}

export function useCriarTarefa() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: tarefasApi.criar,
    onSuccess: () => invalidar(cliente, contextoObrigatorio(contexto), ['tarefas', 'visao-geral']),
  });
}

export function useExcluirTarefa() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: tarefasApi.excluir,
    onSuccess: () => invalidar(cliente, contextoObrigatorio(contexto), ['tarefas', 'visao-geral']),
  });
}

type SnapshotDeTarefas = readonly (readonly [QueryKey, readonly Tarefa[] | undefined])[];

function substituirTarefa(
  tarefas: readonly Tarefa[] | undefined,
  atualizada: Tarefa,
  apenasAbertas: boolean,
) {
  if (!tarefas) return tarefas;
  const semAtual = tarefas.filter((tarefa) => tarefa.id !== atualizada.id);
  if (apenasAbertas && atualizada.concluidaEm) return semAtual;
  return [...semAtual, atualizada];
}

export function useAlternarConclusaoDeTarefa() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: tarefasApi.alternarConclusao,
    onMutate: async (id): Promise<SnapshotDeTarefas> => {
      const seguro = contextoObrigatorio(contexto);
      const prefixo = prefixoDoRecurso(seguro, 'tarefas');
      await cliente.cancelQueries({ queryKey: prefixo });
      const snapshot = cliente.getQueriesData<readonly Tarefa[]>({ queryKey: prefixo });
      const existente = snapshot.flatMap(([, tarefas]) => tarefas ?? [])
        .find((tarefa) => tarefa.id === id);
      if (!existente) return snapshot;
      const otimista: Tarefa = {
        ...existente,
        concluidaEm: existente.concluidaEm ? null : new Date().toISOString(),
      };
      for (const [chave, tarefas] of snapshot) {
        const parametros = chave.at(-1) as { apenasAbertas?: boolean };
        cliente.setQueryData(
          chave,
          substituirTarefa(tarefas, otimista, parametros.apenasAbertas === true),
        );
      }
      return snapshot;
    },
    onError: (_erro, _id, snapshot) => {
      for (const [chave, tarefas] of snapshot ?? []) cliente.setQueryData(chave, tarefas);
    },
    onSuccess: (atualizada) => {
      const seguro = contextoObrigatorio(contexto);
      const consultas = cliente.getQueriesData<readonly Tarefa[]>({
        queryKey: prefixoDoRecurso(seguro, 'tarefas'),
      });
      for (const [chave, tarefas] of consultas) {
        const parametros = chave.at(-1) as { apenasAbertas?: boolean };
        cliente.setQueryData(
          chave,
          substituirTarefa(tarefas, atualizada, parametros.apenasAbertas === true),
        );
      }
    },
    onSettled: () => invalidar(
      cliente,
      contextoObrigatorio(contexto),
      ['tarefas', 'visao-geral'],
    ),
  });
}

export function useCanais() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'canais'),
    queryFn: ({ signal }) => canaisApi.listar(signal),
    enabled: Boolean(contexto),
    // A reconciliacao do provedor ocorre fora da requisicao de criacao. A
    // atualizacao curta deixa a tela confirmar o webhook sem recarregar.
    refetchInterval: 10_000,
  });
}

export function useAuditoria(filtros: FiltrosAuditoria, cursor: string | null) {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'auditoria', { ...filtros, cursor }),
    queryFn: ({ signal }) => auditoriaApi.listar(filtros, cursor, signal),
    enabled: Boolean(contexto),
  });
}

export function useCriarCanal() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: canaisApi.criar,
    onSuccess: () => invalidar(cliente, contextoObrigatorio(contexto), ['canais', 'visao-geral']),
  });
}

export function useAlternarCanal() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: canaisApi.alternarAtivacao,
    onSuccess: () => invalidar(cliente, contextoObrigatorio(contexto), ['canais', 'visao-geral']),
  });
}

export function useVisaoGeral() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useQuery({
    queryKey: chaveDoRecurso(seguro, 'visao-geral'),
    queryFn: ({ signal }) => relatoriosApi.visaoGeral(signal),
    enabled: Boolean(contexto),
    staleTime: 30_000,
  });
}

export function useConversas() {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useInfiniteQuery({
    queryKey: chaveDoRecurso(seguro, 'conversas'),
    queryFn: ({ signal, pageParam }) => conversasApi.listar(pageParam, signal),
    initialPageParam: undefined as CursorTemporal | undefined,
    getNextPageParam: (pagina) => pagina.proximoCursor ?? undefined,
    enabled: Boolean(contexto),
    staleTime: 10_000,
  });
}

export function useMensagens(conversaId: string | null) {
  const contexto = useContextoEstadoServidor();
  const seguro = contextoSeguro(contexto);
  return useInfiniteQuery({
    queryKey: chaveDoRecurso(seguro, 'mensagens', { conversaId }),
    queryFn: ({ signal, pageParam }) =>
      conversasApi.mensagens(conversaId ?? '', pageParam, signal),
    initialPageParam: undefined as CursorTemporal | undefined,
    getNextPageParam: (pagina) => pagina.proximoCursor ?? undefined,
    enabled: Boolean(contexto && conversaId),
    staleTime: 5_000,
  });
}

export function useEnviarMensagem() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return useMutation({
    mutationFn: ({ conversaId, texto }: { readonly conversaId: string; readonly texto: string }) =>
      conversasApi.enviar(conversaId, texto),
    onSuccess: (mensagem: Mensagem, variaveis) => {
      const seguro = contextoObrigatorio(contexto);
      const chave = chaveDoRecurso(seguro, 'mensagens', { conversaId: variaveis.conversaId });
      cliente.setQueryData<InfiniteData<PaginaMensagens, CursorTemporal | undefined>>(
        chave,
        (atuais) => {
          if (!atuais || atuais.pages.some((pagina) =>
            pagina.itens.some((item) => item.id === mensagem.id))) return atuais;
          const [primeira, ...demais] = atuais.pages;
          if (!primeira) return atuais;
          return {
            ...atuais,
            pages: [{ ...primeira, itens: [...primeira.itens, mensagem] }, ...demais],
          };
        },
      );
      return invalidar(cliente, seguro, ['mensagens', 'conversas']);
    },
  });
}

export function useSincronizacaoDaInbox() {
  const contexto = useContextoEstadoServidor();
  const cliente = useQueryClient();
  return (conversaId: string | null) => {
    if (!contexto) return;
    void cliente.invalidateQueries({ queryKey: prefixoDoRecurso(contexto, 'conversas') });
    if (conversaId) {
      void cliente.invalidateQueries({
        queryKey: chaveDoRecurso(contexto, 'mensagens', { conversaId }),
      });
    }
  };
}
