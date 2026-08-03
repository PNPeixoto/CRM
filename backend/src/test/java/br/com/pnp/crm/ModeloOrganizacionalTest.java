package br.com.pnp.crm;

import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.shared.api.AcessoNegadoException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TesteDeIntegracao
@AutoConfigureMockMvc
class ModeloOrganizacionalTest {

    @Autowired CenarioMultiTenant scenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationAccess access;
    @Autowired MockMvc mockMvc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        scenario.limpar();
        tenantA = scenario.criarTenant("alpha", "Alpha Serviços");
        tenantB = scenario.criarTenant("beta", "Beta Serviços");
        userA = scenario.criarUsuario(tenantA, "ana", "12345");
        userB = scenario.criarUsuario(tenantB, "bia", "12345");
    }

    @AfterEach
    void tearDown() {
        scenario.limpar();
    }

    @Test
    void umaIdentidadeAcessaDuasUnidadesETrocaSomenteParaContextoAutorizado() {
        UUID membership = membership(tenantA, userA,
                Instant.now().minus(1, ChronoUnit.DAYS), null);
        UUID role = role(tenantA, "AGENT", "Atendente");
        permission(tenantA, role, "conversations.read");
        UUID center = unit(tenantA, "centro", "Unidade Centro");
        UUID north = unit(tenantA, "norte", "Unidade Norte");
        scope(tenantA, membership, role, "UNIT", center);
        scope(tenantA, membership, role, "UNIT", north);

        OrganizationAccess.AccessSummary summary = TenantContext.executarComo(
                tenantA, () -> access.summarize(tenantA, userA));

        assertThat(summary.membershipId()).isEqualTo(membership);
        assertThat(summary.contexts()).extracting(OrganizationAccess.AuthorizedContext::id)
                .containsExactlyInAnyOrder(tenantA, center, north);
        assertThat(TenantContext.executarComo(
                tenantA, () -> access.selectUnit(tenantA, userA, center)).permissions())
                .containsExactly("conversations.read");

        UUID unauthorized = unit(tenantA, "sul", "Unidade Sul");
        assertThatThrownBy(() -> TenantContext.executarComo(
                tenantA, () -> access.selectUnit(tenantA, userA, unauthorized)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void membershipExpiradaNaoConcedeNemContextoTenant() {
        membership(tenantA, userA,
                Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));

        assertThatThrownBy(() -> TenantContext.executarComo(
                tenantA, () -> access.summarize(tenantA, userA)))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void papelTenantAlcancaUnidadesAtuaisSemDuplicarIdentidade() {
        UUID membership = membership(tenantA, userA,
                Instant.now().minus(1, ChronoUnit.DAYS), null);
        UUID role = role(tenantA, "OWNER", "Proprietário");
        permission(tenantA, role, "organization.manage");
        scope(tenantA, membership, role, "TENANT", null);
        UUID center = unit(tenantA, "centro", "Centro");
        UUID north = unit(tenantA, "norte", "Norte");

        OrganizationAccess.AccessSummary summary = TenantContext.executarComo(
                tenantA, () -> access.summarize(tenantA, userA));

        assertThat(summary.contexts()).hasSize(3);
        assertThat(summary.contexts().stream()
                .filter(context -> Set.of(center, north).contains(context.id())))
                .allMatch(context -> context.roles().contains("OWNER")
                        && context.permissions().contains("organization.manage"));
    }

    @Test
    void bancoRecusaMembershipEUnidadeDeOutroTenant() {
        UUID membershipA = membership(tenantA, userA,
                Instant.now().minus(1, ChronoUnit.DAYS), null);
        UUID roleA = role(tenantA, "AGENT", "Atendente");
        UUID unitB = unit(tenantB, "centro", "Centro B");

        assertThatThrownBy(() -> TenantContext.executarComo(tenantA, () -> jdbc.update("""
                INSERT INTO membership_scope
                    (id, tenant_id, membership_id, role_id, scope_type, unit_id)
                VALUES (?, ?, ?, ?, 'UNIT', ?)
                """, UuidV7.gerar(), tenantA, membershipA, roleA, unitB)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> TenantContext.executarComo(tenantA, () -> jdbc.update("""
                INSERT INTO organization_membership
                    (id, tenant_id, user_id, valid_from)
                VALUES (?, ?, ?, now())
                """, UuidV7.gerar(), tenantA, userB)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void contatoPodeRepresentarPessoaB2COuOrganizacaoB2B() throws Exception {
        scenario.concederTudoNoTenant(tenantA, userA, "contacts.write");

        mockMvc.perform(post("/api/contatos").with(token()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSON","nome":"Cliente Pessoa","email":"pessoa@example.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PERSON"));

        mockMvc.perform(post("/api/contatos").with(token()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"ORGANIZATION","nome":"Cliente Empresa","empresa":"Cliente Empresa Ltda"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ORGANIZATION"));

        Long people = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM contact WHERE contact_kind = 'PERSON'", Long.class));
        Long organizations = TenantContext.executarComo(tenantA, () -> jdbc.queryForObject(
                "SELECT count(*) FROM contact WHERE contact_kind = 'ORGANIZATION'", Long.class));
        assertThat(people).isOne();
        assertThat(organizations).isOne();
    }

    private UUID unit(UUID tenantId, String code, String name) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO organizational_unit (id, tenant_id, code, name)
                VALUES (?, ?, ?, ?)
                """, id, tenantId, code, name));
        return id;
    }

    private UUID membership(UUID tenantId, UUID userId, Instant from, Instant until) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO organization_membership
                    (id, tenant_id, user_id, valid_from, valid_until)
                VALUES (?, ?, ?, ?, ?)
                """, id, tenantId, userId, Timestamp.from(from),
                until == null ? null : Timestamp.from(until)));
        return id;
    }

    private UUID role(UUID tenantId, String code, String name) {
        UUID id = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO app_role (id, tenant_id, code, name)
                VALUES (?, ?, ?, ?)
                """, id, tenantId, code, name));
        return id;
    }

    private void permission(UUID tenantId, UUID roleId, String code) {
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO role_permission (tenant_id, role_id, permission_code)
                VALUES (?, ?, ?)
                """, tenantId, roleId, code));
    }

    private void scope(UUID tenantId, UUID membershipId, UUID roleId,
                       String type, UUID unitId) {
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                INSERT INTO membership_scope
                    (id, tenant_id, membership_id, role_id, scope_type, unit_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UuidV7.gerar(), tenantId, membershipId, roleId, type, unitId));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt().jwt(builder -> builder.subject(userA.toString())
                .claim("tid", tenantA.toString()).claim("login", "ana"));
    }
}
