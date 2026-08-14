import { useState } from 'react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Tarefa } from '@/shared/crm/tipos';
import { formatarDataHora } from '@/shared/formato';
import { Carregando, Pagina, Vazio } from '@/shared/components/Pagina';
import {
  useAlternarConclusaoDeTarefa,
  useCriarTarefa,
  useExcluirTarefa,
  useTarefas,
} from '@/shared/server-state/recursos';
import { FormularioDeTarefa } from './FormularioDeTarefa';

export function TasksPage() {
  const [apenasAbertas, setApenasAbertas] = useState(true);
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const tarefasQuery = useTarefas(apenasAbertas);
  const criarTarefa = useCriarTarefa();
  const alternarConclusao = useAlternarConclusaoDeTarefa();
  const excluirTarefa = useExcluirTarefa();
  const tarefas = tarefasQuery.data ?? [];

  return (
    <Pagina
      titulo="Tarefas"
      descricao={apenasAbertas ? 'Em aberto' : 'Todas'}
      acoes={
        <>
          <Button variant="secundario" onClick={() => setApenasAbertas((v) => !v)}>
            {apenasAbertas ? 'Mostrar todas' : 'Só as abertas'}
          </Button>
          <Button onClick={() => setFormAberto((v) => !v)}>
            {formAberto ? 'Cancelar' : 'Nova tarefa'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {(erro || tarefasQuery.isError) && (
          <AlertaErro>{erro ?? 'Não foi possível carregar as tarefas.'}</AlertaErro>
        )}
        {formAberto && (
          <FormularioDeTarefa
            aoSalvar={async (dados) => {
              try {
                setErro(null);
                await criarTarefa.mutateAsync(dados);
                setFormAberto(false);
              } catch {
                setErro('Não foi possível criar a tarefa.');
              }
            }}
          />
        )}

        {tarefasQuery.isPending ? (
          <Carregando />
        ) : tarefas.length === 0 ? (
          <Vazio
            tipo={apenasAbertas ? 'sem-resultados' : 'vazio-inicial'}
            titulo={apenasAbertas ? 'Nenhuma tarefa em aberto' : 'Nenhuma tarefa'}
            descricao="Crie uma tarefa para acompanhar um follow-up ou um compromisso."
          />
        ) : (
          <ul className="space-y-2">
            {tarefas.map((tarefa) => (
              <ItemDeTarefa
                key={tarefa.id}
                tarefa={tarefa}
                aoAlternar={async () => {
                  try {
                    setErro(null);
                    await alternarConclusao.mutateAsync(tarefa.id);
                  } catch {
                    setErro('Não foi possível alterar a tarefa. A lista foi restaurada.');
                  }
                }}
                aoExcluir={async () => {
                  if (!window.confirm(`Excluir a tarefa "${tarefa.titulo}"?`)) return;
                  try {
                    setErro(null);
                    await excluirTarefa.mutateAsync(tarefa.id);
                  } catch {
                    setErro('Não foi possível excluir a tarefa.');
                  }
                }}
              />
            ))}
          </ul>
        )}
      </div>
    </Pagina>
  );
}

function ItemDeTarefa({
  tarefa,
  aoAlternar,
  aoExcluir,
}: {
  readonly tarefa: Tarefa;
  readonly aoAlternar: () => void;
  readonly aoExcluir: () => void;
}) {
  const concluida = tarefa.concluidaEm !== null;
  // "Atrasada" só faz sentido para tarefa aberta com vencimento no passado.
  const atrasada =
    !concluida && tarefa.vencimentoEm !== null && new Date(tarefa.vencimentoEm) < new Date();

  return (
    <li
      className="flex items-start gap-3 rounded-[var(--radius-surface)] border p-3"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <label className="flex size-11 shrink-0 items-start justify-center pt-1">
        <input
          type="checkbox"
          checked={concluida}
          onChange={aoAlternar}
          className="size-6"
          aria-label={concluida ? `Reabrir ${tarefa.titulo}` : `Concluir ${tarefa.titulo}`}
        />
      </label>

      <div className="min-w-0 flex-1">
        <p
          className="text-sm font-medium"
          style={{
            color: concluida ? 'var(--text-muted)' : 'var(--text-strong)',
            textDecoration: concluida ? 'line-through' : undefined,
          }}
        >
          {tarefa.titulo}
        </p>

        {tarefa.descricao && (
          <p className="mt-0.5 text-sm" style={{ color: 'var(--text-muted)' }}>
            {tarefa.descricao}
          </p>
        )}

        <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
          Responsável: {formatarResponsavel(tarefa)}
        </p>

        {tarefa.vencimentoEm && (
          <p
            className="mt-1 text-xs tabular-nums"
            style={{ color: atrasada ? 'var(--danger)' : 'var(--text-muted)' }}
          >
            {/* O rótulo "Atrasada" acompanha a cor: status por cor sozinha não
                existe para leitor de tela. */}
            {atrasada ? 'Atrasada — ' : 'Vence em '}
            {formatarDataHora(tarefa.vencimentoEm)}
          </p>
        )}
      </div>

      <Button variant="fantasma" size="pequeno" onClick={aoExcluir}>
        Excluir
      </Button>
    </li>
  );
}
