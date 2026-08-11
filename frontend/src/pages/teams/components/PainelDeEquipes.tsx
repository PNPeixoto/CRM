import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Cartao, Carregando, Vazio } from '@/shared/components/Pagina';
import type { Equipe, Membro } from '@/shared/organizacao/tipos';

/**
 * Quem responde a quem.
 *
 * <p>É o que dá sentido ao alcance "a equipe dele": o papel diz o que a pessoa
 * pode fazer, e a equipe diz sobre quem. Um papel de equipe sem composição
 * enxerga apenas o próprio responsável — correto e inútil, e é por isso que a
 * tela avisa quando isso acontece.
 */
export function PainelDeEquipes({
  equipes,
  membros,
  carregando,
  aoIncluir,
  aoRemover,
}: {
  readonly equipes: readonly Equipe[];
  readonly membros: readonly Membro[];
  readonly carregando: boolean;
  readonly aoIncluir: (entrada: { gestorId: string; usuarioId: string }) => void;
  readonly aoRemover: (entrada: { gestorId: string; usuarioId: string }) => void;
}) {
  if (carregando) return <Carregando rotulo="Carregando equipes…" />;

  const gestoresComPapelDeEquipe = membros.filter((membro) => membro.atribuicoes
    .some((atribuicao) => atribuicao.alcance === 'TEAM'));

  if (membros.length === 0) {
    return (
      <Vazio
        titulo="Nenhuma pessoa com vínculo ativo"
        descricao="Convide alguém para a empresa antes de montar equipes."
      />
    );
  }

  return (
    <div className="space-y-4">
      {gestoresComPapelDeEquipe.length === 0 && (
        <Cartao className="bg-[var(--surface-sunken)]">
          <p className="text-sm text-[var(--text-muted)]">
            Ninguém tem papel com alcance “a equipe dele”. Montar uma equipe aqui
            só passa a ter efeito quando alguém receber esse alcance na aba
            Pessoas.
          </p>
        </Cartao>
      )}

      <NovaComposicao membros={membros} aoIncluir={aoIncluir} />

      {equipes.length === 0 ? (
        <Vazio
          titulo="Nenhuma equipe montada"
          descricao="Indique quem responde a quem para que o gestor enxergue o trabalho do time."
        />
      ) : (
        <ul className="space-y-3">
          {equipes.map((equipe) => (
            <Cartao key={equipe.gestorId}>
              <li className="space-y-2">
                <h2 className="font-medium">{equipe.gestorNome}</h2>
                <ul className="space-y-1.5">
                  {equipe.liderados.map((liderado) => (
                    <li
                      key={liderado.usuarioId}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--radius-control)] bg-[var(--surface-sunken)] px-3 py-2"
                    >
                      <span className="text-sm">
                        {liderado.nome}
                        <span className="text-[var(--text-muted)]"> · {liderado.login}</span>
                      </span>
                      <Button
                        variant="fantasma"
                        size="pequeno"
                        onClick={() => aoRemover({
                          gestorId: equipe.gestorId, usuarioId: liderado.usuarioId,
                        })}
                      >
                        Remover
                      </Button>
                    </li>
                  ))}
                </ul>
              </li>
            </Cartao>
          ))}
        </ul>
      )}
    </div>
  );
}

function NovaComposicao({
  membros,
  aoIncluir,
}: {
  readonly membros: readonly Membro[];
  readonly aoIncluir: (entrada: { gestorId: string; usuarioId: string }) => void;
}) {
  const [gestorId, setGestorId] = useState(membros[0]?.usuarioId ?? '');
  const [usuarioId, setUsuarioId] = useState('');

  // Ninguém responde a si mesmo. O banco também recusa, mas oferecer a opção
  // para depois negar é um erro que a tela não precisa deixar acontecer.
  const liderados = membros.filter((membro) => membro.usuarioId !== gestorId);

  return (
    <Cartao>
      <div className="flex flex-wrap items-end gap-2">
        <label className="space-y-1 text-xs text-[var(--text-muted)]">
          <span className="block">Gestor</span>
          <Select
            tamanho="compacto"
            value={gestorId}
            aria-label="Gestor"
            onChange={(evento) => { setGestorId(evento.target.value); setUsuarioId(''); }}
          >
            {membros.map((membro) => (
              <option key={membro.usuarioId} value={membro.usuarioId}>{membro.nome}</option>
            ))}
          </Select>
        </label>

        <label className="space-y-1 text-xs text-[var(--text-muted)]">
          <span className="block">Responde a ele</span>
          <Select
            tamanho="compacto"
            value={usuarioId}
            aria-label="Pessoa que responde ao gestor"
            onChange={(evento) => setUsuarioId(evento.target.value)}
          >
            <option value="">Escolha…</option>
            {liderados.map((membro) => (
              <option key={membro.usuarioId} value={membro.usuarioId}>{membro.nome}</option>
            ))}
          </Select>
        </label>

        <Button
          variant="secundario"
          size="pequeno"
          disabled={!gestorId || !usuarioId}
          onClick={() => aoIncluir({ gestorId, usuarioId })}
        >
          Incluir na equipe
        </Button>
      </div>
    </Cartao>
  );
}
