package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** Expurga arquivos por chave opaca e atualiza metadados sob o RLS do tenant. */
@Component
@ConditionalOnProperty(
        name = "app.providers.telegram.media-retention-enabled",
        havingValue = "true", matchIfMissing = true)
class RetencaoDeMidiaWorker {

    private final EntityManager entityManager;
    private final QuarentenaDeMidia quarentena;
    private final TransactionTemplate transacoes;

    RetencaoDeMidiaWorker(EntityManager entityManager, QuarentenaDeMidia quarentena,
                          PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.quarentena = quarentena;
        this.transacoes = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.providers.telegram.media-retention-schedule-ms:3600000}")
    void expurgar() {
        for (MidiaExpirada midia : reservar()) {
            quarentena.remover(midia.storageKey());
            TenantContext.executarComo(midia.tenantId(), () -> {
                transacoes.executeWithoutResult(status -> concluir(midia.id()));
                return null;
            });
        }
    }

    List<MidiaExpirada> reservar() {
        return transacoes.execute(status -> reservarNaTransacao());
    }

    private List<MidiaExpirada> reservarNaTransacao() {
        List<Tuple> linhas = entityManager.createNativeQuery("""
                SELECT media_id, media_tenant_id, media_storage_key
                  FROM reservar_midias_expiradas(100)
                """, Tuple.class).getResultList();
        return linhas.stream().map(l -> new MidiaExpirada(
                l.get(0, UUID.class), l.get(1, UUID.class), l.get(2, String.class))).toList();
    }

    void concluir(UUID mediaId) {
        entityManager.createNativeQuery("""
                UPDATE channel_media
                   SET status = 'PURGED', storage_key = NULL, purge_lease_until = NULL
                 WHERE id = :id AND tenant_id = :tenant AND status = 'PURGING'
                """)
                .setParameter("id", mediaId)
                .setParameter("tenant", TenantContext.obrigatorio())
                .executeUpdate();
    }

    record MidiaExpirada(UUID id, UUID tenantId, String storageKey) {
    }
}
