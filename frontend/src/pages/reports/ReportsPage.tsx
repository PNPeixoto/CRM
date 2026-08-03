import { useEffect, useState } from 'react';
import { relatoriosApi } from '@/shared/crm/api';
import type { VisaoGeral } from '@/shared/crm/tipos';
import { formatarMoeda, formatarNumero } from '@/shared/formato';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { Cartao, Carregando, Pagina } from '@/shared/components/Pagina';

export function ReportsPage() {
  const [dados, setDados] = useState<VisaoGeral | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    relatoriosApi
      .visaoGeral()
      .then(setDados)
      .catch(() => setDados(null))
      .finally(() => setCarregando(false));
  }, []);

  if (carregando) {
    return (
      <Pagina titulo="Relatórios">
        <Carregando />
      </Pagina>
    );
  }

  if (!dados) {
    return (
      <Pagina titulo="Relatórios">
        <EstadoDeConteudo
          tipo="erro"
          titulo="Não foi possível carregar os dados"
          descricao="Atualize a página para tentar novamente."
        />
      </Pagina>
    );
  }

  const fechadas = dados.oportunidadesGanhas + dados.oportunidadesPerdidas;
  const ticketMedio =
    dados.oportunidadesGanhas > 0
      ? Math.round(dados.valorGanhoCentavos / dados.oportunidadesGanhas)
      : 0;

  return (
    <Pagina titulo="Relatórios" descricao="Consolidado da operação">
      <div className="space-y-6">
        <Tabela
          titulo="Funil de vendas"
          linhas={[
            ['Oportunidades em aberto', formatarNumero(dados.oportunidadesAbertas)],
            ['Valor em aberto', formatarMoeda(dados.valorAbertoCentavos)],
            ['Ganhas', formatarNumero(dados.oportunidadesGanhas)],
            ['Valor ganho', formatarMoeda(dados.valorGanhoCentavos)],
            ['Perdidas', formatarNumero(dados.oportunidadesPerdidas)],
            ['Fechadas no total', formatarNumero(fechadas)],
            [
              'Taxa de conversão',
              `${String(dados.taxaDeConversaoPercentual).replace('.', ',')}%`,
            ],
            // Ticket médio sobre as GANHAS, não sobre todas. Dividir pelo
            // total faria uma oportunidade perdida de valor alto puxar a
            // média para baixo, medindo algo que não é ticket.
            ['Ticket médio (ganhas)', formatarMoeda(ticketMedio)],
          ]}
        />

        <Tabela
          titulo="Atendimento"
          linhas={[
            ['Conversas abertas', formatarNumero(dados.conversasAbertas)],
            ['Aguardando cliente', formatarNumero(dados.conversasAguardando)],
            ['Mensagens recebidas hoje', formatarNumero(dados.mensagensRecebidasHoje)],
            ['Canais ativos', formatarNumero(dados.canaisAtivos)],
          ]}
        />

        <Tabela
          titulo="Base e rotina"
          linhas={[
            ['Contatos cadastrados', formatarNumero(dados.totalDeContatos)],
            ['Tarefas em aberto', formatarNumero(dados.tarefasAbertas)],
            ['Tarefas atrasadas', formatarNumero(dados.tarefasAtrasadas)],
          ]}
        />

        <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
          Números do momento, sem recorte de período. Filtro por data, comparação entre períodos e
          exportação entram no construtor de relatórios, que é P2.
        </p>
      </div>
    </Pagina>
  );
}

function Tabela({
  titulo,
  linhas,
}: {
  readonly titulo: string;
  readonly linhas: readonly (readonly [string, string])[];
}) {
  return (
    <section>
      <h2
        className="mb-2 text-[11px] font-semibold uppercase tracking-wider"
        style={{ color: 'var(--text-muted)' }}
      >
        {titulo}
      </h2>
      <Cartao className="p-0">
        <dl>
          {linhas.map(([rotulo, valor], indice) => (
            <div
              key={rotulo}
              className="flex items-baseline justify-between gap-4 px-4 py-2.5"
              style={{
                borderTop: indice === 0 ? undefined : '1px solid var(--border-subtle)',
              }}
            >
              <dt className="text-sm" style={{ color: 'var(--text-muted)' }}>
                {rotulo}
              </dt>
              <dd className="text-sm font-medium tabular-nums">{valor}</dd>
            </div>
          ))}
        </dl>
      </Cartao>
    </section>
  );
}
