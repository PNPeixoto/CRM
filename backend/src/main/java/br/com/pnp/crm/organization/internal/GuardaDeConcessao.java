package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.Autorizacao;
import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.organization.api.Permissao;
import br.com.pnp.crm.shared.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Impede que administrar papeis vire escalonamento de privilegio.
 *
 * <p><b>O problema.</b> {@code organization.manage} da a alguem o poder de criar
 * papel e atribui-lo. Sem restricao, essa pessoa escreveria um papel com
 * {@code privacy.manage} e {@code audit.read}, atribuiria a si mesma, e teria
 * concedido a si o que ninguem lhe deu. A permissao de administrar precisa
 * significar "administrar dentro do que eu tenho", nunca "tornar-me qualquer
 * coisa".
 *
 * <p><b>A invariante.</b> Toda concessao e subconjunto do privilegio de quem
 * concede. Como subconjunto de subconjunto continua subconjunto, o conjunto de
 * privilegios do tenant nunca cresce por delegacao: quem entra depois nao pode
 * exceder quem concedeu. E o que {@code NaoEscalonamentoTest} verifica.
 *
 * <p><b>Escopo de requisicao.</b> O mapa do autor e lido uma vez e reusado — um
 * unico pedido chega a validar dezenas de permissoes, e reconsultar a cada uma
 * multiplicaria por dezenas a consulta com juncoes temporais. Nunca entre
 * requisicoes: revogar precisa valer na chamada seguinte.
 */
@Service
@RequestScope
class GuardaDeConcessao {

    private final OrganizationAccess acessos;
    private final Autorizacao autorizacao;

    private Map<String, OrganizationAccess.ScopeType> minhas;

    GuardaDeConcessao(OrganizationAccess acessos, Autorizacao autorizacao) {
        this.acessos = acessos;
        this.autorizacao = autorizacao;
    }

    /**
     * Recusa se o autor nao puder delegar todas as permissoes sob o alcance.
     *
     * <p>A verificacao e <b>por permissao</b>, nunca pelo conjunto. Quem tem
     * {@code contacts.read} em TENANT e {@code deals.write} apenas em OWN nao
     * pode conceder {@code deals.write} em TENANT so porque tem <i>alguma</i>
     * coisa em TENANT — e exatamente esse o atalho que uma implementacao
     * ingenua toma.
     */
    void exigirPoderDelegar(Collection<String> permissoes,
                            OrganizationAccess.ScopeType alcance) {
        for (String codigo : permissoes) {
            OrganizationAccess.ScopeType minha = minhas().get(codigo);
            if (minha == null || !podeDelegar(minha, alcance)) {
                throw new ConcessaoAcimaDoPrivilegioException();
            }
        }
    }

    /**
     * Recusa mexer em papel que concede mais do que o autor possui.
     *
     * <p>Sem isto sobra um caminho que nao passa por concessao nenhuma: o autor
     * pega um papel poderoso que jamais poderia ter criado, renomeia,
     * acrescenta o que tem, e ja esta com um papel util e privilegiado nas maos.
     * A guarda de atribuicao sozinha nao fecha isso, porque editar um papel
     * <b>ja atribuido a outra pessoa</b> altera o privilegio dela sem nenhuma
     * atribuicao nova acontecer.
     */
    void exigirPoderGerenciar(Collection<String> permissoesAtuais) {
        for (String codigo : permissoesAtuais) {
            if (!minhas().containsKey(codigo)) {
                throw new ConcessaoAcimaDoPrivilegioException();
            }
        }
    }

    /** Permissoes do catalogo que o autor consegue conceder sob o alcance. */
    Set<String> delegaveis(OrganizationAccess.ScopeType alcance) {
        return Set.copyOf(minhas().entrySet().stream()
                .filter(entrada -> podeDelegar(entrada.getValue(), alcance))
                .map(Map.Entry::getKey)
                .filter(GuardaDeConcessao::doCatalogo)
                .toList());
    }

    /**
     * Regra de delegacao, dita por extenso em vez de comparar ordinais.
     *
     * <p>Comparar {@code ordinal()} pareceria mais curto e estaria errado:
     * UNIT e NETWORK ficam entre TENANT e OWN na ordem do enum e <b>nao decidem
     * sobre registro de dominio</b> (ADR-0008). Quem so os tem nao exerce
     * autoridade nenhuma — e portanto nao pode delegar autoridade real a
     * terceiros. Falha fechada.
     *
     * <p><b>O alcance e relativo, e e isso que torna a regra coerente.</b>
     * Conceder OWN a alguem nao entrega os <i>meus</i> registros: entrega os
     * dele. O mesmo vale para TEAM. A invariante e sobre o <b>tipo</b> de
     * recorte — nunca conceder um recorte mais amplo do que o proprio —, e nao
     * sobre um conjunto absoluto de ids, que mudaria a cada contratacao.
     */
    private static boolean podeDelegar(OrganizationAccess.ScopeType minha,
                                       OrganizationAccess.ScopeType concedida) {
        return switch (minha) {
            // O tenant inteiro contem qualquer recorte menor.
            case TENANT -> concedida == OrganizationAccess.ScopeType.TENANT
                    || concedida == OrganizationAccess.ScopeType.TEAM
                    || concedida == OrganizationAccess.ScopeType.OWN;
            // Quem enxerga a propria equipe delega equipe ou proprio, nunca o
            // tenant inteiro.
            case TEAM -> concedida == OrganizationAccess.ScopeType.TEAM
                    || concedida == OrganizationAccess.ScopeType.OWN;
            // Quem so enxerga o proprio so delega o proprio.
            case OWN -> concedida == OrganizationAccess.ScopeType.OWN;
            default -> false;
        };
    }

    /**
     * Codigo fora do catalogo compilado nao e delegavel.
     *
     * <p>{@code role_permission} aceita qualquer texto no formato do CHECK,
     * inclusive de modulo que ainda nao existe. Devolver esses codigos como
     * concedeveis exportaria como catalogo aquilo que alguem inventou direto no
     * banco.
     */
    private static boolean doCatalogo(String codigo) {
        for (Permissao permissao : Permissao.values()) {
            if (permissao.codigo().equals(codigo)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, OrganizationAccess.ScopeType> minhas() {
        if (minhas == null) {
            minhas = acessos.permissionScopes(
                    TenantContext.obrigatorio(), autorizacao.usuarioCorrente());
        }
        return minhas;
    }
}
