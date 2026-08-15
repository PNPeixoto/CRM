package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TesteDeIntegracao
class FundacaoOmnichannelTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID conexaoId;
    private UUID conversaId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenantId = cenario.criarTenant("foundation-v15", "Foundation V15");
        conexaoId = UuidV7.gerar();
        conversaId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("INSERT INTO channel_connection (id, tenant_id, kind, name) VALUES (?, ?, 'LIVE_CHAT', 'Site')",
                    conexaoId, tenantId);
            jdbc.update("INSERT INTO conversation (id, tenant_id, channel_connection_id, external_contact_id) VALUES (?, ?, ?, 'visitor')",
                    conversaId, tenantId, conexaoId);
            return null;
        });
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void reservaRegistraLeaseFilaMortaEReprocessamento() {
        UUID mensagemId = novaMensagemPendente();

        jdbc.queryForList(
                "SELECT * FROM reservar_mensagens_para_envio(10, 1, 30)");

        TenantContext.executarComo(tenantId, () -> {
            var reservada = jdbc.queryForMap("""
                    SELECT lease_owner, lease_until, dead_lettered_at, attempt_count
                      FROM message WHERE id = ?
                    """, mensagemId);
            assertThat(reservada.get("lease_owner")).isNotNull();
            assertThat(reservada.get("lease_until")).isNotNull();
            assertThat(reservada.get("dead_lettered_at")).isNotNull();
            assertThat(reservada.get("attempt_count")).isEqualTo(1);

            assertThat(jdbc.queryForObject(
                    "SELECT reprocessar_mensagem_saida(?)", Boolean.class, mensagemId)).isTrue();
            var reaberta = jdbc.queryForMap("""
                    SELECT lease_owner, dead_lettered_at, attempt_count, status
                      FROM message WHERE id = ?
                    """, mensagemId);
            assertThat(reaberta.get("lease_owner")).isNull();
            assertThat(reaberta.get("dead_lettered_at")).isNull();
            assertThat(reaberta.get("attempt_count")).isEqualTo(0);
            assertThat(reaberta.get("status")).isEqualTo("PENDING");
            return null;
        });
    }

    @Test
    void medicaoEhIdempotenteAppendOnlyEIsoladaPorTenant() {
        UUID mensagemId = novaMensagemPendente();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("UPDATE message SET status = 'SENT', sent_at = now() WHERE id = ?", mensagemId);
            jdbc.update("UPDATE message SET text_content = 'nao mede de novo' WHERE id = ?", mensagemId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM usage_event WHERE message_id = ?", Long.class, mensagemId))
                    .isOne();
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE usage_event SET quantity = 2 WHERE message_id = ?", mensagemId))
                    .isInstanceOf(DataAccessException.class)
                    .rootCause()
                    .hasMessageContaining("permission denied");
            return null;
        });

        UUID outroTenant = cenario.criarTenant("foundation-other", "Other");
        TenantContext.executarComo(outroTenant, () -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usage_event", Long.class)).isZero());
    }

    @Test
    void retencaoExpurgaCorpoMasPreservaHash() {
        UUID eventoId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO inbound_event (
                    id, tenant_id, channel_connection_id, external_event_id,
                    payload, payload_sha256, payload_retain_until, processed_at
                ) VALUES (?, ?, ?, 'legacy-1', '{"ok":true}', repeat('a', 64),
                          now() - interval '1 minute', now())
                """, eventoId, tenantId, conexaoId));

        assertThat(jdbc.queryForObject(
                "SELECT expurgar_payloads_eventos_recebidos(100)", Integer.class)).isOne();

        TenantContext.executarComo(tenantId, () -> {
            var linha = jdbc.queryForMap("""
                    SELECT payload, payload_ciphertext, payload_sha256, payload_purged_at
                      FROM inbound_event WHERE id = ?
                    """, eventoId);
            assertThat(linha.get("payload")).isNull();
            assertThat(linha.get("payload_ciphertext")).isNull();
            assertThat(linha.get("payload_sha256")).isEqualTo("a".repeat(64));
            assertThat(linha.get("payload_purged_at")).isNotNull();
            return null;
        });
    }

    @Test
    void midiaEhMedidaIsoladaEReservadaParaRetencao() {
        UUID mediaId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO channel_media (
                    id, tenant_id, channel_connection_id, external_file_id,
                    storage_key, detected_mime_type, byte_size, sha256,
                    status, retain_until
                ) VALUES (?, ?, ?, 'file-1', 'tenant/media.bin', 'image/jpeg',
                          6, repeat('b', 64), 'QUARANTINED', now() - interval '1 minute')
                """, mediaId, tenantId, conexaoId));

        TenantContext.executarComo(tenantId, () -> assertThat(jdbc.queryForObject("""
                SELECT quantity FROM usage_event
                 WHERE idempotency_key = ?
                """, Long.class, "media_bytes_stored:" + mediaId)).isEqualTo(6L));

        var reservadas = jdbc.queryForList("SELECT * FROM reservar_midias_expiradas(10)");
        assertThat(reservadas).hasSize(1);
        assertThat(reservadas.getFirst().get("media_id")).isEqualTo(mediaId);
        TenantContext.executarComo(tenantId, () -> {
            var emExpurgo = jdbc.queryForMap("""
                    SELECT status, purge_lease_until FROM channel_media WHERE id = ?
                    """, mediaId);
            assertThat(emExpurgo.get("status")).isEqualTo("PURGING");
            assertThat(emExpurgo.get("purge_lease_until")).isNotNull();
            jdbc.update("""
                    UPDATE channel_media SET purge_lease_until = now() - interval '1 minute'
                     WHERE id = ?
                    """, mediaId);
            return null;
        });
        assertThat(jdbc.queryForList("SELECT * FROM reservar_midias_expiradas(10)"))
                .extracting(linha -> linha.get("media_id"))
                .containsExactly(mediaId);

        UUID outroTenant = cenario.criarTenant("media-other", "Media Other");
        TenantContext.executarComo(outroTenant, () -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM channel_media", Long.class)).isZero());
    }

    private UUID novaMensagemPendente() {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO message (id, tenant_id, conversation_id, channel_connection_id,
                                     direction, content_type, text_content, status)
                VALUES (?, ?, ?, ?, 'OUTBOUND', 'TEXT', 'ola', 'PENDING')
                """, id, tenantId, conversaId, conexaoId));
        return id;
    }
}
