package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.RecursoNaoEncontradoException;
import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acesso a papel, permissao de papel e atribuicao.
 *
 * <p>JdbcTemplate, e nao JPA, para acompanhar o resto do modulo: a resolucao de
 * autorizacao ja e SQL com juncoes temporais, e introduzir entidades so para o
 * CRUD criaria dois modelos da mesma tabela — o que diverge no primeiro ajuste.
 *
 * <p>O {@code tenant_id} nunca vem de parametro do chamador: sai do
 * {@code TenantContext}, que sai do token verificado. As consultas ainda o
 * repetem no WHERE porque o RLS e a ultima linha de defesa, nao a unica.
 */
@Repository
class PapelRepository {

    private final JdbcTemplate jdbc;

    PapelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- papéis

    List<Papel> listar() {
        UUID tenant = TenantContext.obrigatorio();
        Map<UUID, List<String>> permissoes = permissoesPorPapel(tenant);
        Map<UUID, Long> uso = atribuicoesPorPapel(tenant);

        return jdbc.query("""
                SELECT id, code, name, description, system_role, active
                  FROM app_role
                 WHERE tenant_id = ? AND deleted_at IS NULL
                 ORDER BY system_role DESC, name
                """, (rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            return new Papel(id, rs.getString("code"), rs.getString("name"),
                    rs.getString("description"), rs.getBoolean("system_role"),
                    rs.getBoolean("active"),
                    permissoes.getOrDefault(id, List.of()),
                    uso.getOrDefault(id, 0L));
        }, tenant);
    }

