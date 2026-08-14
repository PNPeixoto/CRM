import { useState, type FormEvent } from 'react';
import { Plus } from 'lucide-react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import type { Etapa } from '@/shared/crm/tipos';
import { formatarMoeda, paraCentavos } from '@/shared/formato';
import { Carregando, Pagina } from '@/shared/components/Pagina';
import {
  useCriarOportunidade,
  useFunis,
  useMoverOportunidade,
  useOportunidades,
} from '@/shared/server-state/recursos';
import { KanbanBoard } from './KanbanBoard';

/**
 * Kanban do funil.
 *
 * <p>O backend deriva o status da etapa: arrastar para uma coluna marcada como
 * ganho fecha a oportunidade. A tela não escolhe status — se escolhesse,
 * daria para ter um card em "Ganho" que o relatório ainda conta como aberto.
 */
export function PipelinesPage() {
  const [funilAtivoId, setFunilAtivoId] = useState<string | null>(null);
  const [formAberto, setFormAberto] = useState(false);
  const [etapaInicialId, setEtapaInicialId] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const funisQuery = useFunis();
  const funis = funisQuery.data ?? [];
  const funilAtivo = funis.find((funil) => funil.id === funilAtivoId)
    ?? funis.find((funil) => funil.padrao)
    ?? funis[0]
    ?? null;
  const oportunidadesQuery = useOportunidades(funilAtivo?.id ?? null);
  const oportunidades = oportunidadesQuery.data ?? [];
  const criarOportunidade = useCriarOportunidade();
  const moverOportunidade = useMoverOportunidade();
  const oportunidadesAbertas = oportunidades.filter((oportunidade) => oportunidade.status === 'OPEN');
  const valorAberto = oportunidadesAbertas.reduce(
    (total, oportunidade) => total + oportunidade.valorCentavos,
    0,
  );

  function abrirFormulario(etapaId: string | undefined) {
    if (!etapaId) return;
    setEtapaInicialId(etapaId);
    setFormAberto(true);
  }

  async function mover(oportunidadeId: string, etapaId: string) {
    if (!funilAtivo || moverOportunidade.isPending) return;
    try {
      setErro(null);
      await moverOportunidade.mutateAsync({ id: oportunidadeId, etapaId });
    } catch {
      setErro('Não foi possível mover a oportunidade.');
    }
  }

  if (funisQuery.isPending) {
    return (
      <Pagina titulo="Funil">
        <Carregando />
      </Pagina>
    );
  }

  return (
    <Pagina
      titulo="Funil comercial"
      descricao={funilAtivo
        ? `${funilAtivo.nome} · ${oportunidadesAbertas.length} ${oportunidadesAbertas.length === 1 ? 'oportunidade aberta' : 'oportunidades abertas'} · ${formatarMoeda(valorAberto)} em pipeline`
        : undefined}
      acoes={
        <>
          {funis.length > 1 && (
            <Select
              value={funilAtivo?.id ?? ''}
              onChange={(e) => setFunilAtivoId(e.target.value)}
              aria-label="Selecionar funil"
            >
              {funis.map((funil) => (
                <option key={funil.id} value={funil.id}>
                  {funil.nome}
                </option>
              ))}
            </Select>
          )}
          <Button
            onClick={() => formAberto
              ? setFormAberto(false)
              : abrirFormulario(funilAtivo?.etapas[0]?.id)}
            disabled={!funilAtivo}
          >
            {!formAberto && <Plus aria-hidden="true" />}
            {formAberto ? 'Cancelar' : 'Nova oportunidade'}
          </Button>
        </>
      }
    >
      <div className="min-w-0 space-y-4 overflow-x-hidden">
        {(erro || funisQuery.isError || oportunidadesQuery.isError) && (
          <AlertaErro>{erro ?? 'Não foi possível carregar o funil.'}</AlertaErro>
        )}

        {formAberto && funilAtivo && (
          <FormularioDeOportunidade
            key={etapaInicialId}
            etapas={funilAtivo.etapas}
            etapaInicialId={etapaInicialId ?? funilAtivo.etapas[0]?.id ?? ''}
            aoSalvar={async (dados) => {
              try {
                setErro(null);
                await criarOportunidade.mutateAsync(dados);
                setFormAberto(false);
              } catch {
                setErro('Não foi possível criar a oportunidade.');
              }
            }}
          />
        )}

        {funilAtivo && (
          <KanbanBoard
            etapas={funilAtivo.etapas}
            oportunidades={oportunidades}
            oportunidadeEmMovimento={moverOportunidade.isPending
              ? moverOportunidade.variables.id
              : null}
            aoMover={(oportunidadeId, etapaId) => void mover(oportunidadeId, etapaId)}
            aoAdicionar={abrirFormulario}
          />
        )}
      </div>
    </Pagina>
  );
}

function FormularioDeOportunidade({
  etapas,
  etapaInicialId,
  aoSalvar,
}: {
  readonly etapas: readonly Etapa[];
  readonly etapaInicialId: string;
  readonly aoSalvar: (dados: {
    titulo: string;
    etapaId: string;
    valorCentavos: number;
  }) => Promise<void>;
}) {
  const [titulo, setTitulo] = useState('');
  const [valor, setValor] = useState('');
  const [etapaId, setEtapaId] = useState(etapaInicialId);
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
