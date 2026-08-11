import { useState } from 'react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Cartao, Carregando, Pagina, Vazio } from '@/shared/components/Pagina';
import {
  useAtribuirPapel,
  useCatalogoDePapeis,
  useEquipes,
  useIncluirNaEquipe,
  useMembrosDaOrganizacao,
  useRemoverDaEquipe,
  useRemoverPapel,
  useRevogarPapel,
} from '@/shared/server-state/recursos';
import { EditorDePapel } from './components/EditorDePapel';
import { PainelDeEquipes } from './components/PainelDeEquipes';
import { PainelDeMembros } from './components/PainelDeMembros';
import { PainelDePapeis } from './components/PainelDePapeis';
import type { Papel } from '@/shared/organizacao/tipos';

type Aba = 'papeis' | 'pessoas' | 'equipes';

const ABAS: readonly { readonly id: Aba; readonly rotulo: string }[] = [
  { id: 'papeis', rotulo: 'Papéis' },
  { id: 'pessoas', rotulo: 'Pessoas' },
  { id: 'equipes', rotulo: 'Equipes' },
];

/**
 * Administração de acessos: papéis, atribuições e composição das equipes.
 *
 * <p><b>A tela nunca é a proteção.</b> Ela desabilita o que o backend recusaria
 * — permissão que o usuário não pode conceder, papel de sistema, papel que
 * concede além do próprio privilégio —, mas cada botão continua batendo num
 * endpoint que decide sozinho. Esconder é conveniência; a decisão é do
 * servidor, e há teste garantindo que ele recusa mesmo quando a tela deixa
 * passar.
 */
export function TeamsPage() {
  const [aba, setAba] = useState<Aba>('papeis');
  const [emEdicao, setEmEdicao] = useState<Papel | null>(null);
  const [criando, setCriando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const catalogo = useCatalogoDePapeis();
  const membros = useMembrosDaOrganizacao();
  const equipes = useEquipes();

  const removerPapel = useRemoverPapel();
  const atribuir = useAtribuirPapel();
  const revogar = useRevogarPapel();
  const incluirNaEquipe = useIncluirNaEquipe();
  const removerDaEquipe = useRemoverDaEquipe();

  const semAcesso = catalogo.isError && (catalogo.error as { status?: number })?.status === 403;

  if (semAcesso) {
    return (
      <Pagina titulo="Acessos" descricao="Papéis, pessoas e equipes">
        <Vazio
          titulo="Você não administra acessos"
          descricao="Peça a quem administra a empresa a permissão de gerenciar papéis."
        />
      </Pagina>
    );
  }

  async function executar(acao: () => Promise<unknown>) {
    setErro(null);
    try {
      await acao();
    } catch (falha) {
      setErro(falha instanceof Error
        ? falha.message
        : 'Não foi possível concluir a operação.');
    }
  }

  return (
    <Pagina
      titulo="Acessos"
      descricao="Papéis, pessoas e equipes"
      acoes={aba === 'papeis' && (
        <Button onClick={() => { setEmEdicao(null); setCriando((v) => !v); }}>
          {criando ? 'Cancelar' : 'Novo papel'}
        </Button>
      )}
    >
      <div className="space-y-4">
        {erro && <AlertaErro>{erro}</AlertaErro>}

        {/* Revogar não derruba a sessão em curso: o token vive 15 minutos e
            carrega o contexto. Prometer efeito imediato e entregar 15 minutos
            é pior que avisar. */}
        <Cartao className="bg-[var(--surface-sunken)]">
          <p className="text-sm text-[var(--text-muted)]">
            Mudanças de acesso valem na próxima ação da pessoa. Quem já está com
            a sessão aberta pode levar até 15 minutos para perder o acesso
            retirado.
          </p>
        </Cartao>

        <nav className="flex gap-1 border-b border-[var(--border-subtle)]" aria-label="Seções">
          {ABAS.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setAba(item.id)}
              aria-current={aba === item.id ? 'page' : undefined}
              className={
                aba === item.id
                  ? 'border-b-2 border-[var(--brand)] px-3 py-2 text-sm font-medium'
                  : 'px-3 py-2 text-sm text-[var(--text-muted)] hover:text-[var(--text-strong)]'
              }
            >
              {item.rotulo}
            </button>
          ))}
        </nav>

        {catalogo.isPending && <Carregando rotulo="Carregando acessos…" />}

        {catalogo.data && aba === 'papeis' && (
          <div className="space-y-4">
            {(criando || emEdicao) && (
              <EditorDePapel
                papel={emEdicao}
                catalogo={catalogo.data}
                aoFechar={() => { setCriando(false); setEmEdicao(null); }}
                aoFalhar={setErro}
              />
            )}
            <PainelDePapeis
              papeis={catalogo.data.papeis}
              aoEditar={(papel) => { setCriando(false); setEmEdicao(papel); }}
              aoRemover={(papel) => executar(() => removerPapel.mutateAsync(papel.id))}
            />
          </div>
        )}

        {catalogo.data && aba === 'pessoas' && (
          <PainelDeMembros
            membros={membros.data ?? []}
            papeis={catalogo.data.papeis}
            permissoes={catalogo.data.permissoes}
            carregando={membros.isPending}
            aoAtribuir={(entrada) => executar(() => atribuir.mutateAsync(entrada))}
            aoRevogar={(entrada) => executar(() => revogar.mutateAsync(entrada))}
          />
        )}

        {aba === 'equipes' && (
          <PainelDeEquipes
            equipes={equipes.data ?? []}
            membros={membros.data ?? []}
            carregando={equipes.isPending || membros.isPending}
            aoIncluir={(entrada) => executar(() => incluirNaEquipe.mutateAsync(entrada))}
            aoRemover={(entrada) => executar(() => removerDaEquipe.mutateAsync(entrada))}
          />
        )}
      </div>
    </Pagina>
  );
}
