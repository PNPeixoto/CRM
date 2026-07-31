package br.com.pnp.crm.tenant.internal;

import br.com.pnp.crm.tenant.api.TenantLookup;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
class TenantLookupImpl implements TenantLookup {

    private final EntityManager entityManager;

    TenantLookupImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Consulta a função {@code resolve_tenant_id_por_slug} em vez de fazer um
     * SELECT direto na tabela.
     *
     * <p>O motivo é o RLS: a tabela {@code tenant} está sob política que
     * compara a linha com {@code app.tenant_id}, e no login esse valor ainda
     * não existe — um SELECT normal devolveria vazio, sempre. A função é
     * {@code SECURITY DEFINER} e atravessa a política, mas só sabe fazer esta
     * pergunta e só devolve o id.
     *
     * <p>A alternativa seria deixar a tabela {@code tenant} fora do RLS.
     * Custaria expor a carteira de clientes da plataforma a qualquer consulta
     * que escapasse do filtro em código — exatamente o risco que o RLS existe
     * para cobrir.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> resolverIdPorSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return entityManager
                .createNativeQuery("SELECT resolve_tenant_id_por_slug(:slug)", UUID.class)
                .setParameter("slug", normalizar(slug))
                .getResultStream()
                .findFirst()
                .map(UUID.class::cast);
    }

    /**
     * O slug é gravado em minúsculas — o CHECK da coluna na V1 só aceita esse
     * formato. Normalizar a ENTRADA, e não afrouxar a consulta para
     * {@code lower(slug) = lower(:slug)}, é o que mantém o índice único
     * utilizável: uma função sobre a coluna faria o planejador ignorá-lo.
     *
     * <p>Sem isto, quem digita "PNP" recebe exatamente a mesma resposta de
     * quem errou a senha — e a resposta uniforme, que existe para impedir
     * enumeração de contas, viraria um beco sem saída para o usuário legítimo.
     * Espaço nas pontas também some: é o caso de quem cola o valor.
     */
    private String normalizar(String slug) {
        return slug.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
