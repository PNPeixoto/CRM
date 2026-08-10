-- Motor interno de automacoes: definicoes imutaveis, execucao duravel e trilha
-- sanitizada. Payloads do gatilho e das mensagens nao sao persistidos aqui.

CREATE TABLE automation_definition
(
    id                 uuid PRIMARY KEY,
    tenant_id          uuid        NOT NULL REFERENCES tenant (id),
    code               text        NOT NULL,
    name               text        NOT NULL,
    status             text        NOT NULL DEFAULT 'DRAFT',
    current_version    integer     NOT NULL DEFAULT 1,
    active_version_id  uuid,
    concurrency_limit  integer     NOT NULL DEFAULT 1,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_by         uuid,
    deleted_at         timestamptz,
    CONSTRAINT automation_definition_code_formato
        CHECK (code ~ '^[a-z][a-z0-9_-]{2,79}$'),
    CONSTRAINT automation_definition_nome_valido
        CHECK (char_length(name) BETWEEN 1 AND 200),
    CONSTRAINT automation_definition_status_valido
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT automation_definition_versao_valida CHECK (current_version > 0),
    CONSTRAINT automation_definition_concorrencia_valida
        CHECK (concurrency_limit BETWEEN 1 AND 20),
    CONSTRAINT automation_definition_ativa_tem_versao
        CHECK (status <> 'ACTIVE' OR active_version_id IS NOT NULL),
    CONSTRAINT automation_definition_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX automation_definition_code_unico
    ON automation_definition (tenant_id, code) WHERE deleted_at IS NULL;
CREATE INDEX automation_definition_por_status
    ON automation_definition (tenant_id, status, id) WHERE deleted_at IS NULL;

CREATE TRIGGER automation_definition_updated_at
    BEFORE UPDATE ON automation_definition
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE automation_definition_version
(
    id                    uuid PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    definition_id         uuid        NOT NULL,
    version_number        integer     NOT NULL,
    trigger_type          text        NOT NULL,
    specification         jsonb       NOT NULL,
    specification_sha256  text        NOT NULL,
    max_duration_seconds  integer     NOT NULL,
    step_count            integer     NOT NULL,
    branch_count          integer     NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    CONSTRAINT automation_definition_version_numero_valido
        CHECK (version_number > 0),
    CONSTRAINT automation_definition_version_gatilho_valido
        CHECK (trigger_type ~ '^[A-Z][A-Z0-9_]{2,79}$'),
    CONSTRAINT automation_definition_version_json_valido
        CHECK (jsonb_typeof(specification) = 'object'),
    CONSTRAINT automation_definition_version_hash_valido
        CHECK (specification_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT automation_definition_version_duracao_valida
        CHECK (max_duration_seconds BETWEEN 1 AND 86400),
    CONSTRAINT automation_definition_version_passos_validos
        CHECK (step_count BETWEEN 1 AND 500),
    CONSTRAINT automation_definition_version_ramos_validos
        CHECK (branch_count BETWEEN 0 AND 500),
    CONSTRAINT automation_definition_version_definicao_mesmo_tenant
        FOREIGN KEY (tenant_id, definition_id)
        REFERENCES automation_definition (tenant_id, id),
    CONSTRAINT automation_definition_version_numero_unico
        UNIQUE (tenant_id, definition_id, version_number),
    CONSTRAINT automation_definition_version_tenant_id_unico UNIQUE (tenant_id, id)
);

ALTER TABLE automation_definition
    ADD CONSTRAINT automation_definition_versao_ativa_mesmo_tenant
    FOREIGN KEY (tenant_id, active_version_id)
    REFERENCES automation_definition_version (tenant_id, id);

CREATE TABLE automation_execution
(
    id                     uuid PRIMARY KEY,
    tenant_id              uuid        NOT NULL REFERENCES tenant (id),
    definition_id          uuid        NOT NULL,
    definition_version_id  uuid        NOT NULL,
    trigger_key            text        NOT NULL,
    trigger_type           text        NOT NULL,
    source_reference_id    uuid,
    root_execution_id      uuid,
    parent_execution_id    uuid,
    dry_run                boolean     NOT NULL DEFAULT false,
    status                 text        NOT NULL DEFAULT 'QUEUED',
    deadline_at            timestamptz NOT NULL,
    lease_owner            text,
    lease_until            timestamptz,
    started_at             timestamptz,
    finished_at            timestamptz,
    failure_code           text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_by             uuid,
    CONSTRAINT automation_execution_status_valido CHECK (status IN (
        'QUEUED', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED', 'CANCELLED',
        'TIMED_OUT', 'REJECTED'
    )),
    CONSTRAINT automation_execution_trigger_key_valida
        CHECK (char_length(trigger_key) BETWEEN 1 AND 200),
    CONSTRAINT automation_execution_trigger_type_valido
        CHECK (trigger_type ~ '^[A-Z][A-Z0-9_]{2,79}$'),
    CONSTRAINT automation_execution_failure_code_valido
        CHECK (failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT automation_execution_lease_coerente CHECK (
        (lease_owner IS NULL AND lease_until IS NULL)
        OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    ),
    CONSTRAINT automation_execution_definicao_mesmo_tenant
        FOREIGN KEY (tenant_id, definition_id)
        REFERENCES automation_definition (tenant_id, id),
    CONSTRAINT automation_execution_versao_mesmo_tenant
        FOREIGN KEY (tenant_id, definition_version_id)
        REFERENCES automation_definition_version (tenant_id, id),
    CONSTRAINT automation_execution_tenant_id_unico UNIQUE (tenant_id, id),
    CONSTRAINT automation_execution_gatilho_idempotente
        UNIQUE (tenant_id, definition_id, trigger_key)
);

ALTER TABLE automation_execution
    ADD CONSTRAINT automation_execution_raiz_mesmo_tenant
        FOREIGN KEY (tenant_id, root_execution_id)
        REFERENCES automation_execution (tenant_id, id),
    ADD CONSTRAINT automation_execution_pai_mesmo_tenant
        FOREIGN KEY (tenant_id, parent_execution_id)
        REFERENCES automation_execution (tenant_id, id);

CREATE INDEX automation_execution_fila
    ON automation_execution (status, lease_until, created_at, id)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX automation_execution_concorrencia_tenant
    ON automation_execution (tenant_id, status, definition_id)
    WHERE status = 'RUNNING';
CREATE INDEX automation_execution_historico
    ON automation_execution (tenant_id, created_at DESC, id DESC);

CREATE TRIGGER automation_execution_updated_at
    BEFORE UPDATE ON automation_execution
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE automation_execution_step
(
    id                     uuid PRIMARY KEY,
    tenant_id              uuid        NOT NULL REFERENCES tenant (id),
    execution_id           uuid        NOT NULL,
    definition_version_id  uuid        NOT NULL,
    step_key               text        NOT NULL,
    action_type            text        NOT NULL,
    effect_type            text        NOT NULL,
    retry_safe             boolean     NOT NULL,
    max_attempts           integer     NOT NULL,
    attempt_count          integer     NOT NULL DEFAULT 0,
    status                 text        NOT NULL DEFAULT 'PENDING',
    not_before             timestamptz NOT NULL DEFAULT now(),
    lease_until            timestamptz,
    started_at             timestamptz,
    finished_at            timestamptz,
    failure_code           text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT automation_execution_step_chave_valida
        CHECK (step_key ~ '^[a-z][a-z0-9_-]{0,79}$'),
    CONSTRAINT automation_execution_step_acao_versionada
        CHECK (action_type ~ '^[A-Z][A-Z0-9_]{1,69}_V[0-9]+$'),
    CONSTRAINT automation_execution_step_efeito_valido CHECK (effect_type IN (
        'INTERNAL', 'EXTERNAL_REVERSIBLE', 'EXTERNAL_IRREVERSIBLE'
    )),
    CONSTRAINT automation_execution_step_tentativas_validas CHECK (
        max_attempts BETWEEN 1 AND 5 AND attempt_count BETWEEN 0 AND max_attempts
    ),
    CONSTRAINT automation_execution_step_status_valido CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED', 'CANCELLED'
    )),
    CONSTRAINT automation_execution_step_failure_code_valido
        CHECK (failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT automation_execution_step_execucao_mesmo_tenant
        FOREIGN KEY (tenant_id, execution_id)
        REFERENCES automation_execution (tenant_id, id),
    CONSTRAINT automation_execution_step_versao_mesmo_tenant
        FOREIGN KEY (tenant_id, definition_version_id)
        REFERENCES automation_definition_version (tenant_id, id),
    CONSTRAINT automation_execution_step_unico
        UNIQUE (tenant_id, execution_id, step_key),
    CONSTRAINT automation_execution_step_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE INDEX automation_execution_step_fila
    ON automation_execution_step (tenant_id, execution_id, status, not_before, created_at)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TRIGGER automation_execution_step_updated_at
    BEFORE UPDATE ON automation_execution_step
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE automation_compensation
(
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES tenant (id),
    execution_id   uuid        NOT NULL,
    step_id        uuid        NOT NULL,
    effect_type    text        NOT NULL,
    status         text        NOT NULL,
    reason_code    text        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT automation_compensation_efeito_valido CHECK (effect_type IN (
        'EXTERNAL_REVERSIBLE', 'EXTERNAL_IRREVERSIBLE'
    )),
    CONSTRAINT automation_compensation_status_valido CHECK (status IN (
        'REGISTERED', 'COMPLETED', 'FAILED', 'IMPOSSIBLE'
    )),
    CONSTRAINT automation_compensation_reason_valida
        CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT automation_compensation_execucao_mesmo_tenant
        FOREIGN KEY (tenant_id, execution_id)
        REFERENCES automation_execution (tenant_id, id),
    CONSTRAINT automation_compensation_passo_mesmo_tenant
        FOREIGN KEY (tenant_id, step_id)
        REFERENCES automation_execution_step (tenant_id, id),
    CONSTRAINT automation_compensation_unica
        UNIQUE (tenant_id, execution_id, step_id)
);

CREATE TRIGGER automation_compensation_updated_at
    BEFORE UPDATE ON automation_compensation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE automation_transition
(
    id              uuid PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES tenant (id),
    execution_id    uuid        NOT NULL,
    step_id         uuid,
    entity_type     text        NOT NULL,
    previous_status text,
    new_status      text        NOT NULL,
    reason_code     text        NOT NULL,
    actor_type      text        NOT NULL,
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT automation_transition_entity_valida
        CHECK (entity_type IN ('EXECUTION', 'STEP')),
    CONSTRAINT automation_transition_status_anterior_valido
        CHECK (previous_status IS NULL OR previous_status ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT automation_transition_status_novo_valido
        CHECK (new_status ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT automation_transition_reason_valida
        CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT automation_transition_actor_valido
        CHECK (actor_type IN ('SYSTEM', 'USER', 'WORKER')),
    CONSTRAINT automation_transition_execucao_mesmo_tenant
        FOREIGN KEY (tenant_id, execution_id)
        REFERENCES automation_execution (tenant_id, id),
    CONSTRAINT automation_transition_passo_mesmo_tenant
        FOREIGN KEY (tenant_id, step_id)
        REFERENCES automation_execution_step (tenant_id, id)
);

CREATE INDEX automation_transition_timeline
    ON automation_transition (tenant_id, execution_id, occurred_at, id);

ALTER TABLE automation_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY automation_definition_isolamento ON automation_definition
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE automation_definition_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_definition_version FORCE ROW LEVEL SECURITY;
CREATE POLICY automation_definition_version_isolamento ON automation_definition_version
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE automation_execution ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_execution FORCE ROW LEVEL SECURITY;
CREATE POLICY automation_execution_isolamento ON automation_execution
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE automation_execution_step ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_execution_step FORCE ROW LEVEL SECURITY;
CREATE POLICY automation_execution_step_isolamento ON automation_execution_step
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE automation_compensation ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_compensation FORCE ROW LEVEL SECURITY;
CREATE POLICY automation_compensation_isolamento ON automation_compensation
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE automation_transition ENABLE ROW LEVEL SECURITY;
ALTER TABLE automation_transition FORCE ROW LEVEL SECURITY;
CREATE POLICY automation_transition_isolamento ON automation_transition
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE FUNCTION automation_immutable() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION '% e append-only', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER automation_definition_version_sem_update_delete
    BEFORE UPDATE OR DELETE ON automation_definition_version
    FOR EACH ROW EXECUTE FUNCTION automation_immutable();

CREATE TRIGGER automation_transition_sem_update_delete
    BEFORE UPDATE OR DELETE ON automation_transition
    FOR EACH ROW EXECUTE FUNCTION automation_immutable();

-- Reserva global estreita. A funcao retorna apenas ids; o processamento volta
-- ao contexto RLS do tenant antes de ler definicao, passo ou causa.
CREATE FUNCTION reservar_execucoes_automacao(
    p_limite integer,
    p_lease_seconds integer,
    p_max_por_tenant integer,
    p_worker text
) RETURNS TABLE (execution_id uuid, execution_tenant_id uuid)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    candidata record;
    reservadas integer := 0;
    ativas_tenant integer;
    ativas_definicao integer;
    limite_definicao integer;
BEGIN
    IF p_limite < 1 OR p_limite > 500
       OR p_lease_seconds < 5 OR p_lease_seconds > 3600
       OR p_max_por_tenant < 1 OR p_max_por_tenant > 500
       OR p_worker IS NULL OR char_length(p_worker) NOT BETWEEN 1 AND 120 THEN
        RAISE EXCEPTION 'parametros invalidos para reserva de automacoes';
    END IF;

    FOR candidata IN
        SELECT e.id, e.tenant_id, e.definition_id, e.status
          FROM automation_execution e
         WHERE (e.status = 'RUNNING'
                AND (e.lease_until IS NULL OR e.lease_until <= now()))
            OR (e.status = 'QUEUED')
         ORDER BY CASE WHEN e.status = 'RUNNING' THEN 0 ELSE 1 END,
                  e.created_at, e.id
         LIMIT LEAST(p_limite * 8, 4000)
         FOR UPDATE SKIP LOCKED
    LOOP
        EXIT WHEN reservadas >= p_limite;

        IF candidata.status = 'QUEUED' THEN
            SELECT count(*) INTO ativas_tenant
              FROM automation_execution
             WHERE tenant_id = candidata.tenant_id AND status = 'RUNNING';
            SELECT count(*) INTO ativas_definicao
              FROM automation_execution
             WHERE tenant_id = candidata.tenant_id
               AND definition_id = candidata.definition_id
               AND status = 'RUNNING';
            SELECT concurrency_limit INTO limite_definicao
              FROM automation_definition
             WHERE tenant_id = candidata.tenant_id AND id = candidata.definition_id;

            IF ativas_tenant >= p_max_por_tenant
               OR ativas_definicao >= limite_definicao THEN
                CONTINUE;
            END IF;
        END IF;

        UPDATE automation_execution
           SET status = 'RUNNING',
               started_at = COALESCE(started_at, now()),
               lease_owner = p_worker,
               lease_until = now() + make_interval(secs => p_lease_seconds)
         WHERE id = candidata.id;

        execution_id := candidata.id;
        execution_tenant_id := candidata.tenant_id;
        reservadas := reservadas + 1;
        RETURN NEXT;
    END LOOP;
END;
$$;

REVOKE EXECUTE ON FUNCTION automation_immutable() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION reservar_execucoes_automacao(integer, integer, integer, text)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reservar_execucoes_automacao(integer, integer, integer, text)
    TO "${runtime_role}";

COMMENT ON TABLE automation_definition_version IS
    'Snapshot imutavel; toda execucao referencia exatamente uma versao.';
COMMENT ON TABLE automation_transition IS
    'Trilha append-only sanitizada: somente estados e codigos, sem payload ou mensagem.';
COMMENT ON COLUMN automation_execution.trigger_key IS
    'Chave tecnica idempotente; nao deve conter payload nem segredo.';
