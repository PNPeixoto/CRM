package br.com.pnp.crm.integration.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.automation.api.AutomationAction;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@TesteDeIntegracao
@Import(ConectorHttpSeguroIntegracaoTest.TransporteControlado.class)
class ConectorHttpSeguroIntegracaoTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired HttpConnectorService conectores;
    @Autowired HttpConnectorSecretService segredos;
    @Autowired HttpConnectorAutomationAction acao;
    @Autowired AtomicInteger chamadasExternas;
    @Autowired AtomicReference<TransportHttpSeguro.Requisicao> requisicaoCapturada;

    private UUID tenantId;
    private UUID usuarioId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        chamadasExternas.set(0);
        requisicaoCapturada.set(null);
        tenantId = cenario.criarTenant("http-seguro", "HTTP Seguro");
        usuarioId = cenario.criarUsuario(tenantId, "owner-http", "Senha forte 123!");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void cifraSegredoSanitizaDiagnosticoERepeticaoNaoDuplicaEnvio() {
        String segredoClaro = "segredo-super-secreto-xyz";
        UUID conectorId = TenantContext.executarComo(tenantId, () -> {
            var criado = conectores.criar(new HttpConnectorModel.ConnectorRequest(
                    "erp_teste", "ERP de teste", "POST", "https://api.example.com",
                    "/v1/tenants/{{tenantId}}/executions/{{executionId}}",
                    "{\"executionId\":\"{{executionId}}\",\"step\":\"{{stepKey}}\"}",
                    "Idempotency-Key", 1000, 4096, 10), usuarioId);
            segredos.configurar(criado.id(), new HttpConnectorModel.SecretRequest(
                    "BEARER", null, segredoClaro), usuarioId);
            conectores.mudarStatus(criado.id(), "ACTIVE", usuarioId);
            return criado.id();
        });
        UUID execucaoId = criarExecucao();
        var contexto = new AutomationAction.Contexto(
                tenantId, execucaoId, "enviar_erp", "MESSAGE_RECEIVED",
                UUID.randomUUID(), Map.of("connectorId", conectorId.toString()));

        AutomationAction.Resultado primeira = TenantContext.executarComo(
                tenantId, () -> acao.executar(contexto));
        AutomationAction.Resultado repetida = TenantContext.executarComo(
                tenantId, () -> acao.executar(contexto));

        assertThat(primeira).isEqualTo(AutomationAction.Resultado.SUCESSO);
        assertThat(repetida).isEqualTo(AutomationAction.Resultado.SUCESSO);
        assertThat(chamadasExternas).hasValue(1);
        assertThat(requisicaoCapturada.get().valorAutenticacao())
                .isEqualTo("Bearer " + segredoClaro);
        assertThat(requisicaoCapturada.get().corpo()).doesNotContain(segredoClaro);
        assertThat(requisicaoCapturada.get().chaveIdempotencia()).hasSize(64);

        TenantContext.executarComo(tenantId, () -> {
            String cifrado = jdbc.queryForObject("""
                    SELECT secret_ciphertext FROM http_connector_secret
                     WHERE connector_id = ?
                    """, String.class, conectorId);
            assertThat(cifrado).isNotBlank().doesNotContain(segredoClaro);
            assertThat(conectores.buscar(conectorId).toString()).doesNotContain(segredoClaro);
            assertThat(conectores.preview(conectorId).toString()).doesNotContain(segredoClaro);

            Map<String, Object> tentativa = jdbc.queryForMap("""
                    SELECT status, http_status, failure_code, response_bytes,
                           duration_ms, idempotency_key_hash, target_hash
                      FROM http_connector_attempt
                     WHERE automation_execution_id = ? AND step_key = 'enviar_erp'
                    """, execucaoId);
            assertThat(tentativa.get("status")).isEqualTo("SUCCEEDED");
            assertThat(tentativa.toString()).doesNotContain(segredoClaro)
                    .doesNotContain("api.example.com");
            return null;
        });
    }

    private UUID criarExecucao() {
        return TenantContext.executarComo(tenantId, () -> {
            UUID definicaoId = UuidV7.gerar();
            UUID versaoId = UuidV7.gerar();
            UUID execucaoId = UuidV7.gerar();
            jdbc.update("""
                    INSERT INTO automation_definition
                        (id, tenant_id, code, name, status, current_version,
                         concurrency_limit, created_by, updated_by)
                    VALUES (?, ?, 'http_teste', 'HTTP teste', 'DRAFT', 1, 1, ?, ?)
                    """, definicaoId, tenantId, usuarioId, usuarioId);
            jdbc.update("""
                    INSERT INTO automation_definition_version
                        (id, tenant_id, definition_id, version_number, trigger_type,
                         specification, specification_sha256, max_duration_seconds,
                         step_count, branch_count, created_by)
                    VALUES (?, ?, ?, 1, 'MESSAGE_RECEIVED', '{}'::jsonb, ?, 30, 1, 0, ?)
                    """, versaoId, tenantId, definicaoId, "0".repeat(64), usuarioId);
            jdbc.update("""
                    UPDATE automation_definition
                       SET status = 'ACTIVE', active_version_id = ?
                     WHERE id = ?
                    """, versaoId, definicaoId);
            jdbc.update("""
                    INSERT INTO automation_execution
                        (id, tenant_id, definition_id, definition_version_id,
                         trigger_key, trigger_type, status, deadline_at, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'http:teste:1', 'MESSAGE_RECEIVED',
                            'RUNNING', now() + interval '30 seconds', ?, ?)
                    """, execucaoId, tenantId, definicaoId, versaoId, usuarioId, usuarioId);
            return execucaoId;
        });
    }

    @TestConfiguration
    static class TransporteControlado {

        @Bean
        AtomicInteger chamadasExternas() {
            return new AtomicInteger();
        }

        @Bean
        AtomicReference<TransportHttpSeguro.Requisicao> requisicaoCapturada() {
            return new AtomicReference<>();
        }

        @Bean
        @Primary
        TransportHttpSeguro transporteHttpDeTeste(
                AtomicInteger chamadasExternas,
                AtomicReference<TransportHttpSeguro.Requisicao> requisicaoCapturada) {
            return requisicao -> {
                requisicaoCapturada.set(requisicao);
                chamadasExternas.incrementAndGet();
                return new TransportHttpSeguro.Resposta(
                        TransportHttpSeguro.Tipo.SUCESSO, 201, 2, 5, null);
            };
        }
    }
}
