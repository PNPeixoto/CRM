import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Cartao, Carregando, Vazio } from '@/shared/components/Pagina';
import {
  ALCANCES,
  DESCRICAO_ALCANCE,
  ROTULO_ALCANCE,
  type Alcance,
  type Membro,
  type Papel,
} from '@/shared/organizacao/tipos';

/**
 * Quem tem qual papel, e sob qual alcance.
 *
 * <p>O alcance é escolhido na atribuição, não no papel: o mesmo "Closer" pode
 * valer para a empresa inteira numa pessoa e só para a carteira própria em
 * outra, sem duplicar papel.
 */
export function PainelDeMembros({
  membros,
  papeis,
  carregando,
  aoAtribuir,
  aoRevogar,
}: {
  readonly membros: readonly Membro[];
  readonly papeis: readonly Papel[];
  readonly carregando: boolean;
  readonly aoAtribuir: (entrada: {
    membershipId: string; papelId: string; alcance: Alcance;
  }) => void;
  readonly aoRevogar: (entrada: { membershipId: string; atribuicaoId: string }) => void;
}) {
  if (carregando) return <Carregando rotulo="Carregando pessoas…" />;
  if (membros.length === 0) {
    return (
      <Vazio
        titulo="Nenhuma pessoa com vínculo ativo"
        descricao="Convide alguém para a empresa antes de distribuir papéis."
      />
    );
  }

  return (
    <ul className="space-y-3">
      {membros.map((membro) => (
        <Cartao key={membro.membershipId}>
          <li className="space-y-3">
            <div>
              <h2 className="font-medium">{membro.nome}</h2>
              <p className="text-sm text-[var(--text-muted)]">{membro.login}</p>
            </div>

            {membro.atribuicoes.length === 0 ? (
              <p className="text-sm text-[var(--text-muted)]">
                Sem papel. Esta pessoa entra no sistema e não enxerga nada.
              </p>
            ) : (
              <ul className="space-y-1.5">
                {membro.atribuicoes.map((atribuicao) => (
                  <li
                    key={atribuicao.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--radius-control)] bg-[var(--surface-sunken)] px-3 py-2"
                  >
                    <span className="text-sm">
                      <strong className="font-medium">{atribuicao.papelNome}</strong>
                      {' — '}
                      <span className="text-[var(--text-muted)]">
                        {ROTULO_ALCANCE[atribuicao.alcance]}
                      </span>
                    </span>
                    <Button
                      variant="fantasma"
                      size="pequeno"
                      onClick={() => aoRevogar({
                        membershipId: membro.membershipId, atribuicaoId: atribuicao.id,
                      })}
                    >
                      Revogar
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            <FormularioDeAtribuicao
              papeis={papeis}
              aoAtribuir={(papelId, alcance) => aoAtribuir({
                membershipId: membro.membershipId, papelId, alcance,
              })}
            />
          </li>
        </Cartao>
      ))}
    </ul>
  );
}

function FormularioDeAtribuicao({
  papeis,
  aoAtribuir,
}: {
  readonly papeis: readonly Papel[];
  readonly aoAtribuir: (papelId: string, alcance: Alcance) => void;
}) {
  const disponiveis = papeis.filter((papel) => papel.ativo);
  const [papelId, setPapelId] = useState(disponiveis[0]?.id ?? '');
  const [alcance, setAlcance] = useState<Alcance>('OWN');

  if (disponiveis.length === 0) return null;

  return (
    <div className="flex flex-wrap items-end gap-2">
      <label className="space-y-1 text-xs text-[var(--text-muted)]">
        <span className="block">Papel</span>
        <Select
          tamanho="compacto"
          value={papelId}
          aria-label="Papel a atribuir"
          onChange={(evento) => setPapelId(evento.target.value)}
        >
          {disponiveis.map((papel) => (
            <option key={papel.id} value={papel.id}>{papel.nome}</option>
          ))}
        </Select>
      </label>

      <label className="space-y-1 text-xs text-[var(--text-muted)]">
        <span className="block">Alcance</span>
        <Select
          tamanho="compacto"
          value={alcance}
          aria-label="Alcance da atribuição"
          onChange={(evento) => setAlcance(evento.target.value as Alcance)}
        >
          {ALCANCES.map((item) => (
            <option key={item} value={item}>{ROTULO_ALCANCE[item]}</option>
          ))}
        </Select>
      </label>

      <Button
        variant="secundario"
        size="pequeno"
        disabled={!papelId}
        onClick={() => aoAtribuir(papelId, alcance)}
      >
        Atribuir
      </Button>

      <p className="w-full text-xs text-[var(--text-muted)]">
        {DESCRICAO_ALCANCE[alcance]}
        {alcance === 'TEAM' && ' Monte a equipe na aba Equipes.'}
      </p>
    </div>
  );
}
