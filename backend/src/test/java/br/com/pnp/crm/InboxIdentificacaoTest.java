package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class InboxIdentificacaoTest {

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    private UUID tenant;
    private UUID atendente;
    private UUID conversa;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("inbox-identificacao", "Inbox Identificação");
        atendente = cenario.criarUsuario(tenant, "ana", "12345");
        cenario.concederTudoNoTenant(
                tenant, atendente, "conversations.read", "conversations.write");

        UUID canal = UuidV7.gerar();
        conversa = UuidV7.gerar();
        UUID mensagem = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> {
            jdbc.update("""
                    INSERT INTO channel_connection
                        (id, tenant_id, kind, name, external_account_id, active,
                         created_by, updated_by)
                    VALUES (?, ?, 'WHATSAPP_EVOLUTION', 'WhatsApp Vendas',
                            '5511999990000', true, ?, ?)
                    """, canal, tenant, atendente, atendente);
            jdbc.update("""
                    INSERT INTO conversation
                        (id, tenant_id, channel_connection_id, external_contact_id,
                         contact_display_name, status, assigned_user_id, last_message_at,
                         created_by, updated_by)
                    VALUES (?, ?, ?, '5511988880000@s.whatsapp.net', 'Maria Cliente',
                            'OPEN', ?, now(), ?, ?)
                    """, conversa, tenant, canal, atendente, atendente, atendente);
            jdbc.update("""
                    INSERT INTO message
                        (id, tenant_id, conversation_id, channel_connection_id, direction,
                         content_type, text_content, status, author_user_id,
                         created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'OUTBOUND', 'TEXT', 'Olá, Maria!', 'SENT', ?, ?, ?)
                    """, mensagem, tenant, conversa, canal, atendente, atendente, atendente);
        });
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void identificaCanalContaContatoAtendenteEAutor() throws Exception {
        mockMvc.perform(get("/api/conversas").with(jwt().jwt(builder -> builder
                        .subject(atendente.toString()).claim("tid", tenant.toString())
                        .claim("login", "ana"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].canalTipo").value("WHATSAPP_EVOLUTION"))
                .andExpect(jsonPath("$.itens[0].canalNome").value("WhatsApp Vendas"))
                .andExpect(jsonPath("$.itens[0].canalIdentificador").value("5511999990000"))
                .andExpect(jsonPath("$.itens[0].contatoIdentificador")
                        .value("5511988880000@s.whatsapp.net"))
                .andExpect(jsonPath("$.itens[0].atendenteNome").value("Usuário ana"));

        mockMvc.perform(get("/api/conversas/" + conversa + "/mensagens")
                        .with(jwt().jwt(builder -> builder.subject(atendente.toString())
                                .claim("tid", tenant.toString()).claim("login", "ana"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].autorNome").value("Usuário ana"));
    }
}
