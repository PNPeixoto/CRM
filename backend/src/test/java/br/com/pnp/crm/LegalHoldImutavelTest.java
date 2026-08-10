package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O legal hold e imune a quem executa o expurgo.
 *
 * <p><b>Por que este teste existe.</b> A V23 deixou o papel de runtime herdar
 * `DELETE` sobre `legal_hold` do privilegio padrao. O mesmo papel que apaga
 * dado podia remover a trava que deveria impedi-lo — e uma trava que o proprio
 * guarda remove nao e trava. Registrado como `LGPD-005`.
 *
 * <p>Os testes cobrem as duas formas de neutralizar um hold: apagar o registro,
 * e reescrever o escopo para que ele deixe de cobrir alguma coisa. A segunda e
 * pior, porque o hold continua existindo e parecendo intacto.
 */
@TesteDeIntegracao
class LegalHoldImutavelTest {

    private static final Instant AGORA = Instant.parse("2026-08-10T12:00:00Z");

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;

    private UUID tenant;
    private UUID usuario;
    private UUID hold;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha");
        usuario = cenario.criarUsuario(tenant, "ana", "12345");
        hold = declararHold();
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void oRuntimeNaoApagaOHold() {
        assertThatThrownBy(() -> TenantContext.executarComo(tenant, () ->
                jdbc.update("DELETE FROM legal_hold WHERE id = ?", hold)))
                .as("apagar o hold levaria junto a prova de por que o dado foi retido")
                .isNotNull();

        assertThat(existe(hold)).isTrue();
    }

    @Test
    void oEscopoDoHoldEhImutavel() {
        // Trocar o alvo é a forma silenciosa de neutralizar: o hold continua
        // na tabela, aparentemente intacto, e deixa de cobrir o que cobria.
        assertThatThrownBy(() -> TenantContext.executarComo(tenant, () ->
                jdbc.update("UPDATE legal_hold SET target_type = 'USAGE_EVENT' WHERE id = ?",
                        hold)))
                .isNotNull();

        assertThat(campo("target_type")).isEqualTo("CONTACT");
    }

    @Test
    void aOrigemEOMotivoDoHoldSaoImutaveis() {
        assertThatThrownBy(() -> TenantContext.executarComo(tenant, () ->
                jdbc.update("UPDATE legal_hold SET reason = 'outro motivo' WHERE id = ?", hold)))
                .isNotNull();

        assertThatThrownBy(() -> TenantContext.executarComo(tenant, () ->
                jdbc.update("UPDATE legal_hold SET declared_by = ? WHERE id = ?",
                        UuidV7.gerar(), hold)))
                .isNotNull();

        assertThat(campo("reason")).isEqualTo("processo judicial em curso");
    }

    @Test
    void encerrarOHoldContinuaPossivelEHOCaminhoPretendido() {
        // Revogar precisa funcionar: um hold que ninguem consegue encerrar
        // congelaria o dado para sempre, o que tambem viola o titular.
        TenantContext.executarComo(tenant, () -> jdbc.update(
                "UPDATE legal_hold SET deleted_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(AGORA), Timestamp.from(AGORA), hold));

        assertThat(campo("deleted_at")).isNotNull();
        assertThat(existe(hold)).as("o registro permanece como prova").isTrue();
    }

    @Test
    void encerrarPorValidadeTambemFunciona() {
        TenantContext.executarComo(tenant, () -> jdbc.update(
                "UPDATE legal_hold SET valid_until = ? WHERE id = ?",
                Timestamp.from(AGORA), hold));

        assertThat(campo("valid_until")).isNotNull();
    }

    @Test
    void holdEncerradoDeixaDeBloquearOExpurgo() {
        // Fecha o ciclo: a imutabilidade nao pode ter transformado o hold em
        // bloqueio permanente. Encerrado, ele para de proteger.
        assertThat(protegeContato()).isTrue();

        TenantContext.executarComo(tenant, () -> jdbc.update(
                "UPDATE legal_hold SET deleted_at = ? WHERE id = ?",
                Timestamp.from(AGORA.minus(1, ChronoUnit.HOURS)), hold));

        assertThat(protegeContato()).isFalse();
    }

    // -----------------------------------------------------------------------

    private boolean protegeContato() {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT ha_legal_hold('CONTACT', NULL, ?)", Boolean.class,
                Timestamp.from(AGORA)));
    }

    private UUID declararHold() {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO legal_hold
                    (id, tenant_id, target_type, target_id, reason, declared_by, valid_from)
                VALUES (?, ?, 'CONTACT', NULL, 'processo judicial em curso', ?, ?)
                """, id, tenant, usuario,
                Timestamp.from(AGORA.minus(10, ChronoUnit.DAYS))));
        return id;
    }

    private boolean existe(UUID id) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM legal_hold WHERE id = ?", Long.class, id)) == 1L;
    }

    private String campo(String coluna) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT " + coluna + "::text FROM legal_hold WHERE id = ?",
                String.class, hold));
    }
}
