import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { funilApi } from '@/shared/crm/api';
import { formatarResponsavel } from '@/shared/crm/responsavel';
import type { Etapa, Funil, Oportunidade } from '@/shared/crm/tipos';
import { formatarMoeda, paraCentavos } from '@/shared/formato';
import { Carregando, Pagina } from '@/shared/components/Pagina';

/**
 * Kanban do funil.
 *
 * <p>O backend deriva o status da etapa: arrastar para uma coluna marcada como
 * ganho fecha a oportunidade. A tela não escolhe status — se escolhesse,
 * daria para ter um card em "Ganho" que o relatório ainda conta como aberto.
 */
export function PipelinesPage() {
  const [funis, setFunis] = useState<readonly Funil[]>([]);
  const [funilAtivo, setFunilAtivo] = useState<Funil | null>(null);
  const [oportunidades, setOportunidades] = useState<readonly Oportunidade[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    funilApi
      .listarFunis()
      .then((lista) => {
        setFunis(lista);
        setFunilAtivo(lista.find((f) => f.padrao) ?? lista[0] ?? null);
      })
      .catch(() => setErro('Não foi possível carregar os funis.'))
      .finally(() => setCarregando(false));
  }, []);

  const carregarOportunidades = useCallback(async (funilId: string) => {
    setOportunidades(await funilApi.listarOportunidades(funilId));
  }, []);

  useEffect(() => {
    if (funilAtivo) void carregarOportunidades(funilAtivo.id);
  }, [funilAtivo, carregarOportunidades]);

  async function mover(oportunidadeId: string, etapaId: string) {
    if (!funilAtivo) return;
    await funilApi.mover(oportunidadeId, etapaId);
    // Recarrega em vez de mover o card localmente: o status é decidido no
    // backend, e espelhar essa regra aqui duplicaria a máquina de estados.
    await carregarOportunidades(funilAtivo.id);
  }

  if (carregando) {
    return (
      <Pagina titulo="Funil">
        <Carregando />
      </Pagina>
    );
  }

  return (
    <Pagina
      titulo="Funil"
      descricao={funilAtivo?.nome}
      acoes={
        <>
          {funis.length > 1 && (
            <Select
              value={funilAtivo?.id ?? ''}
              onChange={(e) => setFunilAtivo(funis.find((f) => f.id === e.target.value) ?? null)}
              aria-label="Selecionar funil"
            >
              {funis.map((funil) => (
                <option key={funil.id} value={funil.id}>
                  {funil.nome}
                </option>
              ))}
            </Select>
          )}
          <Button onClick={() => setFormAberto((aberto) => !aberto)} disabled={!funilAtivo}>
            {formAberto ? 'Cancelar' : 'Nova oportunidade'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {erro && <AlertaErro>{erro}</AlertaErro>}

        {formAberto && funilAtivo && (
          <FormularioDeOportunidade
            etapas={funilAtivo.etapas}
            aoSalvar={async (dados) => {
              await funilApi.criar(dados);
              setFormAberto(false);
              await carregarOportunidades(funilAtivo.id);
            }}
          />
        )}

        {funilAtivo && (
          // Rolagem horizontal no contêiner do kanban, não na página: com
          // muitas etapas, o corpo inteiro deslizaria e o cabeçalho sairia
          // da tela.
          <div className="flex gap-3 overflow-x-auto pb-2">
            {funilAtivo.etapas.map((etapa) => (
              <Coluna
                key={etapa.id}
                etapa={etapa}
                oportunidades={oportunidades.filter((o) => o.etapaId === etapa.id)}
                etapas={funilAtivo.etapas}
                aoMover={mover}
              />
            ))}
          </div>
        )}
      </div>
    </Pagina>
  );
}

function Coluna({
  etapa,
  oportunidades,
  etapas,
  aoMover,
}: {
  readonly etapa: Etapa;
  readonly oportunidades: readonly Oportunidade[];
  readonly etapas: readonly Etapa[];
  readonly aoMover: (oportunidadeId: string, etapaId: string) => void;
}) {
  const total = oportunidades.reduce((soma, o) => soma + o.valorCentavos, 0);

  return (
    <section
      className="flex w-72 shrink-0 flex-col rounded-[var(--radius-surface)] border"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-sunken)' }}
      aria-label={`Etapa ${etapa.nome}`}
    >
      <header className="border-b px-3 py-2" style={{ borderColor: 'var(--border-subtle)' }}>
        <div className="flex items-center justify-between gap-2">
          <h2 className="truncate text-sm font-semibold">{etapa.nome}</h2>
          {/* Texto, não só cor: etapa terminal precisa ser legível por quem
              não distingue as cores e por leitor de tela. */}
          {(etapa.ganho || etapa.perda) && (
            <span
              className="shrink-0 rounded px-1.5 py-px text-[10px] font-medium uppercase"
              style={{
                color: etapa.ganho ? 'var(--success)' : 'var(--danger)',
                backgroundColor: etapa.ganho ? 'var(--success-soft)' : 'var(--danger-soft)',
              }}
            >
              {etapa.ganho ? 'ganho' : 'perda'}
            </span>
          )}
        </div>
        <p className="mt-0.5 text-xs tabular-nums" style={{ color: 'var(--text-muted)' }}>
          {oportunidades.length} · {formatarMoeda(total)}
        </p>
      </header>

      <div className="flex min-h-24 flex-col gap-2 p-2">
        {oportunidades.map((oportunidade) => (
          <article
            key={oportunidade.id}
            className="rounded-[var(--radius-control)] border p-2.5"
            style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
          >
            <p className="text-sm font-medium">{oportunidade.titulo}</p>
            <p className="mt-0.5 text-sm tabular-nums" style={{ color: 'var(--text-muted)' }}>
              {formatarMoeda(oportunidade.valorCentavos)}
            </p>
            <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
              Responsável: {formatarResponsavel(oportunidade)}
            </p>

            {/*
              Mover por <select> em vez de arrastar-e-soltar. Drag and drop sem
              alternativa de teclado é inacessível, e a versão acessível é
              trabalho próprio — este controle funciona para todos hoje e não
              impede o arraste depois.
            */}
            <label className="sr-only" htmlFor={`mover-${oportunidade.id}`}>
              Mover {oportunidade.titulo} para outra etapa
            </label>
            <Select
              id={`mover-${oportunidade.id}`}
              tamanho="compacto"
              className="mt-2 w-full"
              value={oportunidade.etapaId}
              onChange={(e) => aoMover(oportunidade.id, e.target.value)}
            >
              {etapas.map((destino) => (
                <option key={destino.id} value={destino.id}>
                  {destino.nome}
                </option>
              ))}
            </Select>
          </article>
        ))}
      </div>
    </section>
  );
}

