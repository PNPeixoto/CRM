package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Monta dados pelos mesmos privilégios e políticas usados pela aplicação. */
@Component
public class CenarioMultiTenant {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TestDatabaseCleaner cleaner;

    CenarioMultiTenant(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                       TestDatabaseCleaner cleaner) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.cleaner = cleaner;
    }

    public UUID criarTenant(String slug, String name) {
        UUID tenantId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update(
                "INSERT INTO tenant (id, slug, name) VALUES (?, ?, ?)", tenantId, slug, name));
        return tenantId;
    }

    public UUID criarUsuario(UUID tenantId, String login, String password) {
        UUID userId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                        INSERT INTO app_user (id, tenant_id, login, email, full_name, password_hash)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                userId, tenantId, login, login + "@" + tenantId + ".teste", "Usuário " + login,
                passwordEncoder.encode(password)));
        return userId;
    }

    /**
     * Concede permissões ao usuário sob um alcance, criando membership e papel.
     *
     * <p>Sem isto, todo cenário de teste que chama endpoint de domínio nasce
     * sem membership vigente e recebe 403 — o que é o comportamento certo da
     * aplicação e apenas ruído no teste que não está medindo autorização.
     *
     * @param scopeType TENANT, UNIT ou OWN, como em {@code membership_scope}
     */
    public UUID conceder(UUID tenantId, UUID userId, String scopeType, String... permissoes) {
        return conceder(tenantId, userId, scopeType, null, permissoes);
    }

    /** Concede sob uma unidade; {@code unitId} é obrigatório para UNIT. */
    public UUID concederNaUnidade(UUID tenantId, UUID userId, UUID unitId, String... permissoes) {
        return conceder(tenantId, userId, "UNIT", unitId, permissoes);
    }

    private UUID conceder(UUID tenantId, UUID userId, String scopeType, UUID unitId,
                          String... permissoes) {
        UUID membershipId = membershipVigente(tenantId, userId);
        UUID roleId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> {
            jdbc.update("""
                    INSERT INTO app_role (id, tenant_id, code, name)
                    VALUES (?, ?, ?, ?)
                    """, roleId, tenantId,
                    // Caixa alta por app_role_code_formato, e o trecho final do
                    // UUID v7 porque o inicial é o relógio: dois papéis criados
                    // no mesmo milissegundo teriam o mesmo código.
                    "PAPEL_" + roleId.toString().substring(24).toUpperCase(java.util.Locale.ROOT),
                    "Papel de teste");
            for (String permissao : permissoes) {
                jdbc.update("""
                        INSERT INTO role_permission (tenant_id, role_id, permission_code)
                        VALUES (?, ?, ?)
                        """, tenantId, roleId, permissao);
            }
            jdbc.update("""
                    INSERT INTO membership_scope
                        (id, tenant_id, membership_id, role_id, scope_type, unit_id, valid_from)
                    VALUES (?, ?, ?, ?, ?, ?, now() - interval '1 day')
                    """, UuidV7.gerar(), tenantId, membershipId, roleId, scopeType, unitId);
            return roleId;
        });
        return roleId;
    }

    /** Atalho para o caso comum: acesso a tudo do tenant. */
    public void concederTudoNoTenant(UUID tenantId, UUID userId, String... permissoes) {
        conceder(tenantId, userId, "TENANT", permissoes);
    }

    public UUID membershipVigente(UUID tenantId, UUID userId) {
        return TenantContext.executarComo(tenantId, () -> {
            java.util.List<UUID> existente = jdbc.queryForList("""
                    SELECT id FROM organization_membership
                     WHERE tenant_id = ? AND user_id = ? AND deleted_at IS NULL
                    """, UUID.class, tenantId, userId);
            if (!existente.isEmpty()) {
                return existente.getFirst();
            }
            UUID id = UuidV7.gerar();
            jdbc.update("""
                    INSERT INTO organization_membership
                        (id, tenant_id, user_id, valid_from)
                    VALUES (?, ?, ?, now() - interval '1 day')
                    """, id, tenantId, userId);
            return id;
        });
    }

    public void limpar() {
        // TRUNCATE não passa por RLS e nunca é concedido ao runtime. A limpeza
        // usa uma conexão administrativa privada do suporte de testes.
        cleaner.clean();
    }
}
