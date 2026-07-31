import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { tarefasApi } from '@/shared/crm/api';
import type { Tarefa } from '@/shared/crm/tipos';
import { formatarDataHora } from '@/shared/formato';
import { Carregando, Pagina, Vazio } from '@/shared/components/Pagina';

export function TasksPage() {
  const [tarefas, setTarefas] = useState<readonly Tarefa[]>([]);
  const [apenasAbertas, setApenasAbertas] = useState(true);
  const [carregando, setCarregando] = useState(true);
  const [formAberto, setFormAberto] = useState(false);

  const carregar = useCallback(async (somenteAbertas: boolean) => {
    setCarregando(true);
    try {
      setTarefas(await tarefasApi.listar(somenteAbertas));
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar(apenasAbertas);
  }, [apenasAbertas, carregar]);

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
        {formAberto && (
          <FormularioDeTarefa
            aoSalvar={async (dados) => {
              await tarefasApi.criar(dados);
              setFormAberto(false);
              await carregar(apenasAbertas);
            }}
          />
        )}

        {carregando ? (
          <Carregando />
        ) : tarefas.length === 0 ? (
          <Vazio
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
                  await tarefasApi.alternarConclusao(tarefa.id);
                  await carregar(apenasAbertas);
                }}
                aoExcluir={async () => {
                  if (!window.confirm(`Excluir a tarefa "${tarefa.titulo}"?`)) return;
                  await tarefasApi.excluir(tarefa.id);
                  await carregar(apenasAbertas);
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
      <input
        type="checkbox"
        checked={concluida}
        onChange={aoAlternar}
        className="mt-1 size-4 shrink-0"
        aria-label={concluida ? `Reabrir ${tarefa.titulo}` : `Concluir ${tarefa.titulo}`}
      />

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

function FormularioDeTarefa({
  aoSalvar,
}: {
  readonly aoSalvar: (dados: {
    titulo: string;
    descricao?: string;
    vencimentoEm?: string | null;
  }) => Promise<void>;
}) {
  const [titulo, setTitulo] = useState('');
  const [descricao, setDescricao] = useState('');
  const [vencimento, setVencimento] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setEnviando(true);
    try {
      await aoSalvar({
        titulo: titulo.trim(),
        descricao: descricao.trim() || undefined,
        // datetime-local devolve hora local sem fuso; o toISOString converte
        // para UTC, que é como o backend guarda.
        vencimentoEm: vencimento ? new Date(vencimento).toISOString() : null,
      });
      setTitulo('');
      setDescricao('');
      setVencimento('');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form
      onSubmit={enviar}
      className="grid gap-3 rounded-[var(--radius-surface)] border p-4 sm:grid-cols-3"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <div className="space-y-1.5">
        <Label htmlFor="titulo">Título</Label>
        <Input id="titulo" value={titulo} onChange={(e) => setTitulo(e.target.value)} required />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="descricao">Descrição</Label>
        <Input id="descricao" value={descricao} onChange={(e) => setDescricao(e.target.value)} />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="vencimento">Vencimento</Label>
        <Input
          id="vencimento"
          type="datetime-local"
          value={vencimento}
          onChange={(e) => setVencimento(e.target.value)}
        />
      </div>

      <div className="sm:col-span-3">
        <Button type="submit" disabled={enviando || titulo.trim().length === 0}>
          {enviando ? 'Salvando…' : 'Criar tarefa'}
        </Button>
      </div>
    </form>
  );
}