    Papel obrigatorio(UUID papelId) {
        return listar().stream()
                .filter(papel -> papel.id().equals(papelId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Papel"));
    }

    UUID criar(String codigo, String nome, String descricao, UUID autor) {
        UUID id = UuidV7.gerar();
        jdbc.update("""
                INSERT INTO app_role
                    (id, tenant_id, code, name, description, system_role, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, false, ?, ?)
                """, id, TenantContext.obrigatorio(), codigo, nome, descricao, autor, autor);
        return id;
    }

    void atualizar(UUID papelId, String nome, String descricao, boolean ativo, UUID autor) {
        jdbc.update("""
                UPDATE app_role
                   SET name = ?, description = ?, active = ?, updated_by = ?
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                """, nome, descricao, ativo, autor, TenantContext.obrigatorio(), papelId);
    }

    void remover(UUID papelId, UUID autor) {
        jdbc.update("""
                UPDATE app_role
                   SET deleted_at = now(), active = false, updated_by = ?
                 WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL
                """, autor, TenantContext.obrigatorio(), papelId);
    }

    /**
     * Substitui o conjunto de permissoes do papel.
     *
     * <p>Apagar e reinserir, e nao calcular a diferenca: {@code role_permission}
     * nao tem coluna propria alem da chave, entao a diferenca nao preservaria
     * nada e so acrescentaria um caminho a mais para errar. Roda dentro da
     * transacao do controller, entao ninguem observa o intervalo sem permissao.
     */
    void definirPermissoes(UUID papelId, Collection<String> permissoes, UUID autor) {
        UUID tenant = TenantContext.obrigatorio();
        jdbc.update("DELETE FROM role_permission WHERE tenant_id = ? AND role_id = ?",
                tenant, papelId);
        for (String codigo : permissoes) {
            jdbc.update("""
                    INSERT INTO role_permission (tenant_id, role_id, permission_code, created_by)
                    VALUES (?, ?, ?, ?)
                    """, tenant, papelId, codigo, autor);
        }
    }

    // ----------------------------------------------------------- atribuições

    /**
     * Membros do tenant com os papéis que cada um carrega.
     *
     * <p><b>Consulta nativa em vez de {@code UsuarioLookup}.</b> Injetar a porta
     * do módulo de identidade parece o caminho limpo e fecha um ciclo: identidade
     * já depende de {@code organization.api} para decidir MFA, e o
     * {@code FronteiraDeModulosTest} recusa o ciclo. Ler a coluna por SQL é o
     * mesmo recurso que {@code DireitosDoTitularService} usa para atravessar
     * módulos sem injetar repositório alheio.
     *
     * <p>Só {@code login} e {@code full_name}: identificar quem tem qual papel
     * exige o nome, e mais nada. E-mail e telefone do funcionário não têm o que
     * fazer numa tela de administração de acessos.
     */
    List<Membro> membros() {
        UUID tenant = TenantContext.obrigatorio();
        Map<UUID, List<Atribuicao>> porMembership = atribuicoesPorMembership(tenant);

        return jdbc.query("""
                SELECT m.id AS membership_id, u.id AS user_id, u.login, u.full_name
                  FROM organization_membership m
                  JOIN app_user u ON u.tenant_id = m.tenant_id AND u.id = m.user_id
                 WHERE m.tenant_id = ? AND m.deleted_at IS NULL AND m.status = 'ACTIVE'
                   AND u.deleted_at IS NULL
                 ORDER BY u.full_name, u.login
                """, (rs, row) -> {
            UUID membershipId = rs.getObject("membership_id", UUID.class);
            return new Membro(membershipId, rs.getObject("user_id", UUID.class),
                    rs.getString("login"), rs.getString("full_name"),
                    porMembership.getOrDefault(membershipId, List.of()));
        }, tenant);
    }

    Membro membroObrigatorio(UUID membershipId) {
        return membros().stream()
                .filter(membro -> membro.membershipId().equals(membershipId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro"));
    }

    UUID atribuir(UUID membershipId, UUID papelId, String alcance, UUID autor) {
        UUID id = UuidV7.gerar();
        jdbc.update("""
                INSERT INTO membership_scope
                    (id, tenant_id, membership_id, role_id, scope_type, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, id, TenantContext.obrigatorio(), membershipId, papelId, alcance, autor, autor);
        return id;
    }

    /**
     * Revoga por exclusao logica, preservando o registro.
     *
     * <p>A atribuicao passada e prova de que o acesso existiu, e e o que permite
     * responder "quem podia fazer isto em marco". Apagar a linha apagaria junto
     * a explicacao de um evento de auditoria antigo.
     */
    int revogar(UUID membershipId, UUID atribuicaoId, UUID autor) {
        return jdbc.update("""
                UPDATE membership_scope
                   SET deleted_at = now(), status = 'REVOKED', updated_by = ?
                 WHERE tenant_id = ? AND membership_id = ? AND id = ? AND deleted_at IS NULL
                """, autor, TenantContext.obrigatorio(), membershipId, atribuicaoId);
    }

    /** Atribuições vivas de papel de sistema no tenant inteiro. */
    long atribuicoesDeSistemaVivas() {
        Long total = jdbc.queryForObject("""
                SELECT count(*)
                  FROM membership_scope s
                  JOIN app_role r ON r.tenant_id = s.tenant_id AND r.id = s.role_id
                 WHERE s.tenant_id = ? AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                   AND r.system_role AND r.deleted_at IS NULL
                """, Long.class, TenantContext.obrigatorio());
        return total == null ? 0L : total;
    }

    // ----------------------------------------------------------------- apoio

    private Map<UUID, List<String>> permissoesPorPapel(UUID tenant) {
        Map<UUID, List<String>> agrupado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT role_id, permission_code
                  FROM role_permission
                 WHERE tenant_id = ?
                 ORDER BY role_id, permission_code
                """, rs -> {
            agrupado.computeIfAbsent(rs.getObject("role_id", UUID.class),
                    chave -> new ArrayList<>()).add(rs.getString("permission_code"));
        }, tenant);
        return agrupado;
    }

    private Map<UUID, Long> atribuicoesPorPapel(UUID tenant) {
        Map<UUID, Long> agrupado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT role_id, count(*) AS total
                  FROM membership_scope
                 WHERE tenant_id = ? AND deleted_at IS NULL AND status = 'ACTIVE'
                 GROUP BY role_id
                """, rs -> {
            agrupado.put(rs.getObject("role_id", UUID.class), rs.getLong("total"));
        }, tenant);
        return agrupado;
    }

    private Map<UUID, List<Atribuicao>> atribuicoesPorMembership(UUID tenant) {
        Map<UUID, List<Atribuicao>> agrupado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.id, s.membership_id, s.role_id, s.scope_type, r.code, r.name
                  FROM membership_scope s
                  JOIN app_role r ON r.tenant_id = s.tenant_id AND r.id = s.role_id
                 WHERE s.tenant_id = ? AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                   AND r.deleted_at IS NULL
                 ORDER BY r.name
                """, rs -> {
            agrupado.computeIfAbsent(rs.getObject("membership_id", UUID.class),
                            chave -> new ArrayList<>())
                    .add(new Atribuicao(rs.getObject("id", UUID.class),
                            rs.getObject("role_id", UUID.class), rs.getString("code"),
                            rs.getString("name"), rs.getString("scope_type")));
        }, tenant);
        return agrupado;
    }

    record Papel(UUID id, String codigo, String nome, String descricao, boolean sistema,
                 boolean ativo, List<String> permissoes, long atribuicoes) {
        Papel {
            permissoes = List.copyOf(permissoes);
        }
    }

    record Membro(UUID membershipId, UUID usuarioId, String login, String nome,
                  List<Atribuicao> atribuicoes) {
        Membro {
            atribuicoes = List.copyOf(atribuicoes);
        }
    }

    record Atribuicao(UUID id, UUID papelId, String papelCodigo, String papelNome,
                      String alcance) {
    }
}
