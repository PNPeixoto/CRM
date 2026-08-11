package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.organization.api.OrganizationAccess;
import br.com.pnp.crm.shared.api.AcessoNegadoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
class OrganizationAccessService implements OrganizationAccess {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    OrganizationAccessService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    OrganizationAccessService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AccessSummary summarize(UUID tenantId, UUID userId) {
        Instant now = clock.instant();
        UUID membershipId = activeMembership(tenantId, userId, now);
        List<Assignment> assignments = assignments(tenantId, membershipId, now);

        ContextBuilder tenant = new ContextBuilder(
                tenantId, ScopeType.TENANT, "tenant", "Toda a empresa");
        Map<UUID, ContextBuilder> units = new LinkedHashMap<>();

        boolean wholeTenant = assignments.stream()
                .anyMatch(item -> item.scope() == ScopeType.TENANT);
        List<UnitRow> authorizedUnits = wholeTenant
                ? allActiveUnits(tenantId)
                : unitsFromAssignments(assignments);
        authorizedUnits.forEach(unit -> units.put(unit.id(),
                new ContextBuilder(unit.id(), ScopeType.UNIT, unit.code(), unit.name())));

        for (Assignment assignment : assignments) {
            if (assignment.scope() == ScopeType.TENANT || assignment.scope() == ScopeType.OWN) {
                tenant.add(assignment);
                units.values().forEach(unit -> unit.add(assignment));
            } else if (assignment.scope() == ScopeType.UNIT) {
                ContextBuilder unit = units.get(assignment.unitId());
                if (unit != null) {
                    unit.add(assignment);
                }
            }
        }

