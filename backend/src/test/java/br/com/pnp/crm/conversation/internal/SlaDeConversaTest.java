package br.com.pnp.crm.conversation.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TesteDeIntegracao
class SlaDeConversaTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConversationRepository conversas;
    @Autowired SlaDeConversaService sla;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID tenant;
    private UUID usuario;
    private UUID conversa;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("sla", "SLA");
        usuario = cenario.criarUsuario(tenant, "operador", "12345");
        conversa = criarConversa();
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO tenant_profile
                    (tenant_id, business_segment, preset_version, time_zone)
                VALUES (?, 'GENERAL_SERVICES', 1, 'America/Montevideo')
                """, tenant));
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void inicioERespostaSaoIdempotentesEPersistemOPrazo() {
        Instant recebidaEm = Instant.parse("2026-08-09T12:00:00Z");
        emTransacao(() -> {
            ConversationEntity entidade = conversas.findById(conversa).orElseThrow();
            sla.iniciarSeNecessario(entidade, recebidaEm);
            sla.iniciarSeNecessario(entidade, recebidaEm);
        });

        TenantContext.executarComo(tenant, () -> {
            assertThat(jdbc.queryForObject(
                    "SELECT due_at FROM conversation WHERE id = ?", Instant.class, conversa))
                    .isEqualTo(recebidaEm.plusSeconds(15 * 60));
            assertThat(contarEvento("STARTED")).isOne();
            return null;
        });

        emTransacao(() -> {
            sla.satisfazer(conversa, recebidaEm.plusSeconds(60));
            sla.satisfazer(conversa, recebidaEm.plusSeconds(90));
        });

        TenantContext.executarComo(tenant, () -> {
            assertThat(jdbc.queryForObject(
                    "SELECT due_at IS NULL FROM conversation WHERE id = ?",
                    Boolean.class, conversa)).isTrue();
            assertThat(contarEvento("SATISFIED")).isOne();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM realtime_event WHERE event_type = 'SLA_CHANGED'",
                    Long.class)).isEqualTo(2);
            return null;
        });
    }

    @Test
    void verificadorDeVencimentoNaoDuplicaOEvento() {
        Instant recebidaEm = Instant.parse("2026-08-09T12:00:00Z");
        emTransacao(() -> sla.iniciarSeNecessario(
                conversas.findById(conversa).orElseThrow(), recebidaEm));
        Instant prazo = recebidaEm.plusSeconds(15 * 60);

        emTransacao(() -> {
            sla.marcarVencido(conversa, prazo);
            sla.marcarVencido(conversa, prazo);
        });

        TenantContext.executarComo(tenant, () -> {
            assertThat(contarEvento("BREACHED")).isOne();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM realtime_event WHERE event_type = 'SLA_CHANGED'",
                    Long.class)).isEqualTo(2);
            return null;
        });
    }

    private void emTransacao(Runnable trabalho) {
        TenantContext.executarComo(tenant, () -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> trabalho.run());
            return null;
        });
    }

    private long contarEvento(String tipo) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM conversation_sla_event
                 WHERE conversation_id = ? AND event_type = ?
                """, Long.class, conversa, tipo);
    }

    private UUID criarConversa() {
        UUID canal = UuidV7.gerar();
        UUID conversaId = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> {
            jdbc.update("""
                    INSERT INTO channel_connection (id, tenant_id, kind, name, created_by, updated_by)
                    VALUES (?, ?, 'LIVE_CHAT', 'Chat SLA', ?, ?)
                    """, canal, tenant, usuario, usuario);
            jdbc.update("""
                    INSERT INTO conversation
                        (id, tenant_id, channel_connection_id, external_contact_id, status,
                         assigned_user_id, created_by, updated_by)
                    VALUES (?, ?, ?, 'cliente-sla', 'OPEN', ?, ?, ?)
                    """, conversaId, tenant, canal, usuario, usuario, usuario);
            return null;
        });
        return conversaId;
    }
}