function FormularioDeOportunidade({
  etapas,
  aoSalvar,
}: {
  readonly etapas: readonly Etapa[];
  readonly aoSalvar: (dados: {
    titulo: string;
    etapaId: string;
    valorCentavos: number;
  }) => Promise<void>;
}) {
  const [titulo, setTitulo] = useState('');
  const [valor, setValor] = useState('');
  const [etapaId, setEtapaId] = useState(etapas[0]?.id ?? '');
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setEnviando(true);
    try {
      // A conversão para centavos acontece aqui, na borda. O valor viaja
      // como inteiro daqui até o banco.
      await aoSalvar({ titulo: titulo.trim(), etapaId, valorCentavos: paraCentavos(valor) });
      setTitulo('');
      setValor('');
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
        <Label htmlFor="valor">Valor (R$)</Label>
        <Input
          id="valor"
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          inputMode="decimal"
          placeholder="0,00"
        />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="etapa">Etapa</Label>
        <Select
          id="etapa"
          className="w-full"
          value={etapaId}
          onChange={(e) => setEtapaId(e.target.value)}
        >
          {etapas.map((etapa) => (
            <option key={etapa.id} value={etapa.id}>
              {etapa.nome}
            </option>
          ))}
        </Select>
      </div>

      <div className="sm:col-span-3">
        <Button type="submit" disabled={enviando || titulo.trim().length === 0}>
          {enviando ? 'Salvando…' : 'Criar oportunidade'}
        </Button>
      </div>
    </form>
  );
}
