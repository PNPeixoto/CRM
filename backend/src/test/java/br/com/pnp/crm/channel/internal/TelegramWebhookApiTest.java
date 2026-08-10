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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class TelegramWebhookApiTest {

    private static final String SEGREDO = "segredo_webhook-valido";
    private static final String HEADER = "X-Telegram-Bot-Api-Secret-Token";

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired CredenciaisDeCanal credenciais;

    private UUID tenantId;
    private UUID conexaoId;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenantId = cenario.criarTenant("telegram-contract", "Telegram Contract");
        conexaoId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("INSERT INTO channel_connection (id, tenant_id, kind, name) VALUES (?, ?, 'TELEGRAM', 'Bot')",
                    conexaoId, tenantId);
            credenciais.guardar(tenantId, conexaoId,
                    TipoCredencial.TELEGRAM_WEBHOOK_SECRET, SEGREDO);
            return null;
        });
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void assinaturaInvalidaNaoPersisteEReplayValidoConverge() throws Exception {
        String payload = "{\"update_id\":7001,\"message\":{\"message_id\":9,\"date\":1785000000,\"chat\":{\"id\":123},\"text\":\"oi\"}}";
        String url = "/api/webhooks/telegram/" + conexaoId;

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(HEADER, "invalido").content(payload))
                .andExpect(status().isForbidden());
        assertThat(totalEventos()).isZero();

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(HEADER, SEGREDO).content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .header(HEADER, SEGREDO).content(payload))
                .andExpect(status().isOk());

        assertThat(totalEventos()).isOne();
    }

    private long totalEventos() {
        return TenantContext.executarComo(tenantId, () -> jdbc.queryForObject(
                "SELECT count(*) FROM inbound_event WHERE channel_connection_id = ?",
                Long.class, conexaoId));
    }
}
