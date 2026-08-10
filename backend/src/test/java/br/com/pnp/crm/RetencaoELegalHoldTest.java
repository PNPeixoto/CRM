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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retencao e legal hold, com relogio controlado.
 *
 * <p><b>Por que o relogio e parametro.</b> Nenhuma funcao de expurgo chama
 * {@code now()}: o instante de corte entra como argumento. Sem isso, provar
 * retencao exigiria esperar dias ou mexer no relogio do banco — e o aceite do
 * Prompt 18 pede exatamente "relogio controlado prova retencao e expurgo
 * idempotente".
 *
 * <p>O efeito colateral e melhor que o objetivo: um corte explicito impede que
 * alguem ligue o expurgo sem escolher conscientemente a data.
 *
 * <p>O caso escolhido e a <b>anonimizacao de contato</b>, que e o que o aceite
 * chama de "exclusao impossivel vira anonimizacao": contato e referenciado por
 * oportunidade, tarefa e conversa, entao apagar quebraria historico comercial
 * que nao pertence ao titular do dado pessoal.
 */
@TesteDeIntegracao
class RetencaoELegalHoldTest {

    /** Instante fixo. Teste que depende de relogio real falha de madrugada. */
    private static final Instant AGORA = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant CORTE = AGORA.minus(30, ChronoUnit.DAYS);
    private static final Instant ANTIGO = CORTE.minus(1, ChronoUnit.DAYS);

    @Autowired CenarioMultiTenant cenario;
    @Autowired JdbcTemplate jdbc;

    private UUID tenant;
    private UUID usuario;

