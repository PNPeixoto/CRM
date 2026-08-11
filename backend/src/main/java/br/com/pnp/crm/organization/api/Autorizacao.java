package br.com.pnp.crm.organization.api;

import java.util.Set;
import java.util.UUID;

/**
 * Ponto único de decisão de autorização do backend.
 *
 * <p>Existe para que nenhum módulo precise reimplementar a leitura de
 * membership, papel e escopo. Autorização espalhada é autorização que diverge:
 * o módulo que for escrito depois esquece uma condição, e a diferença só
 * aparece quando alguém explora.
 *
 * <p><b>O tenant não é parâmetro.</b> Ele vem do {@code TenantContext}, que por
 * sua vez vem do token verificado. Aceitá-lo aqui abriria a porta para um
 * chamador informar o tenant errado — exatamente o que o
 * `01-padroes-tecnicos.md` proíbe.
 */
public interface Autorizacao {

    /**
     * Falha quando o usuário corrente não tem a permissão em nenhum escopo.
     *
     * @throws br.com.pnp.crm.shared.api.AcessoNegadoException sempre que negar
     */
    void exigir(Permissao permissao);

    /**
     * Exige apenas vínculo ativo com o tenant corrente.
     *
     * <p>Usado por endpoints de bootstrap que todo membro precisa consultar,
     * como apresentação e navegação. Eles não concedem ação de domínio nem
     * substituem uma permissão nas mutações.
     */
    void exigirMembershipVigente();

    /**
     * Exige que a permissão tenha sido concedida para todo o tenant.
     *
     * <p>Recursos compartilhados, como configuração de canal, apresentação da
     * empresa, relatório consolidado e inbox coletiva, não possuem responsável
     * por registro. Aceitar uma concessão {@link Alcance#PROPRIO} nesses
     * endpoints transformaria um alcance restrito em acesso ao tenant inteiro.
     */
    void exigirNoTenant(Permissao permissao);

    /**
     * Alcance efetivo do usuário corrente para uma permissão.
     *
     * <p>É o que permite a uma listagem filtrar sem duplicar a regra: quem tem
     * {@link Alcance#TENANT} enxerga tudo do tenant; quem tem
     * {@link Alcance#PROPRIO} enxerga apenas o que lhe pertence.
     *
     * @throws br.com.pnp.crm.shared.api.AcessoNegadoException se não houver
     *                                                        permissão alguma
     */
    Alcance alcanceDe(Permissao permissao);

    /**
     * Confirma que o usuário pode agir sobre um registro específico.
     *
     * <p>Sob {@link Alcance#TENANT} sempre passa — o RLS já garante o tenant.
     * Sob {@link Alcance#EQUIPE}, exige que o responsável seja o próprio usuário
     * ou alguém da sua equipe. Sob {@link Alcance#PROPRIO}, exige que seja o
     * próprio usuário. Registro sem responsável é tratado como <b>negado</b>
     * fora do alcance de tenant: dono ausente não é dono presente, e liberar por
     * omissão é como dado órfão vira dado de todos.
     *
     * @param responsavelId responsável do registro, possivelmente {@code null}
     */
    void exigirSobreRegistro(Permissao permissao, UUID responsavelId);

    /**
     * Recorte a aplicar na consulta de uma listagem.
     *
     * <p>Existe porque {@link #alcanceDe} responde "qual alcance", e quem monta
     * uma listagem precisa de "quais responsáveis" — e sob
     * {@link Alcance#EQUIPE} isso é um conjunto, não um id. Devolver o alcance e
     * deixar cada controller traduzir faria a mesma regra ser reescrita em três
     * módulos, que é como duas delas passam a divergir.
     */
    Recorte recorteDe(Permissao permissao);

    /**
     * Quem a listagem pode enxergar.
     *
     * <p>{@code todoOTenant} verdadeiro dispensa o conjunto: o RLS já limita ao
     * tenant, e não há recorte adicional. Caso contrário, {@code responsaveis}
     * traz exatamente os ids permitidos — nunca vazio, porque o próprio usuário
     * sempre está incluído.
     */
    record Recorte(boolean todoOTenant, Set<UUID> responsaveis) {
        public Recorte {
            responsaveis = Set.copyOf(responsaveis);
        }

        public static Recorte tenantInteiro() {
            return new Recorte(true, Set.of());
        }

        public static Recorte apenas(Set<UUID> responsaveis) {
            return new Recorte(false, responsaveis);
        }
    }

    /** Id do usuário autenticado na requisição corrente. */
    UUID usuarioCorrente();

    /**
     * Responsável a gravar quando a requisição não informa um.
     *
     * <p>Quando a requisição omite o responsável, o autor assume a
     * responsabilidade em qualquer alcance. Isso mantém todo contato, tarefa e
     * oportunidade identificável para a conta gestora e impede registros órfãos.
     * Uma fila explicitamente não atribuída exige um caso de uso próprio, em vez
     * de reutilizar a ausência acidental desse campo.
     */
    default UUID responsavelPadrao(Permissao permissao, UUID informado) {
        if (informado == null) {
            alcanceDe(permissao);
            return usuarioCorrente();
        }
        return informado;
    }

    /**
     * Alcance efetivo. Nomeado em português para não colidir com
     * {@link OrganizationAccess.ScopeType}, que descreve o que está
     * <i>persistido</i> — este descreve o que foi <i>decidido</i>.
     *
     * <p>{@code UNIT} não aparece aqui: as tabelas de domínio não carregam
     * unidade, então não há como aplicá-lo sem inventar um filtro que o banco
     * não sustenta. Ver ADR-0008.
     *
     * <p>{@code EQUIPE} aparece porque o filtro <b>existe</b>: o responsável já
     * é coluna indexada em contato, oportunidade e tarefa desde a V5, e a
     * composição da equipe é uma tabela própria. Ver ADR-0015.
     *
     * <p>A ordem das constantes é do mais amplo para o mais restrito, e há
     * teste que depende disso ao escolher o alcance efetivo.
     */
    enum Alcance {
        /** Enxerga e altera todo o tenant. */
        TENANT,
        /** Enxerga e altera o que é seu e o de quem responde a você. */
        EQUIPE,
        /** Enxerga e altera apenas os registros sob sua responsabilidade. */
        PROPRIO
    }
}
