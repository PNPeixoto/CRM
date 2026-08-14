import { useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { FUSO_DE_NEGOCIO, instanteDeHorarioLocal } from '@/shared/formato';

export interface DadosDeNovaTarefa {
  readonly titulo: string;
  readonly descricao?: string;
  readonly vencimentoEm?: string | null;
}

export function FormularioDeTarefa({
  aoSalvar,
  vencimentoInicial = '',
  rotuloBotao = 'Criar tarefa',
}: {
  readonly aoSalvar: (dados: DadosDeNovaTarefa) => Promise<void>;
  readonly vencimentoInicial?: string;
  readonly rotuloBotao?: string;
}) {
  const [titulo, setTitulo] = useState('');
  const [descricao, setDescricao] = useState('');
  const [vencimento, setVencimento] = useState(vencimentoInicial);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setEnviando(true);
    try {
      await aoSalvar({
        titulo: titulo.trim(),
        descricao: descricao.trim() || undefined,
        vencimentoEm: vencimento
          ? instanteDeHorarioLocal(vencimento, FUSO_DE_NEGOCIO)
          : null,
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
      className="grid gap-3 rounded-[var(--radius-surface)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4 sm:grid-cols-3"
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
        <Label htmlFor="vencimento">Data e horário</Label>
        <Input
          id="vencimento"
          type="datetime-local"
          value={vencimento}
          onChange={(e) => setVencimento(e.target.value)}
        />
      </div>

      <div className="sm:col-span-3">
        <Button type="submit" disabled={enviando || titulo.trim().length === 0}>
          {enviando ? 'Salvando…' : rotuloBotao}
        </Button>
      </div>
    </form>
  );
}
