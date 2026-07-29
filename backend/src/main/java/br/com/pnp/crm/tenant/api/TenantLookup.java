package br.com.pnp.crm.tenant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Única porta pelo qual outro módulo obtém um tenant.
 *
 * <p>Devolve o id e nada mais. Nome, plano e configuração da conta ficam
 * dentro do módulo {@code tenant} — quem precisa disso pede ao módulo dono,
 * em vez de receber a entidade inteira e passar a depender do formato dela.
 */
public interface TenantLookup {

    /**
     * Traduz o slug informado na tela de login em um id de tenant.
     *
     * <p>Só considera contas ativas e não excluídas. Devolve vazio para slug
     * inexistente, inativo ou excluído — sem distinguir os casos, porque o
     * chamador é o login e a distinção viraria informação para quem sonda.
     */
    Optional<UUID> resolverIdPorSlug(String slug);
}
