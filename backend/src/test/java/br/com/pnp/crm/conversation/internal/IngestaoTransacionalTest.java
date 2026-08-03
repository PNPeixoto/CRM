package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.channel.api.InboundMessage;
import br.com.pnp.crm.channel.api.TipoConteudo;
import br.com.pnp.crm.conversation.api.MensagemRecebidaEvent;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@TesteDeIntegracao
@Import(IngestaoTransacionalTest.ProbeConfiguration.class)
class IngestaoTransacionalTest {

    @Autowired CenarioMultiTenant scenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired IngestaoDeMensagemService ingestion;
    @Autowired TransactionProbe probe;

    private UUID tenantId;
    private UUID channelId;

    @BeforeEach
    void setUp() {
        scenario.limpar();
        probe.reset();
        tenantId = scenario.criarTenant("alpha", "Alpha");
        UUID userId = scenario.criarUsuario(tenantId, "ana", "12345");
        channelId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO channel_connection (id, tenant_id, kind, name, created_by, updated_by)
                VALUES (?, ?, 'LIVE_CHAT', 'Chat', ?, ?)
                """, channelId, tenantId, userId, userId));
    }

    @AfterEach
    void tearDown() {
        scenario.limpar();
    }

    @Test
    void publishesMessageEventInsideARealTransaction() {
        var result = ingestion.registrar(new InboundMessage(
                channelId, "external-1", "visitor-1", "Visitante", TipoConteudo.TEXT,
                "Olá", Instant.now(), Map.of("source", "test")));

        assertThat(result).isPresent();
        assertThat(probe.transactionWasActive()).isTrue();
    }

    @Test
    void concurrentDuplicateIsPersistedOnlyOnceWithoutAbortingTheTransaction() throws Exception {
        InboundMessage input = new InboundMessage(
                channelId, "external-concurrent", "visitor-1", "Visitante", TipoConteudo.TEXT,
                "Olá", Instant.now(), Map.of("source", "test"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var ingestion = (java.util.concurrent.Callable<Optional<UUID>>) () -> {
                ready.countDown();
                start.await();
                return this.ingestion.registrar(input);
            };
            var first = executor.submit(ingestion);
            var second = executor.submit(ingestion);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .satisfiesExactlyInAnyOrder(
                            result -> assertThat(result).isPresent(),
                            result -> assertThat(result).isEmpty());
        }

        Long messages = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM message WHERE external_id = 'external-concurrent'",
                Long.class));
        Long conversations = TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM conversation WHERE external_contact_id = 'visitor-1'",
                Long.class));
        assertThat(messages).isOne();
        assertThat(conversations).isOne();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        TransactionProbe transactionProbe() {
            return new TransactionProbe();
        }
    }

    static class TransactionProbe {
        private final AtomicBoolean transactionWasActive = new AtomicBoolean();

        @EventListener
        void on(MensagemRecebidaEvent ignored) {
            transactionWasActive.set(TransactionSynchronizationManager.isActualTransactionActive());
        }

        boolean transactionWasActive() {
            return transactionWasActive.get();
        }

        void reset() {
            transactionWasActive.set(false);
        }
    }
}
