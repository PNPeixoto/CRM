package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mecânica da fila de saída, exercitada contra o Postgres real.
 *
 * <p>Testar isto com mock não provaria nada: o que precisa funcionar é a
 * função {@code reservar_mensagens_para_envio} com {@code FOR UPDATE SKIP
 * LOCKED}, o backoff calculado em SQL e o teto de tentativas — três
 * comportamentos que existem inteiramente dentro do banco.
 */
@TesteDeIntegracao
class FilaDeSaidaTest {

    private static final int LIMITE = 10;
    private static final int MAX_TENTATIVAS = 3;
    private static final int ESPERA_SEGUNDOS = 30;

    @Autowired
    private CenarioMultiTenant cenario;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID conexaoA;
    private UUID conversaA;

    @BeforeEach
    void prepararCenario() {
        cenario.limpar();
        tenantA = cenario.criarTenant("alpha", "Alpha");
        conexaoA = criarConexao(tenantA, "Chat do site");
        conversaA = criarConversa(tenantA, conexaoA, "visitante-1");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    @DisplayName("mensagem pendente é reservada e a tentativa é contada na reserva")
    void reservaContabilizaTentativa() {
        UUID mensagem = criarMensagemPendente(tenantA, conversaA, conexaoA);

        List<Map<String, Object>> reservadas = reservar();

        assertThat(reservadas).hasSize(1);
        assertThat(reservadas.getFirst().get("message_id")).isEqualTo(mensagem);

        // Contabilizar na reserva, e não na conclusão, é o que impede que uma
        // mensagem capaz de derrubar o worker seja reprocessada para sempre.
        assertThat(tentativasDe(mensagem)).isOne();
    }

    @Test
    @DisplayName("mensagem reservada não é devolvida de novo antes do backoff")
    void backoffImpedeReservaImediata() {
        criarMensagemPendente(tenantA, conversaA, conexaoA);

        assertThat(reservar()).hasSize(1);
        // Segunda chamada imediata: o next_attempt_at já foi empurrado para o
        // futuro. Sem isso o worker giraria em laço apertado sobre a mesma
        // mensagem, gastando as tentativas em segundos.
        assertThat(reservar()).isEmpty();
    }

    @Test
    @DisplayName("atingido o teto de tentativas, a mensagem deixa de ser candidata")
    void tetoDeTentativasEncerraAFila() {
        UUID mensagem = criarMensagemPendente(tenantA, conversaA, conexaoA);

        // Simula tentativas já gastas e libera o backoff.
        TenantContext.executarComo(tenantA, () -> jdbc.update(
                "UPDATE message SET attempt_count = ?, next_attempt_at = now() - interval '1 hour' "
                        + "WHERE id = ?", MAX_TENTATIVAS, mensagem));

        assertThat(reservar())
                .as("a fila morta é o teto na consulta, não um estado novo")
                .isEmpty();

        // A mensagem continua existindo e visível na conversa — o atendente
        // precisa ver que ela não saiu.
        assertThat(tentativasDe(mensagem)).isEqualTo(MAX_TENTATIVAS);
    }

    @Test
    @DisplayName("mensagem recebida nunca entra na fila de saída")
    void apenasSaidaEhReservada() {
        TenantContext.executarComo(tenantA, () -> jdbc.update("""
                INSERT INTO message (id, tenant_id, conversation_id, channel_connection_id,
                                     external_id, direction, content_type, text_content, status)
                VALUES (?, ?, ?, ?, 'ext-1', 'INBOUND', 'TEXT', 'oi', 'RECEIVED')
                """, UuidV7.gerar(), tenantA, conversaA, conexaoA));

        assertThat(reservar()).isEmpty();
    }

    @Test
    @DisplayName("a reserva enxerga mensagens de todos os tenants, sem contexto definido")
    void reservaAtravessaOTenant() {
        UUID tenantB = cenario.criarTenant("beta", "Beta");
        UUID conexaoB = criarConexao(tenantB, "Chat do site");
        UUID conversaB = criarConversa(tenantB, conexaoB, "visitante-9");

        criarMensagemPendente(tenantA, conversaA, conexaoA);
        criarMensagemPendente(tenantB, conversaB, conexaoB);

        // Sem TenantContext: é assim que o worker roda. A função é
        // SECURITY DEFINER justamente porque, sob RLS, esta consulta
        // devolveria zero linhas — e nenhuma mensagem sairia do sistema.
        List<Map<String, Object>> reservadas = reservar();

        assertThat(reservadas).hasSize(2);
        assertThat(reservadas.stream().map(linha -> linha.get("message_tenant_id")))
                .containsExactlyInAnyOrder(tenantA, tenantB);
    }

    private List<Map<String, Object>> reservar() {
        return jdbc.queryForList(
                "SELECT message_id, message_tenant_id FROM reservar_mensagens_para_envio(?, ?, ?)",
                LIMITE, MAX_TENTATIVAS, ESPERA_SEGUNDOS);
    }

    private int tentativasDe(UUID messageId) {
        Integer tentativas = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT attempt_count FROM message WHERE id = ?", Integer.class, messageId));
        return tentativas == null ? 0 : tentativas;
    }

    private UUID criarConexao(UUID tenantId, String nome) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update(
                "INSERT INTO channel_connection (id, tenant_id, kind, name) VALUES (?, ?, 'LIVE_CHAT', ?)",
                id, tenantId, nome));
        return id;
    }

    private UUID criarConversa(UUID tenantId, UUID conexaoId, String contatoExterno) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO conversation (id, tenant_id, channel_connection_id, external_contact_id)
                VALUES (?, ?, ?, ?)
                """, id, tenantId, conexaoId, contatoExterno));
        return id;
    }

    private UUID criarMensagemPendente(UUID tenantId, UUID conversaId, UUID conexaoId) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO message (id, tenant_id, conversation_id, channel_connection_id,
                                     direction, content_type, text_content, status)
                VALUES (?, ?, ?, ?, 'OUTBOUND', 'TEXT', 'resposta do atendente', 'PENDING')
                """, id, tenantId, conversaId, conexaoId));
        return id;
    }
}
