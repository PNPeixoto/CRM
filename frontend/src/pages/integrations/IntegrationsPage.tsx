import { useState, type FormEvent } from 'react';
import { AlertaErro } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import type { Canal, PareamentoCanal, TipoCanal } from '@/shared/crm/tipos';
import { canaisApi } from '@/shared/crm/api';
import { segredoOpcional } from '@/shared/forms/erros';
import { Cartao, Carregando, Pagina, Vazio } from '@/shared/components/Pagina';
import { useAlternarCanal, useCanais, useCriarCanal } from '@/shared/server-state/recursos';

const ROTULO_TIPO: Record<TipoCanal, string> = {
  LIVE_CHAT: 'Chat ao vivo',
  TELEGRAM: 'Telegram',
  WHATSAPP_CLOUD: 'WhatsApp',
  WHATSAPP_EVOLUTION: 'WhatsApp Evolution (teste)',
  INSTAGRAM: 'Instagram',
};

/** Só os canais que o backend sabe atender hoje. */
const TIPOS_DISPONIVEIS: readonly TipoCanal[] = [
  'LIVE_CHAT', 'TELEGRAM', 'WHATSAPP_EVOLUTION', 'INSTAGRAM',
];

/**
 * Canais e números conectados.
 *
 * <p>Nenhum segredo é exibido. A tela mostra apenas <b>se</b> há credencial
 * cadastrada — o backend não tem endpoint que devolva token de bot, e não deve
 * ter: token na tela é token exposto a qualquer XSS, a qualquer captura e a
 * quem passar atrás do atendente.
 */
export function IntegrationsPage() {
  const [formAberto, setFormAberto] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const canaisQuery = useCanais();
  const criarCanal = useCriarCanal();
  const alternarCanal = useAlternarCanal();
  const canais = canaisQuery.data ?? [];
  const erroExibido = erro ?? (canaisQuery.isError ? 'Não foi possível carregar os canais.' : null);

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
        {erroExibido && <AlertaErro>{erroExibido}</AlertaErro>}

        {formAberto && (
          <FormularioDeCanal
            aoSalvar={async (dados) => {
              setErro(null);
              try {
                await criarCanal.mutateAsync(dados);
                setFormAberto(false);
              } catch {
                setErro('Não foi possível conectar o canal.');
              }
            }}
          />
        )}

        {canaisQuery.isPending ? (
          <Carregando />
        ) : erroExibido && canais.length === 0 ? null : canais.length === 0 ? (
          <Vazio
            titulo="Nenhum canal conectado"
            descricao="Conecte chat ao vivo, Telegram, Instagram ou WhatsApp Evolution para receber mensagens."
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
                    await alternarCanal.mutateAsync(canal.id);
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
  const callbackMeta = canal.tipo === 'INSTAGRAM'
    ? new URL(`/api/webhooks/meta/${canal.id}`, window.location.origin).toString()
    : null;
  const precisaCredencial = canal.tipo === 'TELEGRAM'
    || canal.tipo === 'WHATSAPP_EVOLUTION'
    || canal.tipo === 'INSTAGRAM';
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
          Falta {!canal.temToken ? 'a credencial do provedor' : 'o segredo do webhook'}. O canal não recebe
          nem envia mensagem sem os dois.
        </p>
      )}

      {!incompleto && (
        canal.tipo === 'TELEGRAM'
        || canal.tipo === 'WHATSAPP_EVOLUTION'
        || canal.tipo === 'INSTAGRAM'
      ) && (
        <EstadoDaSincronizacao canal={canal} />
      )}

      {canal.tipo === 'INSTAGRAM' && (
        <p className="mt-2 break-all text-xs" style={{ color: 'var(--text-muted)' }}>
          Callback na Meta: <code>{callbackMeta}</code>
        </p>
      )}

      <div className="mt-3 flex flex-wrap gap-2">
        <Button variant="secundario" size="pequeno" onClick={aoAlternar}>
          {canal.ativo ? 'Desativar' : 'Ativar'}
        </Button>
        {canal.tipo === 'WHATSAPP_EVOLUTION' && canal.ativo && <Pareamento canal={canal} />}
      </div>
    </Cartao>
  );
}