        List<AuthorizedContext> contexts = new ArrayList<>();
        contexts.add(tenant.build());
        units.values().stream()
                .map(ContextBuilder::build)
                .sorted(Comparator.comparing(AuthorizedContext::name))
                .forEach(contexts::add);
        return new AccessSummary(tenantId, membershipId, contexts);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizedContext selectUnit(UUID tenantId, UUID userId, UUID unitId) {
        return summarize(tenantId, userId).contexts().stream()
                .filter(context -> context.type() == ScopeType.UNIT && context.id().equals(unitId))
                .findFirst()
                .orElseThrow(AcessoNegadoException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ScopeType> permissionScopes(UUID tenantId, UUID userId) {
        Instant now = clock.instant();
        UUID membershipId = activeMembership(tenantId, userId, now);

        Map<String, ScopeType> efetivo = new LinkedHashMap<>();
        for (Assignment assignment : assignments(tenantId, membershipId, now)) {
            if (assignment.permission() == null) {
                continue;
            }
            // Escolhe o alcance que a aplicação consegue realmente decidir.
            // TENANT vence tudo; OWN continua válido quando a mesma permissão
            // também aparece em UNIT/TEAM/NETWORK, que hoje falham fechados.
            // Usar ordinal aqui fazia UNIT vencer OWN e anulava uma concessão
            // própria perfeitamente válida.
            efetivo.merge(assignment.permission(), assignment.scope(),
                    OrganizationAccessService::escopoEfetivo);
        }
        return Map.copyOf(efetivo);
    }

    /**
     * Escolhe o alcance que a aplicação consegue realmente decidir, preferindo
     * o mais amplo entre os decidíveis.
     *
     * <p>A ordem é TENANT, depois TEAM, depois OWN — e é escrita por extenso em
     * vez de comparar {@code ordinal()}, porque UNIT e NETWORK ficam no meio da
     * ordem do enum sem decidir nada. Comparar ordinais faria UNIT vencer OWN e
     * anularia uma concessão própria perfeitamente válida.
     */
    private static ScopeType escopoEfetivo(ScopeType atual, ScopeType novo) {
        if (atual == ScopeType.TENANT || novo == ScopeType.TENANT) {
            return ScopeType.TENANT;
        }
        if (atual == ScopeType.TEAM || novo == ScopeType.TEAM) {
            return ScopeType.TEAM;
        }
        if (atual == ScopeType.OWN || novo == ScopeType.OWN) {
            return ScopeType.OWN;
        }
        // Nenhum dos dois é decidível sobre os registros atuais. Preservar o
        // primeiro mantém a resposta determinística sem ampliar acesso.
        return atual;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> equipeDe(UUID tenantId, UUID userId) {
        List<UUID> liderados = jdbc.queryForList("""
                SELECT member_user_id
                  FROM team_member
                 WHERE tenant_id = ? AND manager_user_id = ?
                   AND valid_until IS NULL
                """, UUID.class, tenantId, userId);

        Set<UUID> equipe = new LinkedHashSet<>(liderados);
        // O próprio usuário sempre entra: gestor que enxerga a equipe e não
        // enxerga a própria carteira seria um recorte que ninguém pediu.
        equipe.add(userId);
        return Set.copyOf(equipe);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyRole(UUID tenantId, UUID userId, Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        Timestamp now = Timestamp.from(clock.instant());
        Integer found = jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                      FROM organization_membership m
                      JOIN membership_scope s
                        ON s.tenant_id = m.tenant_id AND s.membership_id = m.id
                      JOIN app_role r
                        ON r.tenant_id = s.tenant_id AND r.id = s.role_id
                     WHERE m.tenant_id = ? AND m.user_id = ?
                       AND m.status = 'ACTIVE' AND m.deleted_at IS NULL
                       AND m.valid_from <= ?
                       AND (m.valid_until IS NULL OR m.valid_until > ?)
                       AND s.status = 'ACTIVE' AND s.deleted_at IS NULL
                       AND s.valid_from <= ?
                       AND (s.valid_until IS NULL OR s.valid_until > ?)
                       AND r.active AND r.deleted_at IS NULL
                       AND r.code = ANY (string_to_array(?, ','))
                ) THEN 1 ELSE 0 END
                """, Integer.class, tenantId, userId, now, now, now, now,
                String.join(",", roleCodes));
        return found != null && found == 1;
    }

    private UUID activeMembership(UUID tenantId, UUID userId, Instant now) {
        Timestamp instant = Timestamp.from(now);
        List<UUID> found = jdbc.queryForList("""
                SELECT id
                  FROM organization_membership
                 WHERE tenant_id = ? AND user_id = ?
                   AND status = 'ACTIVE' AND deleted_at IS NULL
                   AND valid_from <= ?
                   AND (valid_until IS NULL OR valid_until > ?)
                """, UUID.class, tenantId, userId, instant, instant);
        if (found.size() != 1) {
            throw new AcessoNegadoException();
        }
        return found.getFirst();
    }

    private List<Assignment> assignments(UUID tenantId, UUID membershipId, Instant now) {
        Timestamp instant = Timestamp.from(now);
        return jdbc.query("""
                SELECT s.scope_type, s.unit_id, u.code AS unit_code, u.name AS unit_name,
                       r.code AS role_code, p.permission_code
                  FROM membership_scope s
                  JOIN app_role r
                    ON r.tenant_id = s.tenant_id AND r.id = s.role_id
                   AND r.active AND r.deleted_at IS NULL
             LEFT JOIN organizational_unit u
                    ON u.tenant_id = s.tenant_id AND u.id = s.unit_id
                   AND u.active AND u.deleted_at IS NULL
             LEFT JOIN role_permission p
                    ON p.tenant_id = r.tenant_id AND p.role_id = r.id
                 WHERE s.tenant_id = ? AND s.membership_id = ?
                   AND s.status = 'ACTIVE' AND s.deleted_at IS NULL
                   AND s.valid_from <= ?
                   AND (s.valid_until IS NULL OR s.valid_until > ?)
                   AND (s.scope_type <> 'UNIT' OR u.id IS NOT NULL)
                 ORDER BY s.scope_type, u.name, r.code, p.permission_code
                """, (rs, row) -> mapAssignment(rs), tenantId, membershipId, instant, instant);
    }

    private List<UnitRow> allActiveUnits(UUID tenantId) {
        return jdbc.query("""
                SELECT id, code, name
                  FROM organizational_unit
                 WHERE tenant_id = ? AND active AND deleted_at IS NULL
                 ORDER BY name
                """, (rs, row) -> new UnitRow(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")), tenantId);
    }

    private static List<UnitRow> unitsFromAssignments(List<Assignment> assignments) {
        Map<UUID, UnitRow> unique = new LinkedHashMap<>();
        assignments.stream()
                .filter(item -> item.scope() == ScopeType.UNIT)
                .forEach(item -> unique.putIfAbsent(item.unitId(),
                        new UnitRow(item.unitId(), item.unitCode(), item.unitName())));
        return List.copyOf(unique.values());
    }

    private static Assignment mapAssignment(ResultSet rs) throws SQLException {
        return new Assignment(
                ScopeType.valueOf(rs.getString("scope_type")),
                rs.getObject("unit_id", UUID.class),
                rs.getString("unit_code"),
                rs.getString("unit_name"),
                rs.getString("role_code"),
                rs.getString("permission_code"));
    }

    private record Assignment(ScopeType scope, UUID unitId, String unitCode,
                              String unitName, String role, String permission) {
    }

    private record UnitRow(UUID id, String code, String name) {
    }

    private static final class ContextBuilder {
        private final UUID id;
        private final ScopeType type;
        private final String code;
        private final String name;
        private final Set<String> roles = new LinkedHashSet<>();
        private final Set<String> permissions = new LinkedHashSet<>();
        private final Set<ScopeType> scopes = new LinkedHashSet<>();

        private ContextBuilder(UUID id, ScopeType type, String code, String name) {
            this.id = id;
            this.type = type;
            this.code = code;
            this.name = name;
            scopes.add(type);
        }

        private void add(Assignment assignment) {
            roles.add(assignment.role());
            if (assignment.permission() != null) {
                permissions.add(assignment.permission());
            }
            scopes.add(assignment.scope());
        }

        private AuthorizedContext build() {
            return new AuthorizedContext(id, type, code, name, roles, permissions, scopes);
        }
    }
}
