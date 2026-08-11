import { useState, type FormEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Cartao } from '@/shared/components/Pagina';
import { useCriarPapel, useDefinirPermissoes, useAtualizarPapel } from '@/shared/server-state/recursos';
import {
  rotuloDaPermissao,
  type CatalogoDePapeis,
  type Papel,
} from '@/shared/organizacao/tipos';

/**
 * Cria ou edita um papel.
 *
 * <p>O catálogo vem do backend marcando cada permissão como delegável ou não —
 * as não delegáveis aparecem desabilitadas, com o motivo. É conveniência para
 * quem administra de boa-fé: o servidor recusa igual se a caixa for marcada por
 * outro caminho.
 */
export function EditorDePapel({
  papel,
  catalogo,
  aoFechar,
  aoFalhar,
}: {
  readonly papel: Papel | null;
  readonly catalogo: CatalogoDePapeis;
  readonly aoFechar: () => void;
  readonly aoFalhar: (mensagem: string) => void;
}) {
  const [codigo, setCodigo] = useState(papel?.codigo ?? '');
  const [nome, setNome] = useState(papel?.nome ?? '');
  const [descricao, setDescricao] = useState(papel?.descricao ?? '');
  const [selecionadas, setSelecionadas] = useState<readonly string[]>(papel?.permissoes ?? []);

  const criar = useCriarPapel();
  const atualizar = useAtualizarPapel();
  const definirPermissoes = useDefinirPermissoes();
  const salvando = criar.isPending || atualizar.isPending || definirPermissoes.isPending;

  function alternar(permissao: string) {
    setSelecionadas((atual) => (atual.includes(permissao)
      ? atual.filter((item) => item !== permissao)
      : [...atual, permissao]));
  }

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    try {
      if (papel) {
        // Duas chamadas porque são dois recursos: os dados do papel e o
        // conjunto de permissões. Juntar num só endpoint faria renomear exigir
        // enviar a lista inteira de novo.
        await atualizar.mutateAsync({
          id: papel.id, nome, descricao: descricao || null, ativo: papel.ativo,
        });
        await definirPermissoes.mutateAsync({ id: papel.id, permissoes: selecionadas });
      } else {
        await criar.mutateAsync({
          codigo, nome, descricao: descricao || null, permissoes: selecionadas,
        });
      }
      aoFechar();
    } catch (falha) {
      aoFalhar(falha instanceof Error ? falha.message : 'Não foi possível salvar o papel.');
    }
  }

  return (
    <Cartao>
      <form className="space-y-4" onSubmit={salvar}>
        <h2 className="font-medium">{papel ? `Editar ${papel.nome}` : 'Novo papel'}</h2>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="papel-codigo">Código</Label>
            <Input
              id="papel-codigo"
              value={codigo}
              // O código é a identidade estável do papel: renomear é rotina,
              // trocar identidade quebraria toda referência já gravada.
              disabled={papel !== null}
              required
              maxLength={49}
              pattern="[A-Z][A-Z0-9_]{1,48}"
              placeholder="SDR"
              onChange={(evento) => setCodigo(evento.target.value.toUpperCase())}
            />
            <p className="text-xs text-[var(--text-muted)]">
              Maiúsculas, números e _. Não muda depois de criado.
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="papel-nome">Nome</Label>
            <Input
              id="papel-nome"
              value={nome}
              required
              maxLength={120}
              placeholder="Pré-vendas"
              onChange={(evento) => setNome(evento.target.value)}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="papel-descricao">Descrição</Label>
          <Input
            id="papel-descricao"
            value={descricao}
            maxLength={500}
            placeholder="Qualifica lead e agenda reunião"
            onChange={(evento) => setDescricao(evento.target.value)}
          />
        </div>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">O que este papel permite</legend>
          <ul className="grid gap-1.5 sm:grid-cols-2">
            {catalogo.permissoes.map((permissao) => {
              const bloqueada = !permissao.delegavelProprio;
              return (
                <li key={permissao.codigo}>
                  <label
                    className={bloqueada
                      ? 'flex cursor-not-allowed items-center gap-2 text-sm opacity-50'
                      : 'flex items-center gap-2 text-sm'}
                    title={bloqueada ? 'Você não possui este acesso e não pode concedê-lo.' : undefined}
                  >
                    <input
                      type="checkbox"
                      disabled={bloqueada}
                      checked={selecionadas.includes(permissao.codigo)}
                      onChange={() => alternar(permissao.codigo)}
                    />
                    {rotuloDaPermissao(permissao.codigo)}
                  </label>
                </li>
              );
            })}
          </ul>
        </fieldset>

        <div className="flex gap-2">
          <Button type="submit" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar'}
          </Button>
          <Button type="button" variant="fantasma" onClick={aoFechar}>Cancelar</Button>
        </div>
      </form>
    </Cartao>
  );
}
