package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
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
 * O teste que o {@code 01-padroes-tecnicos.md} coloca em primeiro lugar:
 * usuário do tenant A não lê nem edita dado do tenant B.
 *
 * <p>Roda contra o Postgres real de propósito. Um mock de repositório
 * confirmaria apenas que a cláusula {@code WHERE} foi escrita — e é justamente
 * a query que <b>esquece</b> a cláusula que precisa ser barrada. Só o banco
 * com RLS ativo consegue provar isso.
 */
@TesteDeIntegracao
class IsolamentoEntreTenantsTest {

    @Autowired
    private CenarioMultiTenant cenario;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void prepararDoisTenants() {
        cenario.limpar();
        tenantA = cenario.criarTenant("alpha", "Alpha Franquias");
        tenantB = cenario.criarTenant("beta", "Beta Franquias");
        cenario.criarUsuario(tenantA, "peixoto", "12345");
        cenario.criarUsuario(tenantB, "peixoto", "12345");
    }

    @AfterEach
    void limpar() {
        cenario.limpar();
    }

    @Test
    @DisplayName("consulta sem filtro de tenant devolve apenas as linhas do tenant corrente")
    void naoLeDadoDeOutroTenant() {
        // SELECT deliberadamente sem WHERE tenant_id — simula o bug que o RLS
        // existe para conter.
        List<Map<String, Object>> vistosPorA = TenantContext.executarComo(tenantA,
                () -> jdbc.queryForList("SELECT id, tenant_id FROM app_user"));

        assertThat(vistosPorA).hasSize(1);
        assertThat(vistosPorA.getFirst().get("tenant_id")).isEqualTo(tenantA);
    }

    @Test
    @DisplayName("UPDATE sem filtro de tenant não alcança linha de outro tenant")
    void naoEditaDadoDeOutroTenant() {
        int linhasAfetadas = TenantContext.executarComo(tenantA,
                () -> jdbc.update("UPDATE app_user SET full_name = 'invadido'"));

        assertThat(linhasAfetadas).isOne();

        String nomeNoTenantB = TenantContext.executarComo(tenantB,
                () -> jdbc.queryForObject("SELECT full_name FROM app_user", String.class));

        assertThat(nomeNoTenantB).isNotEqualTo("invadido");
    }

    @Test
    @DisplayName("DELETE sem filtro de tenant não alcança linha de outro tenant")
    void naoApagaDadoDeOutroTenant() {
        TenantContext.executarComo(tenantA, () -> jdbc.update("DELETE FROM app_user"));

        Long restantesEmB = TenantContext.executarComo(tenantB,
                () -> jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class));

        assertThat(restantesEmB).isOne();
    }

    @Test
    @DisplayName("sem tenant no contexto, nenhuma linha é visível")
    void semTenantNaoVeNada() {
        // Falha fechada: a ausência de contexto não pode significar "vê tudo".
        // Este é o comportamento que torna seguro o esquecimento de um filtro.
        Long visiveis = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);

        assertThat(visiveis).isZero();
    }

    @Test
    @DisplayName("o mesmo login pode existir em tenants diferentes")
    void loginEhUnicoApenasDentroDoTenant() {
        Long totalA = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE login = 'peixoto'", Long.class));
        Long totalB = TenantContext.executarComo(tenantB, () -> jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE login = 'peixoto'", Long.class));

        assertThat(totalA).isOne();
        assertThat(totalB).isOne();
    }
}
