import { useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import type { FiltrosAuditoria } from '@/shared/audit/api';
import type { AcaoAuditoria, EventoAuditoria, ResultadoAuditoria } from '@/shared/audit/tipos';
import { formatarDataHora } from '@/shared/formato';
import { Carregando, Cartao, Pagina, Vazio } from '@/shared/components/Pagina';
import { useAuditoria } from '@/shared/server-state/recursos';

const ACOES: readonly { valor: AcaoAuditoria; rotulo: string }[] = [
  { valor: 'AUTHORIZATION_DENIED', rotulo: 'Acesso negado' },
  { valor: 'AUDIT_READ', rotulo: 'Consulta da auditoria' },
  { valor: 'CREDENTIAL_CHANGED', rotulo: 'Credencial alterada' },
  { valor: 'ROLE_CHANGED', rotulo: 'Perfil ou papel alterado' },
  { valor: 'SENSITIVE_CONFIGURATION_CHANGED', rotulo: 'ConfiguraÃ§Ã£o sensÃ­vel alterada' },
  { valor: 'EXPORT_REQUESTED', rotulo: 'ExportaÃ§Ã£o solicitada' },
  { valor: 'EXPORT_COMPLETED', rotulo: 'ExportaÃ§Ã£o concluÃ­da' },
];

const ROTULO_ACAO = Object.fromEntries(
  ACOES.map((item) => [`${item.valor}_V1`, item.rotulo]),
) as Readonly<Record<string, string>>;

const ROTULO_RESULTADO: Record<ResultadoAuditoria, string> = {
  SUCCEEDED: 'ConcluÃ­do',
  DENIED: 'Negado',
  FAILED: 'Falhou',
};

const ROTULO_ALVO: Readonly<Record<string, string>> = {
  AUTHORIZATION: 'AutorizaÃ§Ã£o',
  AUDIT_TRAIL: 'Trilha de auditoria',
  EXPORT: 'ExportaÃ§Ã£o',
  CHANNEL_CONNECTION: 'Canal',
  HTTP_CONNECTOR: 'Conector HTTP',
  ROLE: 'Perfil de acesso',
  TENANT_PROFILE: 'ConfiguraÃ§Ã£o da empresa',
};

const FILTROS_INICIAIS: FiltrosAuditoria = { acao: '', resultado: '' };

export function AuditPage() {
  const [filtros, setFiltros] = useState<FiltrosAuditoria>(FILTROS_INICIAIS);
  const [cursor, setCursor] = useState<string | null>(null);
  const [historico, setHistorico] = useState<readonly (string | null)[]>([]);
  const query = useAuditoria(filtros, cursor);
  const pagina = query.data;

  function alterarFiltros(proximos: FiltrosAuditoria) {
    setFiltros(proximos);
    setCursor(null);
    setHistorico([]);
  }

  function proximaPagina() {
    if (!pagina?.proximoCursor) return;
    setHistorico((atual) => [...atual, cursor]);
    setCursor(pagina.proximoCursor);
  }

  function paginaAnterior() {
    setHistorico((atual) => {
      const anterior = atual.at(-1) ?? null;
      setCursor(anterior);
      return atual.slice(0, -1);
    });
  }

  return (
    <Pagina
      titulo="Auditoria"
      descricao="HistÃ³rico verificÃ¡vel das aÃ§Ãµes de maior impacto"
      acoes={pagina?.integridadeVerificada ? (
        <span
          className="inline-flex items-center gap-1.5 text-xs font-medium text-[var(--success)]"
          role="status"
        >
          <ShieldCheck className="size-4" aria-hidden="true" />
          Integridade verificada
        </span>
      ) : undefined}
    >
      <div className="space-y-4">
        <Cartao>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="auditoria-acao">AÃ§Ã£o</Label>
              <Select
                id="auditoria-acao"
                value={filtros.acao}
                onChange={(evento) => alterarFiltros({
                  ...filtros,
                  acao: evento.target.value as AcaoAuditoria | '',
                })}
              >
                <option value="">Todas as aÃ§Ãµes</option>
                {ACOES.map((acao) => (
                  <option key={acao.valor} value={acao.valor}>{acao.rotulo}</option>
                ))}
              </Select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="auditoria-resultado">Resultado</Label>
              <Select
                id="auditoria-resultado"
                value={filtros.resultado}
                onChange={(evento) => alterarFiltros({
                  ...filtros,
                  resultado: evento.target.value as ResultadoAuditoria | '',
                })}
              >
                <option value="">Todos os resultados</option>
                {Object.entries(ROTULO_RESULTADO).map(([valor, rotulo]) => (
                  <option key={valor} value={valor}>{rotulo}</option>
                ))}
              </Select>
            </div>
          </div>
        </Cartao>

        {query.isPending ? (
          <Carregando />
        ) : query.isError ? (
          <EstadoDeConteudo
            tipo="erro"
            titulo="NÃ£o foi possÃ­vel consultar a auditoria"
            descricao="A operaÃ§Ã£o foi interrompida para preservar a confiabilidade da trilha."
          />
        ) : !pagina || pagina.eventos.length === 0 ? (
          <Vazio
            tipo={filtros.acao || filtros.resultado ? 'sem-resultados' : 'vazio-inicial'}
            titulo="Nenhum evento encontrado"
            descricao="Altere os filtros ou aguarde uma aÃ§Ã£o auditÃ¡vel."
          />
        ) : (
          <ListaDeEventos eventos={pagina.eventos} />
        )}

        {(historico.length > 0 || Boolean(pagina?.proximoCursor)) && (
          <nav className="flex items-center gap-2" aria-label="PaginaÃ§Ã£o da auditoria">
            <Button
              variant="secundario"
              onClick={paginaAnterior}
              disabled={historico.length === 0 || query.isFetching}
            >
              PÃ¡gina anterior
            </Button>
            <span className="text-sm text-[var(--text-muted)]">
              PÃ¡gina {historico.length + 1}
            </span>
            <Button
              variant="secundario"
              onClick={proximaPagina}
              disabled={!pagina?.proximoCursor || query.isFetching}
            >
              PrÃ³xima pÃ¡gina
            </Button>
          </nav>
        )}
      </div>
    </Pagina>
  );
}

function ListaDeEventos({ eventos }: { readonly eventos: readonly EventoAuditoria[] }) {
  return (
    <div className="overflow-hidden rounded-[var(--radius-surface)] border border-[var(--border-subtle)] bg-[var(--surface-raised)]">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead className="bg-[var(--surface-sunken)] text-xs text-[var(--text-muted)]">
            <tr>
              <th className="px-4 py-2.5 font-medium">Quando</th>
              <th className="px-4 py-2.5 font-medium">AÃ§Ã£o</th>
              <th className="px-4 py-2.5 font-medium">ResponsÃ¡vel</th>
              <th className="px-4 py-2.5 font-medium">Alvo</th>
              <th className="px-4 py-2.5 font-medium">Resultado</th>
              <th className="px-4 py-2.5 font-medium">CorrelaÃ§Ã£o</th>
            </tr>
          </thead>
          <tbody>
            {eventos.map((evento) => (
              <tr key={evento.id} className="border-t border-[var(--border-subtle)]">
                <td className="whitespace-nowrap px-4 py-3">{formatarDataHora(evento.ocorridoEm)}</td>
                <td className="px-4 py-3 font-medium">{ROTULO_ACAO[evento.acao] ?? evento.acao}</td>
                <td className="px-4 py-3">
                  <span className="block">{nomeDoAtor(evento)}</span>
                  {evento.atorId && <CodigoMascarado valor={evento.atorId} />}
                </td>
                <td className="px-4 py-3">
                  <span className="block">{ROTULO_ALVO[evento.alvoTipo] ?? evento.alvoTipo}</span>
                  {evento.alvoId && <CodigoMascarado valor={evento.alvoId} />}
                </td>
                <td className="px-4 py-3">
                  <span
                    className="rounded px-1.5 py-0.5 text-xs font-medium"
                    style={estiloDoResultado(evento.resultado)}
                  >
                    {ROTULO_RESULTADO[evento.resultado]}
                  </span>
                </td>
                <td className="px-4 py-3"><CodigoMascarado valor={evento.correlacaoId} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function CodigoMascarado({ valor }: { readonly valor: string }) {
  return (
    <span className="font-mono text-[11px] text-[var(--text-muted)]" aria-label="Identificador mascarado">
      {valor.slice(0, 8)}â€¦
    </span>
  );
}

function nomeDoAtor(evento: EventoAuditoria) {
  if (evento.atorTipo === 'SYSTEM') return 'Sistema';
  return evento.atorNome ?? 'UsuÃ¡rio removido';
}

function estiloDoResultado(resultado: ResultadoAuditoria) {
  if (resultado === 'SUCCEEDED') {
    return { color: 'var(--success)', backgroundColor: 'var(--success-soft)' };
  }
  return { color: 'var(--danger)', backgroundColor: 'var(--danger-soft)' };
}
