package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@TesteDeIntegracao
class RegistroDeEventoRecebidoTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistroDeEventoRecebido registro;

    private UUID tenantId;
    private UUID conexaoId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenantId = cenario.criarTenant("webhook-v15", "Webhook V15");
        conexaoId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update(
                "INSERT INTO channel_connection (id, tenant_id, kind, name) VALUES (?, ?, 'TELEGRAM', 'Bot')",
                conexaoId, tenantId));
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void duplicataConcorrenteConvergeEPayloadFicaCifrado() throws Exception {
        byte[] payload = "{\"update_id\":901,\"message\":{\"text\":\"segredo do cliente\"}}"
                .getBytes(StandardCharsets.UTF_8);
        CountDownLatch largada = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> gravarDepoisDaLargada(largada, payload));
            var segunda = executor.submit(() -> gravarDepoisDaLargada(largada, payload));
            largada.countDown();
            primeira.get();
            segunda.get();
        }

        TenantContext.executarComo(tenantId, () -> {
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM inbound_event WHERE channel_connection_id = ?",
                    Long.class, conexaoId)).isOne();
            var linha = jdbc.queryForMap("""
                    SELECT payload, payload_ciphertext, payload_sha256
                      FROM inbound_event WHERE channel_connection_id = ?
                    """, conexaoId);
            assertThat(linha.get("payload")).isNull();
            assertThat((String) linha.get("payload_ciphertext"))
                    .doesNotContain("segredo do cliente");
            assertThat((String) linha.get("payload_sha256")).hasSize(64);
            return null;
        });
    }

    private void gravarDepoisDaLargada(CountDownLatch largada, byte[] payload) {
        try {
            largada.await();
            TenantContext.executarComo(tenantId, () -> {
                registro.gravar(tenantId, conexaoId, payload);
                return null;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