    @BeforeEach
    void preparar() {
        cenario.limpar();
        tenant = cenario.criarTenant("alpha", "Alpha");
        usuario = cenario.criarUsuario(tenant, "ana", "12345");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    void anonimizacaoRespeitaOCorteEHIdempotente() {
        UUID recente = contatoExcluidoEm(AGORA.minus(1, ChronoUnit.DAYS));
        UUID antigo = contatoExcluidoEm(ANTIGO);

        int primeira = anonimizar(CORTE);
        int segunda = anonimizar(CORTE);

        assertThat(primeira).as("trata apenas o que passou do corte").isOne();
        // Idempotencia: `anonymized_at` ja preenchido sai do alvo. Sem isso, um
        // worker que rode duas vezes por sobreposicao de lease reescreveria a
        // data e mentiria sobre quando o dado foi removido.
        assertThat(segunda).as("segunda passada nao encontra nada").isZero();

        assertThat(anonimizado(antigo)).isTrue();
        assertThat(anonimizado(recente)).as("dentro do prazo permanece intacto").isFalse();
    }

    @Test
    void anonimizacaoRemoveODadoPessoalEPreservaARelacao() {
        UUID alvo = contatoExcluidoEm(ANTIGO);

        anonimizar(CORTE);

        Map<String, Object> linha = TenantContext.executarComo(tenant, () -> jdbc.queryForMap(
                "SELECT name, email, phone, company_name, notes FROM contact WHERE id = ?", alvo));

        assertThat(linha.get("name")).isEqualTo("Titular anonimizado");
        assertThat(linha.get("email")).isNull();
        assertThat(linha.get("phone")).isNull();
        assertThat(linha.get("company_name")).isNull();
        assertThat(linha.get("notes")).isNull();

        // A linha continua existindo: oportunidade e tarefa apontam para ela, e
        // apagar quebraria historico que nao e do titular.
        assertThat(existe(alvo)).isTrue();
    }

    @Test
    void legalHoldSobreORegistroImpedeATransformacaoDaquelaLinha() {
        UUID protegido = contatoExcluidoEm(ANTIGO);
        UUID desprotegido = contatoExcluidoEm(ANTIGO);
        declararHold("CONTACT", protegido, null);

        assertThat(anonimizar(CORTE)).isOne();

        assertThat(anonimizado(protegido)).as("hold preserva").isFalse();
        assertThat(anonimizado(desprotegido)).as("fora do hold, anonimiza").isTrue();
    }

    @Test
    void holdDeCategoriaCobreTodosOsRegistrosDela() {
        contatoExcluidoEm(ANTIGO);
        contatoExcluidoEm(ANTIGO);
        // `target_id` nulo significa a categoria inteira.
        declararHold("CONTACT", null, null);

        assertThat(anonimizar(CORTE)).isZero();
    }

    @Test
    void holdDeTenantCobreQualquerCategoria() {
        contatoExcluidoEm(ANTIGO);
        declararHold("TENANT", null, null);

        // Hold de tenant existe para quando ainda nao se sabe o que preservar.
        // Precisa alcancar categoria que ninguem listou.
        assertThat(anonimizar(CORTE)).isZero();
    }

    @Test
    void holdVencidoDeixaDeProteger() {
        UUID alvo = contatoExcluidoEm(ANTIGO);
        declararHold("CONTACT", alvo, CORTE.minus(1, ChronoUnit.HOURS));

        assertThat(anonimizar(CORTE)).isOne();
        assertThat(anonimizado(alvo)).isTrue();
    }

    @Test
    void holdDeOutraCategoriaNaoProtegeAlemDoEscopoAutorizado() {
        UUID alvo = contatoExcluidoEm(ANTIGO);
        // O aceite exige que o hold preserve **somente** o escopo autorizado.
        // Um hold amplo demais e tao defeituoso quanto um que nao protege.
        declararHold("USAGE_EVENT", null, null);

        assertThat(anonimizar(CORTE)).isOne();
        assertThat(anonimizado(alvo)).isTrue();
    }

    @Test
    void contatoAtivoNuncaEhAnonimizadoPorRetencao() {
        UUID ativo = contato(null);

        assertThat(anonimizar(CORTE)).isZero();
        assertThat(anonimizado(ativo)).isFalse();
    }

    @Test
    void expurgoDeUmTenantNaoAlcancaOVizinho() {
        UUID vizinho = cenario.criarTenant("beta", "Beta");
        UUID usuarioVizinho = cenario.criarUsuario(vizinho, "bia", "12345");
        UUID doVizinho = UuidV7.gerar();
        TenantContext.executarComo(vizinho, () -> {
            inserirContato(vizinho, doVizinho, usuarioVizinho, ANTIGO);
            return null;
        });
        contatoExcluidoEm(ANTIGO);

        assertThat(anonimizar(CORTE)).as("so o tenant corrente").isOne();

        Object nome = TenantContext.executarComo(vizinho, () -> jdbc.queryForObject(
                "SELECT name FROM contact WHERE id = ?", String.class, doVizinho));
        assertThat(nome).as("vizinho intacto").isNotEqualTo("Titular anonimizado");
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private int anonimizar(Instant corte) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT anonimizar_contatos(?, ?)", Integer.class, Timestamp.from(corte), 100));
    }

    private boolean anonimizado(UUID id) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT anonymized_at IS NOT NULL FROM contact WHERE id = ?",
                Boolean.class, id));
    }

    private boolean existe(UUID id) {
        return TenantContext.executarComo(tenant, () -> jdbc.queryForObject(
                "SELECT count(*) FROM contact WHERE id = ?", Long.class, id)) == 1L;
    }

    private UUID contatoExcluidoEm(Instant quando) {
        return contato(quando);
    }

    private UUID contato(Instant excluidoEm) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenant, () -> {
            inserirContato(tenant, id, usuario, excluidoEm);
            return null;
        });
        return id;
    }

    private void inserirContato(UUID tenantId, UUID id, UUID dono, Instant excluidoEm) {
        jdbc.update("""
                INSERT INTO contact
                    (id, tenant_id, name, email, phone, company_name, notes,
                     owner_user_id, deleted_at)
                VALUES (?, ?, 'Fulano de Teste', 'fulano@exemplo.test', '+550000000000',
                        'Empresa Teste', 'anotacao', ?, ?)
                """, id, tenantId, dono,
                excluidoEm == null ? null : Timestamp.from(excluidoEm));
    }

    private void declararHold(String tipo, UUID alvo, Instant ate) {
        TenantContext.executarComo(tenant, () -> jdbc.update("""
                INSERT INTO legal_hold
                    (id, tenant_id, target_type, target_id, reason, declared_by,
                     valid_from, valid_until)
                VALUES (?, ?, ?, ?, 'processo de teste', ?, ?, ?)
                """, UuidV7.gerar(), tenant, tipo, alvo, usuario,
                Timestamp.from(ANTIGO.minus(1, ChronoUnit.DAYS)),
                ate == null ? null : Timestamp.from(ate)));
    }
}
