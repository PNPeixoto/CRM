package br.com.pnp.crm.audit.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.audit.api.AuditTrail;
import br.com.pnp.crm.shared.api.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TesteDeIntegracao
class AuditoriaCorporativaTest {

    @Autowired CenarioMultiTenant scenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditTrail audit;
    @Autowired AuditQueryService queries;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID tenantA;
    private UUID userA;

    @BeforeEach
    void setUp() {
        scenario.limpar();
        tenantA = scenario.criarTenant("audit-a", "Audit A");
        userA = scenario.criarUsuario(tenantA, "alice", "12345");
    }

    @AfterEach
    void tearDown() {
        scenario.limpar();
    }

    @Test
    void registraCatalogoCriticoSemConteudoLivreEVerificaIntegridadeNaLeitura() {
        registrarCritico(AuditTrail.Acao.CREDENTIAL_CHANGED,
                AuditTrail.TipoAlvo.HTTP_CONNECTOR,
                AuditTrail.Motivo.CONNECTOR_CREDENTIAL_CHANGED);
        registrarCritico(AuditTrail.Acao.ROLE_CHANGED,
                AuditTrail.TipoAlvo.ROLE,
                AuditTrail.Motivo.ROLE_ASSIGNMENT_CHANGED);
        registrarCritico(AuditTrail.Acao.SENSITIVE_CONFIGURATION_CHANGED,
                AuditTrail.TipoAlvo.TENANT_PROFILE,
                AuditTrail.Motivo.TENANT_PRESENTATION_CHANGED);
        registrarCritico(AuditTrail.Acao.EXPORT_REQUESTED,
                AuditTrail.TipoAlvo.EXPORT,
                AuditTrail.Motivo.EXPORT_REQUESTED);

        AuditQueryService.Pagina pagina = TenantContext.executarComo(tenantA,
                () -> queries.listar(userA, null, null, null, 100));

        assertThat(pagina.integridadeVerificada()).isTrue();
        assertThat(pagina.eventos()).extracting(AuditQueryService.EventoResponse::acao)
                .contains("CREDENTIAL_CHANGED_V1", "ROLE_CHANGED_V1",
                        "SENSITIVE_CONFIGURATION_CHANGED_V1", "EXPORT_REQUESTED_V1",
                        "AUDIT_READ_V1");
        assertThat(pagina.eventos()).allSatisfy(evento -> {
            assertThat(evento.atorId()).isEqualTo(userA);
            assertThat(evento.atorNome()).contains("alice");
            assertThat(evento.correlacaoId()).isNotNull();
        });

        List<String> colunas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'audit_event'
                """, String.class);
        assertThat(colunas).noneMatch(nome -> nome.matches(
                ".*(payload|message|arquivo|file|token|cookie|secret|body|content).*"));
    }

    @Test
    void updateDeleteELeituraDeOutroTenantFalhamFechado() {
        UUID eventA = registrarCritico(AuditTrail.Acao.CREDENTIAL_CHANGED,
                AuditTrail.TipoAlvo.CHANNEL_CONNECTION,
                AuditTrail.Motivo.CHANNEL_CREDENTIAL_CHANGED);
        UUID tenantB = scenario.criarTenant("audit-b", "Audit B");
        UUID userB = scenario.criarUsuario(tenantB, "bia", "12345");
        UUID eventB = registrarCritico(tenantB, userB, AuditTrail.Acao.ROLE_CHANGED,
                AuditTrail.TipoAlvo.ROLE, AuditTrail.Motivo.ROLE_ASSIGNMENT_CHANGED);

        assertThatThrownBy(() -> TenantContext.executarComo(tenantA,
                () -> jdbc.update("UPDATE audit_event SET reason_code = 'PAGE_VIEW' WHERE id = ?",
                        eventA))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> TenantContext.executarComo(tenantA,
                () -> jdbc.update("DELETE FROM audit_event WHERE id = ?", eventA)))
                .isInstanceOf(DataAccessException.class);

        List<UUID> vistosPorA = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForList("SELECT id FROM audit_event", UUID.class));
        assertThat(vistosPorA).contains(eventA).doesNotContain(eventB);
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'audit_event', 'UPDATE')",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'audit_event', 'DELETE')",
                Boolean.class)).isFalse();
    }

    @Test
    void politicaDeFalhaEDepaginacaoSaoExplicitas() {
        // `executarComo` recebe Supplier, e `registrar` devolve void: o lambda
        // precisa de corpo com retorno explícito para o tipo ser inferível.
        assertThatThrownBy(() -> TenantContext.executarComo(tenantA, () -> {
            audit.registrar(evento(tenantA,
                    AuditTrail.Acao.CREDENTIAL_CHANGED,
                    AuditTrail.TipoAlvo.HTTP_CONNECTOR,
                    AuditTrail.Motivo.CONNECTOR_CREDENTIAL_CHANGED,
                    userA, UUID.randomUUID()));
            return null;
        }))
                .isInstanceOf(AuditUnavailableException.class);

        TenantContext.executarComo(tenantA, () -> {
            audit.registrar(new AuditTrail.Evento(
                    AuditTrail.Acao.AUTHORIZATION_DENIED,
                    AuditTrail.Ator.humano(userA),
                    AuditTrail.Escopo.tenant(tenantA),
                    new AuditTrail.Alvo(AuditTrail.TipoAlvo.AUTHORIZATION, null),
                    AuditTrail.Resultado.DENIED,
                    AuditTrail.Motivo.PERMISSION_MISSING));
            return null;
        });
        registrarCritico(AuditTrail.Acao.EXPORT_COMPLETED,
                AuditTrail.TipoAlvo.EXPORT, AuditTrail.Motivo.EXPORT_COMPLETED);
        registrarCritico(AuditTrail.Acao.ROLE_CHANGED,
                AuditTrail.TipoAlvo.ROLE, AuditTrail.Motivo.ROLE_ASSIGNMENT_CHANGED);

        AuditQueryService.Pagina primeira = TenantContext.executarComo(tenantA,
                () -> queries.listar(userA, null, null, null, 2));
        AuditQueryService.Pagina segunda = TenantContext.executarComo(tenantA,
                () -> queries.listar(userA, null, null, primeira.proximoCursor(), 2));

        assertThat(primeira.eventos()).hasSize(2);
        assertThat(primeira.proximoCursor()).isNotBlank();
        assertThat(segunda.eventos()).isNotEmpty();
        assertThat(primeira.eventos()).extracting(AuditQueryService.EventoResponse::id)
                .doesNotContainAnyElementsOf(
                        segunda.eventos().stream().map(AuditQueryService.EventoResponse::id).toList());
    }

    private UUID registrarCritico(AuditTrail.Acao acao, AuditTrail.TipoAlvo tipo,
                                  AuditTrail.Motivo motivo) {
        return registrarCritico(tenantA, userA, acao, tipo, motivo);
    }

    private UUID registrarCritico(UUID tenantId, UUID userId, AuditTrail.Acao acao,
                                  AuditTrail.TipoAlvo tipo, AuditTrail.Motivo motivo) {
        UUID alvoId = UUID.randomUUID();
        TenantContext.executarComo(tenantId, () -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> audit.registrar(evento(
                            tenantId, acao, tipo, motivo, userId, alvoId)));
            return null;
        });
        return TenantContext.executarComo(tenantId, () -> jdbc.queryForObject("""
                SELECT id FROM audit_event
                 WHERE target_id = ? ORDER BY occurred_at DESC LIMIT 1
                """, UUID.class, alvoId));
    }

    private AuditTrail.Evento evento(UUID tenantId, AuditTrail.Acao acao,
                                     AuditTrail.TipoAlvo tipo,
                                     AuditTrail.Motivo motivo, UUID userId, UUID alvoId) {
        return new AuditTrail.Evento(
                acao, AuditTrail.Ator.humano(userId), AuditTrail.Escopo.tenant(tenantId),
                new AuditTrail.Alvo(tipo, alvoId), AuditTrail.Resultado.SUCCEEDED, motivo);
    }
}
