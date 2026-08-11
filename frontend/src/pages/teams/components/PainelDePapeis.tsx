import { Button } from '@/components/ui/button';
import { Cartao, Vazio } from '@/shared/components/Pagina';
import { rotuloDaPermissao, type Papel } from '@/shared/organizacao/tipos';

/**
 * Lista os papéis do tenant.
 *
 * <p>Papel de sistema e papel que concede além do privilégio do usuário
 * aparecem, mas sem botão: esconder um papel que existe faria a tela mentir
 * sobre a configuração da empresa, e quem administra precisa saber que ele está
 * ali mesmo sem poder mexer.
 */
export function PainelDePapeis({
  papeis,
  aoEditar,
  aoRemover,
}: {
  readonly papeis: readonly Papel[];
  readonly aoEditar: (papel: Papel) => void;
  readonly aoRemover: (papel: Papel) => void;
}) {
  if (papeis.length === 0) {
    return (
      <Vazio
        titulo="Nenhum papel ainda"
        descricao="Crie o primeiro papel para começar a distribuir acessos."
      />
    );
  }

  return (
    <ul className="space-y-3">
      {papeis.map((papel) => (
        <Cartao key={papel.id}>
          <li className="space-y-3">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-medium">{papel.nome}</h2>
                  <code className="rounded bg-[var(--surface-sunken)] px-1.5 py-0.5 text-xs text-[var(--text-muted)]">
                    {papel.codigo}
                  </code>
                  {papel.sistema && <Etiqueta>Papel de sistema</Etiqueta>}
                  {!papel.ativo && <Etiqueta>Inativo</Etiqueta>}
                </div>
                {papel.descricao && (
                  <p className="mt-1 text-sm text-[var(--text-muted)]">{papel.descricao}</p>
                )}
                <p className="mt-1 text-sm text-[var(--text-muted)]">
                  {papel.atribuicoes === 0
                    ? 'Ninguém usa este papel'
                    : `${papel.atribuicoes} pessoa(s) com este papel`}
                </p>
              </div>

              <div className="flex shrink-0 gap-2">
                <Button
                  variant="secundario"
                  size="pequeno"
                  disabled={!papel.gerenciavel}
                  title={motivoDeBloqueio(papel)}
                  onClick={() => aoEditar(papel)}
                >
                  Editar
                </Button>
                <Button
                  variant="fantasma"
                  size="pequeno"
                  // Papel em uso não some por baixo de quem está trabalhando. O
                  // backend também recusa; aqui só evita a viagem inútil.
                  disabled={!papel.gerenciavel || papel.atribuicoes > 0}
                  title={motivoDeBloqueio(papel)}
                  onClick={() => aoRemover(papel)}
                >
                  Remover
                </Button>
              </div>
            </div>

            {papel.permissoes.length > 0 && (
              <ul className="flex flex-wrap gap-1.5">
                {papel.permissoes.map((codigo) => (
                  <li
                    key={codigo}
                    className="rounded-[var(--radius-control)] bg-[var(--surface-sunken)] px-2 py-1 text-xs"
                  >
                    {rotuloDaPermissao(codigo)}
                  </li>
                ))}
              </ul>
            )}
          </li>
        </Cartao>
      ))}
    </ul>
  );
}

function motivoDeBloqueio(papel: Papel): string | undefined {
  if (papel.sistema) return 'Papel de sistema não pode ser alterado.';
  if (!papel.gerenciavel) return 'Este papel concede acessos que você não possui.';
  if (papel.atribuicoes > 0) return 'Revogue as atribuições antes de remover.';
  return undefined;
}

function Etiqueta({ children }: { readonly children: string }) {
  return (
    <span className="rounded-[var(--radius-control)] border border-[var(--border-subtle)] px-1.5 py-0.5 text-xs text-[var(--text-muted)]">
      {children}
    </span>
  );
}
