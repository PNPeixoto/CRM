-- Alcance intermediário: "o gestor enxerga o que a equipe dele faz".
--
-- Até aqui só existiam TENANT (vê tudo) e OWN (vê o próprio). Faltava o meio,
-- que é justamente o que uma hierarquia com Gestor e Gerente pede.
--
-- Equipe, e não unidade. `contact`, `deal` e `task` já carregam
-- `owner_user_id` com FK composta e índice por responsável desde a V5, então o
-- recorte por equipe é filtro sobre coluna existente: nenhuma coluna nova em
-- tabela de domínio, nenhum backfill inventado, e nenhum conflito com o
-- ADR-0008, que trata de unidade. Ver ADR-0015.

CREATE TABLE team_member
(
    id              uuid PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES tenant (id),
    manager_user_id uuid        NOT NULL,
    member_user_id  uuid        NOT NULL,
    valid_from      timestamptz NOT NULL DEFAULT now(),
    valid_until     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    -- Ninguém responde por si mesmo: a auto-referência criaria um nó que é
    -- gestor e liderado ao mesmo tempo, e "minha equipe" passaria a incluir
    -- quem a consulta já inclui por outro caminho.
    CONSTRAINT team_member_sem_autogestao
        CHECK (manager_user_id <> member_user_id),
    CONSTRAINT team_member_vigencia_valida
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT team_member_gestor_mesmo_tenant
        FOREIGN KEY (tenant_id, manager_user_id) REFERENCES app_user (tenant_id, id),
    CONSTRAINT team_member_liderado_mesmo_tenant
        FOREIGN KEY (tenant_id, member_user_id) REFERENCES app_user (tenant_id, id),
    CONSTRAINT team_member_tenant_id_unico UNIQUE (tenant_id, id)
);

-- Uma composição viva por par. O índice é parcial para que remover e recompor
-- a equipe continue possível sem apagar o histórico.
CREATE UNIQUE INDEX team_member_vigente_unico
    ON team_member (tenant_id, manager_user_id, member_user_id)
    WHERE valid_until IS NULL;

CREATE INDEX team_member_por_gestor
    ON team_member (tenant_id, manager_user_id)
    WHERE valid_until IS NULL;

CREATE TRIGGER team_member_updated_at
    BEFORE UPDATE ON team_member
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE team_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_member FORCE ROW LEVEL SECURITY;
CREATE POLICY team_member_isolamento ON team_member
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- Composição de equipe decide quem enxerga o quê: é superfície de
-- autorização, e apagar a linha apagaria junto a explicação de um acesso
-- passado. Encerrar é `valid_until`, e só isso.
REVOKE DELETE ON team_member FROM "${runtime_role}";
REVOKE UPDATE ON team_member FROM "${runtime_role}";
GRANT UPDATE (valid_until, updated_at, updated_by) ON team_member TO "${runtime_role}";

-- ---------------------------------------------------------------------------
-- O alcance TEAM passa a ser persistível.
-- ---------------------------------------------------------------------------
--
-- `ScopeType` já declarava TEAM desde a V10; o CHECK é que não aceitava, e a
-- aplicação falhava fechada. Agora o alcance existe dos dois lados.

ALTER TABLE membership_scope DROP CONSTRAINT membership_scope_tipo_valido;
ALTER TABLE membership_scope ADD CONSTRAINT membership_scope_tipo_valido
    CHECK (scope_type IN ('TENANT', 'UNIT', 'TEAM', 'OWN'));

-- TEAM não tem unidade, pela mesma razão que TENANT e OWN não têm.
ALTER TABLE membership_scope DROP CONSTRAINT membership_scope_unidade_coerente;
ALTER TABLE membership_scope ADD CONSTRAINT membership_scope_unidade_coerente CHECK (
    (scope_type = 'UNIT' AND unit_id IS NOT NULL)
    OR (scope_type IN ('TENANT', 'TEAM', 'OWN') AND unit_id IS NULL));
