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
                .setParameter("slug", slug)
                .getResultStream()
                .findFirst()
                .map(UUID.class::cast);
    }
}
