import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { canaisApi } from '@/shared/crm/api';
import type { Canal, TipoCanal } from '@/shared/crm/tipos';
import { Cartao, Carregando, Pagina, Vazio } from '@/shared/components/Pagina';

const ROTULO_TIPO: Record<TipoCanal, string> = {
  LIVE_CHAT: 'Chat ao vivo',
  TELEGRAM: 'Telegram',
  WHATSAPP_CLOUD: 'WhatsApp',
  INSTAGRAM: 'Instagram',
};

/** Só os canais que o backend sabe atender hoje. */
const TIPOS_DISPONIVEIS: readonly TipoCanal[] = ['LIVE_CHAT', 'TELEGRAM'];

/**
 * Canais e números conectados.
 *
 * <p>Nenhum segredo é exibido. A tela mostra apenas <b>se</b> há credencial
 * cadastrada — o backend não tem endpoint que devolva token de bot, e não deve
 * ter: token na tela é token exposto a qualquer XSS, a qualquer captura e a
 * quem passar atrás do atendente.
 */
export function IntegrationsPage() {
  const [canais, setCanais] = useState<readonly Canal[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    try {
      setErro(null);
      setCanais(await canaisApi.listar());
    } catch {
      setCanais([]);
      setErro('Não foi possível carregar os canais.');
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  return (
    <Pagina
      titulo="Canais e números"
      descricao="Conexões de atendimento"
      acoes={
        <Button onClick={() => setFormAberto((v) => !v)}>
          {formAberto ? 'Cancelar' : 'Conectar canal'}
        </Button>
      }
    >
      <div className="space-y-4">
        {erro && <AlertaErro>{erro}</AlertaErro>}

        {formAberto && (
          <FormularioDeCanal
            aoSalvar={async (dados) => {
              setErro(null);
              try {
                await canaisApi.criar(dados);
                setFormAberto(false);
                await carregar();
              } catch {
                setErro('Não foi possível conectar o canal.');
              }
            }}
          />
        )}

        {carregando ? (
          <Carregando />
        ) : erro && canais.length === 0 ? null : canais.length === 0 ? (
          <Vazio
            titulo="Nenhum canal conectado"
            descricao="Conecte o chat ao vivo ou um bot do Telegram para começar a receber mensagens."
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {canais.map((canal) => (
              <CartaoDeCanal
                key={canal.id}
                canal={canal}
                aoAlternar={async () => {
                  setErro(null);
                  try {
                    await canaisApi.alternarAtivacao(canal.id);
                    await carregar();
                  } catch {
                    setErro('Não foi possível alterar o canal.');
                  }
                }}
              />
            ))}
          </div>
        )}
      </div>
    </Pagina>
  );
}

function CartaoDeCanal({
  canal,
  aoAlternar,
}: {
  readonly canal: Canal;
  readonly aoAlternar: () => void;
}) {
  const precisaCredencial = canal.tipo === 'TELEGRAM';
  const incompleto = precisaCredencial && (!canal.temToken || !canal.temSegredoWebhook);

  return (
    <Cartao>
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{canal.nome}</p>
          <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {ROTULO_TIPO[canal.tipo]}
            {canal.identificadorExterno ? ` · ${canal.identificadorExterno}` : ''}
          </p>
        </div>

        <span
          className="shrink-0 rounded px-1.5 py-px text-[11px] font-medium"
          style={{
            color: canal.ativo ? 'var(--success)' : 'var(--text-muted)',
            backgroundColor: canal.ativo ? 'var(--success-soft)' : 'var(--surface-sunken)',
          }}
        >
          {canal.ativo ? 'Ativo' : 'Inativo'}
        </span>
      </div>

      {incompleto && (
        <p className="mt-2 text-xs" style={{ color: 'var(--warning)' }}>
          Falta {!canal.temToken ? 'o token do bot' : 'o segredo do webhook'}. O canal não recebe
          nem envia mensagem sem os dois.
        </p>
      )}

      <div className="mt-3">
        <Button variant="secundario" size="pequeno" onClick={aoAlternar}>
          {canal.ativo ? 'Desativar' : 'Ativar'}
        </Button>
      </div>
    </Cartao>
  );
}

function FormularioDeCanal({
  aoSalvar,
}: {
  readonly aoSalvar: (dados: {
    tipo: string;
    nome: string;
    identificadorExterno?: string;
    token?: string;
    segredoWebhook?: string;
  }) => Promise<void>;
}) {
  const [tipo, setTipo] = useState<TipoCanal>('TELEGRAM');
  const [nome, setNome] = useState('');
  const [identificador, setIdentificador] = useState('');
  const [token, setToken] = useState('');
  const [segredo, setSegredo] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setEnviando(true);
    try {
      await aoSalvar({
        tipo,
        nome: nome.trim(),
        identificadorExterno: identificador.trim() || undefined,
        token: token.trim() || undefined,
        segredoWebhook: segredo.trim() || undefined,
      });
      // Limpa os segredos do estado assim que saem: manter em memória depois
      // do envio os deixa disponíveis a qualquer script que rode na página.
      setNome('');
      setIdentificador('');
      setToken('');
      setSegredo('');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form
      onSubmit={enviar}
      className="grid gap-3 rounded-[var(--radius-surface)] border p-4 sm:grid-cols-2"
      style={{ borderColor: 'var(--border-subtle)', backgroundColor: 'var(--surface-raised)' }}
    >
      <div className="space-y-1.5">
        <Label htmlFor="tipo">Tipo</Label>
        <Select
          id="tipo"
          className="w-full"
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoCanal)}
        >
          {TIPOS_DISPONIVEIS.map((t) => (
            <option key={t} value={t}>
              {ROTULO_TIPO[t]}
            </option>
          ))}
        </Select>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="nome">Nome</Label>
        <Input
          id="nome"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          placeholder="Bot de atendimento"
          required
        />
      </div>

      {tipo === 'TELEGRAM' && (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="identificador">Id do bot</Label>
            <Input
              id="identificador"
              value={identificador}
              onChange={(e) => setIdentificador(e.target.value)}
              placeholder="1234567890"
              autoComplete="off"
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="token">Token do bot</Label>
            <Input
              id="token"
              // type=password evita exibição em tela e no histórico de
              // preenchimento; autoComplete off evita que o navegador o salve.
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              autoComplete="off"
              placeholder="Obtido no @BotFather"
            />
          </div>

          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="segredo">Segredo do webhook</Label>
            <Input
              id="segredo"
              type="password"
              value={segredo}
              onChange={(e) => setSegredo(e.target.value)}
              autoComplete="off"
              placeholder="openssl rand -hex 32"
            />
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              Precisa ser o mesmo valor informado no <code>setWebhook</code>. Guardado cifrado e
              nunca exibido de volta.
            </p>
          </div>
        </>
      )}

      <div className="sm:col-span-2">
        <Button type="submit" disabled={enviando || nome.trim().length === 0}>
          {enviando ? 'Conectando…' : 'Conectar'}
        </Button>
      </div>
    </form>
  );
}