/**
 * Pareamento do WhatsApp Evolution.
 *
 * <p>Antes disto, conectar um número exigia chamar a Evolution na mão com a
 * API key do servidor — a credencial mais ampla do laboratório distribuída
 * para a tarefa mais corriqueira.
 *
 * <p>O QR não é guardado em lugar nenhum: vive no estado do componente e some
 * ao fechar. Ele pareia uma sessão de WhatsApp, então persistir seria criar
 * uma credencial durável a partir de algo que expira em segundos.
 */
function Pareamento({ canal }: { readonly canal: Canal }) {
  const [material, setMaterial] = useState<PareamentoCanal | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const jaConectado = canal.estadoRemoto === 'HEALTHY' || canal.estadoRemoto === 'REPAIRED';

  async function pedir() {
    setErro(null);
    setCarregando(true);
    try {
      setMaterial(await canaisApi.parear(canal.id));
    } catch {
      setErro('Não foi possível obter o QR agora. Tente novamente em instantes.');
    } finally {
      setCarregando(false);
    }
  }

  if (material) {
    return (
      <div className="w-full">
        <div className="flex items-start justify-between gap-2">
          <p className="text-xs font-medium">
            {material.qrCode || material.codigo
              ? 'Abra o WhatsApp → Dispositivos conectados → Conectar dispositivo.'
              : 'Este número já está conectado.'}
          </p>
          <Button variant="secundario" size="pequeno" onClick={() => setMaterial(null)}>
            Fechar
          </Button>
        </div>

        {material.qrCode && (
          <img
            src={material.qrCode}
            alt="QR Code para conectar o WhatsApp"
            className="mt-2 h-48 w-48 rounded"
            style={{ backgroundColor: 'var(--surface-base)' }}
          />
        )}

        {material.codigo && (
          <p className="mt-2 text-xs" style={{ color: 'var(--text-muted)' }}>
            Ou digite o código no aparelho:{' '}
            <span className="font-mono font-medium">{material.codigo}</span>
          </p>
        )}

        {(material.qrCode || material.codigo) && (
          <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
            O código expira em cerca de um minuto. Se falhar, peça outro.
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button variant="secundario" size="pequeno" onClick={pedir} disabled={carregando}>
        {carregando ? 'Gerando…' : jaConectado ? 'Reconectar' : 'Conectar WhatsApp'}
      </Button>
      {erro && (
        <span className="text-xs" style={{ color: 'var(--danger)' }}>
          {erro}
        </span>
      )}
    </div>
  );
}

function EstadoDaSincronizacao({ canal }: { readonly canal: Canal }) {
  const saudavel = canal.estadoRemoto === 'HEALTHY' || canal.estadoRemoto === 'REPAIRED';
  const falha = canal.estadoRemoto === 'ERROR' || canal.estadoRemoto === 'DEGRADED';
  const provedor = canal.tipo === 'WHATSAPP_EVOLUTION'
    ? 'Evolution'
    : canal.tipo === 'INSTAGRAM' ? 'Meta' : 'Telegram';
  const texto = saudavel
    ? `${provedor} sincronizado.`
    : falha
      ? `A ${provedor} ainda não confirmou esta conexão.`
      : `Confirmando o webhook com a ${provedor}…`;
  const pendencias = (canal.pendenciasRemotas ?? 0) > 0
    ? ` ${canal.pendenciasRemotas} atualização(ões) aguardando no provedor.`
    : '';

  return (
    <p
      role="status"
      className="mt-2 text-xs"
      style={{ color: saudavel ? 'var(--success)' : falha ? 'var(--warning)' : 'var(--text-muted)' }}
    >
      {texto}{pendencias}
    </p>
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
    tokenVerificacaoWebhook?: string;
  }) => Promise<void>;
}) {
  const [tipo, setTipo] = useState<TipoCanal>('TELEGRAM');
  const [nome, setNome] = useState('');
  const [identificador, setIdentificador] = useState('');
  const [token, setToken] = useState('');
  const [segredoWebhook, setSegredoWebhook] = useState('');
  const [tokenVerificacaoWebhook, setTokenVerificacaoWebhook] = useState('');
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setEnviando(true);
    try {
      await aoSalvar({
        tipo,
        nome: nome.trim(),
        identificadorExterno: identificador.trim() || undefined,
        token: segredoOpcional(token),
        segredoWebhook: segredoOpcional(segredoWebhook),
        tokenVerificacaoWebhook: segredoOpcional(tokenVerificacaoWebhook),
      });
      // Limpa os segredos do estado assim que saem: manter em memória depois
      // do envio os deixa disponíveis a qualquer script que rode na página.
      setNome('');
      setIdentificador('');
      setToken('');
      setSegredoWebhook('');
      setTokenVerificacaoWebhook('');
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
              required
            />
            <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
              O CRM gera e protege o segredo do webhook automaticamente.
            </p>
          </div>
        </>
      )}

      {tipo === 'WHATSAPP_EVOLUTION' && (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="identificador-evolution">Nome da instância</Label>
            <Input
              id="identificador-evolution"
              value={identificador}
              onChange={(e) => setIdentificador(e.target.value)}
              placeholder="pnp-teste"
              autoComplete="off"
              required
            />
          </div>
          <div className="space-y-1.5 self-end">
            <p className="text-xs" style={{ color: 'var(--warning)' }}>
              Uso experimental local por QR Code. Não substitui a API oficial da Meta.
            </p>
          </div>
        </>
      )}

      {tipo === 'INSTAGRAM' && (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="identificador-instagram">ID da conta profissional</Label>
            <Input
              id="identificador-instagram"
              value={identificador}
              onChange={(e) => setIdentificador(e.target.value)}
              placeholder="17841400000000000"
              inputMode="numeric"
              pattern="[0-9]{1,40}"
              autoComplete="off"
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="token-instagram">Token de acesso da Meta</Label>
            <Input
              id="token-instagram"
              type="password"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              autoComplete="off"
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="segredo-instagram">Segredo do aplicativo Meta</Label>
            <Input
              id="segredo-instagram"
              type="password"
              value={segredoWebhook}
              onChange={(e) => setSegredoWebhook(e.target.value)}
              autoComplete="off"
              required
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="verificacao-instagram">Token de verificação do webhook</Label>
            <Input
              id="verificacao-instagram"
              type="password"
              value={tokenVerificacaoWebhook}
              onChange={(e) => setTokenVerificacaoWebhook(e.target.value)}
              autoComplete="off"
              required
            />
          </div>

          <p className="text-xs sm:col-span-2" style={{ color: 'var(--text-muted)' }}>
            Use uma conta profissional com a permissão instagram_business_manage_messages.
            Depois de conectar, copie o callback exibido no cartão para o aplicativo Meta.
          </p>
        </>
      )}

      <div className="sm:col-span-2">
        <Button
          type="submit"
          disabled={
            enviando
            || nome.trim().length === 0
            || (tipo === 'TELEGRAM' && !token.trim())
            || (tipo === 'WHATSAPP_EVOLUTION' && !identificador.trim())
            || (tipo === 'INSTAGRAM' && (
              !identificador.trim()
              || !token.trim()
              || !segredoWebhook.trim()
              || !tokenVerificacaoWebhook.trim()
            ))
          }
        >
          {enviando ? 'Conectando…' : 'Conectar'}
        </Button>
      </div>
    </form>
  );
}
