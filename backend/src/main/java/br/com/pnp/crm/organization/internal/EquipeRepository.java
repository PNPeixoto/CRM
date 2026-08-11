package br.com.pnp.crm.organization.internal;

import br.com.pnp.crm.shared.api.TenantContext;
import br.com.pnp.crm.shared.api.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Composicao viva das equipes do tenant. */
@Repository
class EquipeRepository {

    private final JdbcTemplate jdbc;

    EquipeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Gestores com os liderados vigentes de cada um. */
    Map<UUID, List<UUID>> composicao() {
        Map<UUID, List<UUID>> agrupado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT manager_user_id, member_user_id
                  FROM team_member
                 WHERE tenant_id = ? AND valid_until IS NULL
                 ORDER BY manager_user_id, member_user_id
                """, rs -> {
            agrupado.computeIfAbsent(rs.getObject("manager_user_id", UUID.class),
                    chave -> new ArrayList<>()).add(rs.getObject("member_user_id", UUID.class));
        }, TenantContext.obrigatorio());
        return agrupado;
    }

    boolean lidera(UUID gestorId, UUID lideradoId) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM team_member
                 WHERE tenant_id = ? AND manager_user_id = ? AND member_user_id = ?
                   AND valid_until IS NULL
                """, Long.class, TenantContext.obrigatorio(), gestorId, lideradoId);
        return total != null && total > 0;
    }

    void incluir(UUID gestorId, UUID lideradoId, UUID autor) {
        jdbc.update("""
                INSERT INTO team_member
                    (id, tenant_id, manager_user_id, member_user_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UuidV7.gerar(), TenantContext.obrigatorio(), gestorId, lideradoId,
                autor, autor);
    }

    /**
     * Encerra a composicao sem apagar a linha.
     *
     * <p>O periodo em que alguem respondeu a um gestor explica acessos
     * registrados na trilha naquele intervalo. Apagar a linha apagaria junto a
     * justificativa de um evento antigo — e o runtime nem tem {@code DELETE}
     * nesta tabela.
     */
    int encerrar(UUID gestorId, UUID lideradoId, UUID autor) {
        return jdbc.update("""
                UPDATE team_member
                   SET valid_until = now(), updated_by = ?
                 WHERE tenant_id = ? AND manager_user_id = ? AND member_user_id = ?
                   AND valid_until IS NULL
                """, autor, TenantContext.obrigatorio(), gestorId, lideradoId);
    }
}
