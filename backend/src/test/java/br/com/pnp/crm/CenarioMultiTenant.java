package br.com.pnp.crm;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cria tenants e usuários para os testes.
 *
 * <p>Os INSERT passam pelo mesmo {@code DataSource} da aplicação e portanto
 * pelo mesmo RLS. Isso é proposital: se a política estiver quebrada a ponto de
 * impedir inserção legítima, os testes falham na preparação, e não com um
 * resultado sutilmente errado mais adiante.
 */
@Component
public class CenarioMultiTenant {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    CenarioMultiTenant(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public UUID criarTenant(String slug, String nome) {
        UUID tenantId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update(
                "INSERT INTO tenant (id, slug, name) VALUES (?, ?, ?)", tenantId, slug, nome));
        return tenantId;
    }

    public UUID criarUsuario(UUID tenantId, String login, String senha) {
        UUID usuarioId = UuidV7.gerar();
        TenantContext.executarComo(tenantId, () -> jdbc.update("""
                        INSERT INTO app_user (id, tenant_id, login, email, full_name, password_hash)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                usuarioId, tenantId, login, login + "@" + tenantId + ".teste", "Usuário " + login,
                passwordEncoder.encode(senha)));
        return usuarioId;
    }

    public void limpar() {
        // Sem contexto de tenant o RLS esconde tudo, inclusive de um DELETE.
        // A limpeza precisa rodar como superusuário lógico da migration, e o
        // caminho honesto para isso no teste é TRUNCATE, que é DDL e não passa
        // por política de linha.
        jdbc.execute("TRUNCATE TABLE refresh_token, app_user, tenant CASCADE");
    }
}
