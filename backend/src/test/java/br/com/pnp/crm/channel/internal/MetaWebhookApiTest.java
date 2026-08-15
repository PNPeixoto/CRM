package br.com.pnp.crm.channel.internal;

import br.com.pnp.crm.CenarioMultiTenant;
import br.com.pnp.crm.TesteDeIntegracao;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class MetaWebhookApiTest {

    private static final String APP_SECRET = "meta-app-secret-de-teste";
    private static final String VERIFY_TOKEN = "meta-verify-token-de-teste";
    private static final String CONTA = "178900000000001";

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired CredenciaisDeCanal credenciais;

    private UUID tenantId;
    private UUID conexaoId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenantId = cenario.criarTenant("instagram-contract", "Instagram Contract");
        conexaoId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("""
                    INSERT INTO channel_connection
                        (id, tenant_id, kind, name, external_account_id)
                    VALUES (?, ?, 'INSTAGRAM', 'Instagram', ?)
                    """, conexaoId, tenantId, CONTA);
            credenciais.guardar(tenantId, conexaoId,
                    TipoCredencial.META_APP_SECRET, APP_SECRET);
            credenciais.guardar(tenantId, conexaoId,
                    TipoCredencial.META_WEBHOOK_VERIFY_TOKEN, VERIFY_TOKEN);
            return null;
        });
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void desafioExigeTokenDaConexaoEDevolveSomenteChallenge() throws Exception {
        String url = "/api/webhooks/meta/" + conexaoId;
        mockMvc.perform(get(url)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "invalido")
                        .param("hub.challenge", "123456"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(url)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "123456"))
                .andExpect(status().isOk())
                .andExpect(content().string("123456"));
    }

    @Test
    void hmacContaEReplaySaoValidadosAntesDaPersistencia() throws Exception {
        String payload = payload(CONTA, "MID-1");
        String url = "/api/webhooks/meta/" + conexaoId;

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(MetaWebhookController.HEADER_ASSINATURA, "sha256=" + "0".repeat(64))
                        .content(payload))
                .andExpect(status().isForbidden());
        assertThat(totalEventos()).isZero();

        String contaErrada = payload("178900000000999", "MID-2");
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(MetaWebhookController.HEADER_ASSINATURA, assinar(contaErrada))
                        .content(contaErrada))
                .andExpect(status().isForbidden());
        assertThat(totalEventos()).isZero();

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(MetaWebhookController.HEADER_ASSINATURA, assinar(payload))
                        .content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(MetaWebhookController.HEADER_ASSINATURA, assinar(payload))
                        .content(payload))
                .andExpect(status().isOk());

        assertThat(totalEventos()).isOne();
    }

    private String payload(String conta, String mid) {
        return """
                {"object":"instagram","entry":[{"id":"%s","time":1785000000,
                "messaging":[{"sender":{"id":"IGSID-1"},"recipient":{"id":"%s"},
                "timestamp":1785000000123,"message":{"mid":"%s","text":"oi"}}]}]}
                """.formatted(conta, conta, mid);
    }

    private String assinar(String payload) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(
                hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private long totalEventos() {
        return TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM inbound_event WHERE channel_connection_id = ?",
                Long.class, conexaoId));
    }
}
