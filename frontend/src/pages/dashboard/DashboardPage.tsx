import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { formatarMoeda, formatarNumero } from '@/shared/formato';
import { EstadoDeConteudo } from '@/components/ui/content-state';
import { Cartao, Carregando, Pagina } from '@/shared/components/Pagina';
import { useVisaoGeral } from '@/shared/server-state/recursos';

export function DashboardPage() {
  const dadosQuery = useVisaoGeral();
  const dados = dadosQuery.data;

  return (
    <Pagina titulo="Visão geral" descricao="O que precisa da sua atenção agora">
      {dadosQuery.isPending ? (
        <Carregando />
      ) : dadosQuery.isError || !dados ? (
        <EstadoDeConteudo
          tipo="erro"
          titulo="Não foi possível carregar os indicadores"
          descricao="Atualize a página para tentar novamente."
        />
      ) : (
        <div className="space-y-6">
          {/* Atendimento primeiro: é o que tem urgência. Faturamento não muda
              no minuto seguinte; cliente esperando, sim. */}
          <Secao titulo="Atendimento">
            <Kpi
              rotulo="Conversas abertas"
              valor={formatarNumero(dados.conversasAbertas)}
              destino="/inbox"
            />
            <Kpi rotulo="Aguardando cliente" valor={formatarNumero(dados.conversasAguardando)} />
            <Kpi rotulo="Mensagens hoje" valor={formatarNumero(dados.mensagensRecebidasHoje)} />
            <Kpi
              rotulo="Canais ativos"
              valor={formatarNumero(dados.canaisAtivos)}
              destino="/integracoes"
              alerta={dados.canaisAtivos === 0}
            />
          </Secao>

          <Secao titulo="Funil">
            <Kpi
              rotulo="Em aberto"
              valor={formatarNumero(dados.oportunidadesAbertas)}
              apoio={formatarMoeda(dados.valorAbertoCentavos)}
              destino="/funis"
            />
            <Kpi
              rotulo="Ganhas"
              valor={formatarNumero(dados.oportunidadesGanhas)}
              apoio={formatarMoeda(dados.valorGanhoCentavos)}
            />
            <Kpi rotulo="Perdidas" valor={formatarNumero(dados.oportunidadesPerdidas)} />
            <Kpi
              rotulo="Conversão"
              valor={`${String(dados.taxaDeConversaoPercentual).replace('.', ',')}%`}
              apoio="sobre as fechadas"
            />
          </Secao>

          <Secao titulo="Rotina">
            <Kpi
              rotulo="Tarefas abertas"
              valor={formatarNumero(dados.tarefasAbertas)}
              destino="/tarefas"
            />
            <Kpi
              rotulo="Atrasadas"
              valor={formatarNumero(dados.tarefasAtrasadas)}
              destino="/tarefas"
              alerta={dados.tarefasAtrasadas > 0}
            />
            <Kpi
              rotulo="Contatos"
              valor={formatarNumero(dados.totalDeContatos)}
              destino="/contatos"
            />
          </Secao>
        </div>
      )}
    </Pagina>
  );
}

function Secao({ titulo, children }: { readonly titulo: string; readonly children: ReactNode }) {
  return (
    <section>
      <h2
        className="mb-2 text-[11px] font-semibold uppercase tracking-wider"
        style={{ color: 'var(--text-muted)' }}
      >
        {titulo}
      </h2>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{children}</div>
    </section>
  );
}

function Kpi({
  rotulo,
  valor,
  apoio,
  destino,
  alerta = false,
}: {
  readonly rotulo: string;
  readonly valor: string;
  readonly apoio?: string;
  readonly destino?: string;
  readonly alerta?: boolean;
}) {
  const conteudo = (
    <Cartao className={destino ? 'transition-colors hover:border-[var(--brand)]' : undefined}>
      <p className="text-xs" style={{ color: 'var(--text-muted)' }}>
        {rotulo}
      </p>
      <p
        className="mt-1 text-2xl font-semibold tabular-nums"
        // A cor reforça, não informa: o número já diz que há 12 tarefas
        // atrasadas, e quem não distingue a cor lê o mesmo.
        style={{ color: alerta ? 'var(--danger)' : 'var(--text-strong)' }}
      >
        {valor}
      </p>
      {apoio && (
        <p className="mt-0.5 text-xs tabular-nums" style={{ color: 'var(--text-muted)' }}>
          {apoio}
        </p>
      )}
    </Cartao>
  );

  return destino ? (
    <Link to={destino} className="block rounded-[var(--radius-surface)]">
      {conteudo}
    </Link>
  ) : (
    conteudo
  );
}
