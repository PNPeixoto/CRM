-- Entitlements configuraveis e medicao reconciliavel.
--
-- Esta migration cria mecanismo tecnico, nao politica comercial. Nao ha plano,
-- preco, quantidade incluida nem concessao automatica para tenant algum. O
-- contrato informa explicitamente vigencia, janela, timezone, limites e
-- eventual carencia. Autorizacao de usuario e visibilidade de menu continuam
-- fora deste modulo.

-- ---------------------------------------------------------------------------
-- Catalogo tecnico publicado pelo backend
-- ---------------------------------------------------------------------------

CREATE TABLE technical_capability
(
    code            text        PRIMARY KEY,
    module_code     text        NOT NULL,
    catalog_version integer     NOT NULL,
    available       boolean     NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT technical_capability_codigo_valido
        CHECK (code ~ '^[A-Z][A-Z0-9_]{2,79}$'),
    CONSTRAINT technical_capability_modulo_valido
        CHECK (module_code ~ '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT technical_capability_versao_positiva
        CHECK (catalog_version > 0)
);

CREATE TABLE usage_metric
(
    code            text        PRIMARY KEY,
    capability_code text        NOT NULL REFERENCES technical_capability (code),
    unit            text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usage_metric_codigo_valido
        CHECK (code ~ '^[A-Z][A-Z0-9_]{2,79}$'),
    CONSTRAINT usage_metric_unidade_valida
        CHECK (unit ~ '^[a-z][a-z0-9_]{1,31}$'),
    CONSTRAINT usage_metric_codigo_unidade_unicos UNIQUE (code, unit)
);

-- Estas sao metricas tecnicas que o backend ja media desde V15/V17. O seed
-- nao concede uso nem define se qualquer delas sera faturavel.
INSERT INTO technical_capability (code, module_code, catalog_version, available)
VALUES ('OMNICHANNEL_MESSAGING', 'channel', 1, true);

INSERT INTO usage_metric (code, capability_code, unit)
VALUES ('MESSAGE_INBOUND_ACCEPTED', 'OMNICHANNEL_MESSAGING', 'message'),
       ('MESSAGE_OUTBOUND_SENT', 'OMNICHANNEL_MESSAGING', 'message'),
       ('MEDIA_BYTES_STORED', 'OMNICHANNEL_MESSAGING', 'byte');

-- Catalogo tecnico muda por migration/release, nunca por uma requisicao do
-- tenant. O runtime apenas o consulta.
REVOKE INSERT, UPDATE, DELETE ON technical_capability FROM "${runtime_role}";
REVOKE INSERT, UPDATE, DELETE ON usage_metric FROM "${runtime_role}";

-- ---------------------------------------------------------------------------
-- Concessao contratual versionada
-- ---------------------------------------------------------------------------

CREATE TABLE entitlement_grant
(
    id                     uuid        PRIMARY KEY,
    tenant_id              uuid        NOT NULL REFERENCES tenant (id),
    capability_code        text        NOT NULL REFERENCES technical_capability (code),
    version_number         integer     NOT NULL,
    contract_reference     text        NOT NULL,
    valid_from             timestamptz NOT NULL,
    valid_until            timestamptz,
    window_type            text        NOT NULL,
    time_zone              text        NOT NULL,
    soft_limit             bigint,
    hard_limit             bigint,
    hard_limit_grace_until timestamptz,
    created_at             timestamptz NOT NULL DEFAULT now(),
    created_by             uuid,
    CONSTRAINT entitlement_grant_tenant_id_unico UNIQUE (tenant_id, id),
    CONSTRAINT entitlement_grant_versao_unica
        UNIQUE (tenant_id, capability_code, version_number),
    CONSTRAINT entitlement_grant_versao_positiva CHECK (version_number > 0),
    CONSTRAINT entitlement_grant_referencia_valida
        CHECK (length(trim(contract_reference)) BETWEEN 1 AND 200),
    CONSTRAINT entitlement_grant_vigencia_valida
        CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT entitlement_grant_janela_valida
        CHECK (window_type IN ('CALENDAR_DAY', 'CALENDAR_MONTH', 'CONTRACT_TERM')),
    CONSTRAINT entitlement_grant_timezone_valido
        CHECK (length(trim(time_zone)) BETWEEN 1 AND 100),
    CONSTRAINT entitlement_grant_soft_positivo CHECK (soft_limit IS NULL OR soft_limit > 0),
    CONSTRAINT entitlement_grant_hard_positivo CHECK (hard_limit IS NULL OR hard_limit > 0),
    CONSTRAINT entitlement_grant_limites_coerentes
        CHECK (soft_limit IS NULL OR hard_limit IS NULL OR soft_limit <= hard_limit),
    CONSTRAINT entitlement_grant_carencia_coerente CHECK (
        hard_limit_grace_until IS NULL
        OR (hard_limit IS NOT NULL
            AND hard_limit_grace_until > valid_from
            AND (valid_until IS NULL OR hard_limit_grace_until <= valid_until))
    )
);

CREATE INDEX entitlement_grant_vigencia
    ON entitlement_grant (tenant_id, capability_code, valid_from, valid_until);

ALTER TABLE entitlement_grant ENABLE ROW LEVEL SECURITY;
ALTER TABLE entitlement_grant FORCE ROW LEVEL SECURITY;
CREATE POLICY entitlement_grant_isolamento ON entitlement_grant
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- Serializa a criacao por tenant/capacidade e impede duas politicas vigentes
-- para o mesmo instante. Assim um evento atrasado encontra uma unica versao.
CREATE FUNCTION validar_nova_concessao() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = NEW.time_zone) THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'ENTITLEMENT_TIMEZONE_INVALID';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended(
        NEW.tenant_id::text || ':entitlement:' || NEW.capability_code, 0));

    IF EXISTS (
        SELECT 1 FROM entitlement_grant g
         WHERE g.tenant_id = NEW.tenant_id
           AND g.capability_code = NEW.capability_code
           AND g.valid_from < COALESCE(NEW.valid_until, 'infinity'::timestamptz)
           AND COALESCE(g.valid_until, 'infinity'::timestamptz) > NEW.valid_from
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23P01', MESSAGE = 'ENTITLEMENT_VALIDITY_OVERLAP';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER entitlement_grant_validar_insert
    BEFORE INSERT ON entitlement_grant
    FOR EACH ROW EXECUTE FUNCTION validar_nova_concessao();

CREATE FUNCTION proteger_concessao_historica() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION 'entitlement_grant e historico versionado; insira nova versao';
END;
$$;

CREATE TRIGGER entitlement_grant_sem_update_delete
    BEFORE UPDATE OR DELETE ON entitlement_grant
    FOR EACH ROW EXECUTE FUNCTION proteger_concessao_historica();

REVOKE UPDATE, DELETE ON entitlement_grant FROM "${runtime_role}";
REVOKE EXECUTE ON FUNCTION validar_nova_concessao() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION validar_nova_concessao() FROM "${runtime_role}";
REVOKE EXECUTE ON FUNCTION proteger_concessao_historica() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION proteger_concessao_historica() FROM "${runtime_role}";

-- ---------------------------------------------------------------------------
-- Evolucao compativel do livro-razao de uso
-- ---------------------------------------------------------------------------

ALTER TABLE usage_event
    DROP CONSTRAINT usage_event_tipo_valido,
    ALTER COLUMN channel_connection_id DROP NOT NULL,
    ADD COLUMN source_type text,
    ADD COLUMN source_id text,
    ADD COLUMN entitlement_grant_id uuid;

-- Banco vivo ja tem ledger e o gatilho da V15 recusa UPDATE inclusive para o
-- dono. A abertura e local a esta transacao e desaparece ao fim da migration;
-- depois do backfill a funcao e fechada novamente mais abaixo.
CREATE OR REPLACE FUNCTION usage_event_imutavel() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND current_setting('app.usage_migration_mode', true) = 'controlled' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'usage_event e append-only';
END;
$$;

SELECT set_config('app.usage_migration_mode', 'controlled', true);

UPDATE usage_event
   SET source_type = CASE WHEN message_id IS NOT NULL THEN 'MESSAGE' ELSE 'CHANNEL_MEDIA' END,
       source_id = CASE
           WHEN message_id IS NOT NULL THEN message_id::text
           ELSE split_part(idempotency_key, ':', 2)
       END;

SELECT set_config('app.usage_migration_mode', '', true);

ALTER TABLE usage_event
    ALTER COLUMN source_type SET NOT NULL,
    ALTER COLUMN source_id SET NOT NULL,
    ADD CONSTRAINT usage_event_fonte_tipo_valido
        CHECK (source_type ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    ADD CONSTRAINT usage_event_fonte_id_valida
        CHECK (length(trim(source_id)) BETWEEN 1 AND 200),
    ADD CONSTRAINT usage_event_metrica_catalogada
        FOREIGN KEY (event_type, unit) REFERENCES usage_metric (code, unit),
    ADD CONSTRAINT usage_event_concessao_mesmo_tenant
        FOREIGN KEY (tenant_id, entitlement_grant_id)
        REFERENCES entitlement_grant (tenant_id, id);

CREATE INDEX usage_event_por_concessao_e_janela
    ON usage_event (tenant_id, entitlement_grant_id, occurred_at, event_type)
    WHERE entitlement_grant_id IS NOT NULL;

-- UPDATE nunca e valido. DELETE existe somente para a operacao controlada de
-- retencao criada em V23; fora desse modo, o ledger continua append-only.
CREATE OR REPLACE FUNCTION usage_event_imutavel() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP = 'DELETE'
       AND current_setting('app.usage_retention_mode', true) = 'controlled' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'usage_event e append-only';
END;
$$;

CREATE OR REPLACE FUNCTION expurgar_telemetria(
    p_corte  timestamptz,
    p_limite integer
) RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    v_total integer;
BEGIN
    PERFORM set_config('app.usage_retention_mode', 'controlled', true);
    DELETE FROM usage_event u
     WHERE u.tenant_id = current_tenant_id()
       AND u.id IN (
           SELECT id FROM usage_event
            WHERE tenant_id = current_tenant_id()
              AND occurred_at < p_corte
              AND NOT ha_legal_hold('USAGE_EVENT', id, p_corte)
            ORDER BY occurred_at
            LIMIT p_limite);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    PERFORM set_config('app.usage_retention_mode', '', true);
    RETURN v_total;
END;
$$;

REVOKE INSERT, UPDATE, DELETE ON usage_event FROM "${runtime_role}";

-- Os produtores existentes continuam medindo mesmo sem concessao. Quando ha
-- contrato vigente, o evento recebe o snapshot da versao correspondente ao
-- instante da ocorrencia; por isso evento atrasado nao cai no plano atual.
CREATE OR REPLACE FUNCTION medir_uso_de_mensagem() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    v_tipo text;
    v_instante timestamptz;
    v_concessao_id uuid;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.direction = 'INBOUND' THEN
        v_tipo := 'MESSAGE_INBOUND_ACCEPTED';
        v_instante := COALESCE(NEW.sent_at, NEW.created_at, now());
    ELSIF TG_OP = 'UPDATE'
       AND NEW.direction = 'OUTBOUND'
       AND NEW.status = 'SENT'
       AND OLD.status IS DISTINCT FROM 'SENT' THEN
        v_tipo := 'MESSAGE_OUTBOUND_SENT';
        v_instante := COALESCE(NEW.sent_at, now());
    ELSE
        RETURN NEW;
    END IF;

    SELECT g.id INTO v_concessao_id
      FROM entitlement_grant g
      JOIN usage_metric m ON m.capability_code = g.capability_code
     WHERE g.tenant_id = NEW.tenant_id
       AND m.code = v_tipo
       AND g.valid_from <= v_instante
       AND (g.valid_until IS NULL OR g.valid_until > v_instante)
     ORDER BY g.version_number DESC
     LIMIT 1;

    INSERT INTO usage_event (
        id, tenant_id, channel_connection_id, message_id, event_type,
        quantity, unit, idempotency_key, occurred_at, source_type, source_id,
        entitlement_grant_id
    ) VALUES (
        gen_random_uuid(), NEW.tenant_id, NEW.channel_connection_id, NEW.id,
        v_tipo, 1, 'message', concat(lower(v_tipo), ':', NEW.id), v_instante,
        'MESSAGE', NEW.id::text, v_concessao_id
    ) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION medir_uso_de_midia() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    v_concessao_id uuid;
BEGIN
    SELECT g.id INTO v_concessao_id
      FROM entitlement_grant g
      JOIN usage_metric m ON m.capability_code = g.capability_code
     WHERE g.tenant_id = NEW.tenant_id
       AND m.code = 'MEDIA_BYTES_STORED'
       AND g.valid_from <= NEW.created_at
       AND (g.valid_until IS NULL OR g.valid_until > NEW.created_at)
     ORDER BY g.version_number DESC
     LIMIT 1;

    INSERT INTO usage_event (
        id, tenant_id, channel_connection_id, message_id, event_type,
        quantity, unit, idempotency_key, occurred_at, source_type, source_id,
        entitlement_grant_id
    ) VALUES (
        gen_random_uuid(), NEW.tenant_id, NEW.channel_connection_id, NULL,
        'MEDIA_BYTES_STORED', NEW.byte_size, 'byte',
        concat('media_bytes_stored:', NEW.id), NEW.created_at,
        'CHANNEL_MEDIA', NEW.id::text, v_concessao_id
    ) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING;
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- Registro atomico quando a capacidade exige preflight de hard limit
-- ---------------------------------------------------------------------------

CREATE FUNCTION registrar_evento_de_uso(
    p_event_id        uuid,
    p_metric_code     text,
    p_quantity        bigint,
    p_source_type     text,
    p_source_id       text,
    p_idempotency_key text,
    p_occurred_at     timestamptz
) RETURNS TABLE (
    usage_event_id       uuid,
    outcome              text,
    entitlement_grant_id uuid,
    window_started_at    timestamptz,
    window_ended_at      timestamptz,
    total_quantity       bigint
)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    v_tenant_id uuid;
    v_existing_id uuid;
    v_existing_grant_id uuid;
    v_capability_code text;
    v_unit text;
    v_available boolean;
    v_grant entitlement_grant%ROWTYPE;
    v_window_start timestamptz;
    v_window_end timestamptz;
    v_total bigint;
    v_outcome text;
BEGIN
    v_tenant_id := current_tenant_id();
    IF v_tenant_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'TENANT_CONTEXT_REQUIRED';
    END IF;
    IF p_event_id IS NULL OR p_occurred_at IS NULL OR p_quantity IS NULL OR p_quantity <= 0 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'USAGE_EVENT_INVALID';
    END IF;
    IF p_idempotency_key IS NULL OR length(trim(p_idempotency_key)) NOT BETWEEN 1 AND 200
       OR p_source_id IS NULL OR length(trim(p_source_id)) NOT BETWEEN 1 AND 200
       OR p_source_type IS NULL OR p_source_type !~ '^[A-Z][A-Z0-9_]{1,63}$' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'USAGE_SOURCE_INVALID';
    END IF;

    -- A primeira trava fecha replay concorrente ate quando duas chamadas
    -- tentam associar a mesma chave a metricas diferentes.
    PERFORM pg_advisory_xact_lock(hashtextextended(
        v_tenant_id::text || ':usage-key:' || p_idempotency_key, 0));

    SELECT u.id, u.entitlement_grant_id
      INTO v_existing_id, v_existing_grant_id
      FROM usage_event u
     WHERE u.tenant_id = v_tenant_id
       AND u.idempotency_key = p_idempotency_key;
    IF FOUND THEN
        RETURN QUERY SELECT v_existing_id, 'REPLAY'::text, v_existing_grant_id,
                            NULL::timestamptz, NULL::timestamptz, NULL::bigint;
        RETURN;
    END IF;

    SELECT m.capability_code, m.unit, c.available
      INTO v_capability_code, v_unit, v_available
      FROM usage_metric m
      JOIN technical_capability c ON c.code = m.capability_code
     WHERE m.code = p_metric_code;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'USAGE_METRIC_UNKNOWN';
    END IF;
    IF NOT v_available THEN
        RETURN QUERY SELECT NULL::uuid, 'MODULE_UNAVAILABLE'::text, NULL::uuid,
                            NULL::timestamptz, NULL::timestamptz, NULL::bigint;
        RETURN;
    END IF;

    SELECT g.* INTO v_grant
      FROM entitlement_grant g
     WHERE g.tenant_id = v_tenant_id
       AND g.capability_code = v_capability_code
       AND g.valid_from <= p_occurred_at
       AND (g.valid_until IS NULL OR g.valid_until > p_occurred_at)
     ORDER BY g.version_number DESC
     LIMIT 1;
    IF NOT FOUND THEN
        RETURN QUERY SELECT NULL::uuid, 'NOT_CONTRACTED'::text, NULL::uuid,
                            NULL::timestamptz, NULL::timestamptz, NULL::bigint;
        RETURN;
    END IF;

    IF v_grant.window_type = 'CALENDAR_DAY' THEN
        v_window_start := date_trunc(
            'day', p_occurred_at AT TIME ZONE v_grant.time_zone)
            AT TIME ZONE v_grant.time_zone;
        v_window_end := (date_trunc(
            'day', p_occurred_at AT TIME ZONE v_grant.time_zone) + interval '1 day')
            AT TIME ZONE v_grant.time_zone;
    ELSIF v_grant.window_type = 'CALENDAR_MONTH' THEN
        v_window_start := date_trunc(
            'month', p_occurred_at AT TIME ZONE v_grant.time_zone)
            AT TIME ZONE v_grant.time_zone;
        v_window_end := (date_trunc(
            'month', p_occurred_at AT TIME ZONE v_grant.time_zone) + interval '1 month')
            AT TIME ZONE v_grant.time_zone;
    ELSE
        v_window_start := v_grant.valid_from;
        v_window_end := v_grant.valid_until;
    END IF;

    -- Trava da cota: soma e insert fazem parte da mesma secao critica. Nao ha
    -- contador mutavel; depois da trava a verdade continua sendo o ledger.
    PERFORM pg_advisory_xact_lock(hashtextextended(
        v_tenant_id::text || ':usage-window:' || v_grant.id::text || ':'
        || p_metric_code || ':' || v_window_start::text, 0));

    SELECT COALESCE(sum(u.quantity), 0) INTO v_total
      FROM usage_event u
     WHERE u.tenant_id = v_tenant_id
       AND u.entitlement_grant_id = v_grant.id
       AND u.event_type = p_metric_code
       AND u.occurred_at >= v_window_start
       AND (v_window_end IS NULL OR u.occurred_at < v_window_end);

    IF v_grant.hard_limit IS NOT NULL
       AND v_total + p_quantity > v_grant.hard_limit
       AND (v_grant.hard_limit_grace_until IS NULL
            OR p_occurred_at >= v_grant.hard_limit_grace_until) THEN
        RETURN QUERY SELECT NULL::uuid, 'HARD_LIMIT_EXCEEDED'::text, v_grant.id,
                            v_window_start, v_window_end, v_total;
        RETURN;
    END IF;

    IF v_grant.hard_limit IS NOT NULL
       AND v_total + p_quantity > v_grant.hard_limit THEN
        v_outcome := 'HARD_LIMIT_GRACE';
    ELSIF v_grant.soft_limit IS NOT NULL
       AND v_total + p_quantity > v_grant.soft_limit THEN
        v_outcome := 'SOFT_LIMIT_EXCEEDED';
    ELSE
        v_outcome := 'RECORDED';
    END IF;

    INSERT INTO usage_event (
        id, tenant_id, channel_connection_id, message_id, event_type,
        quantity, unit, idempotency_key, occurred_at, source_type, source_id,
        entitlement_grant_id
    ) VALUES (
        p_event_id, v_tenant_id, NULL, NULL, p_metric_code, p_quantity, v_unit,
        p_idempotency_key, p_occurred_at, p_source_type, p_source_id, v_grant.id
    );

    v_total := v_total + p_quantity;
    RETURN QUERY SELECT p_event_id, v_outcome, v_grant.id,
                        v_window_start, v_window_end, v_total;
END;
$$;

REVOKE EXECUTE ON FUNCTION registrar_evento_de_uso(
    uuid, text, bigint, text, text, text, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION registrar_evento_de_uso(
    uuid, text, bigint, text, text, text, timestamptz) TO "${runtime_role}";

REVOKE EXECUTE ON FUNCTION medir_uso_de_mensagem() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION medir_uso_de_mensagem() FROM "${runtime_role}";
REVOKE EXECUTE ON FUNCTION medir_uso_de_midia() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION medir_uso_de_midia() FROM "${runtime_role}";

COMMENT ON TABLE technical_capability IS
    'Catalogo tecnico do build; disponibilidade nao equivale a contratacao nem permissao.';
COMMENT ON TABLE entitlement_grant IS
    'Snapshot contratual versionado e imutavel, sem preco ou status de pagamento.';
COMMENT ON COLUMN entitlement_grant.hard_limit_grace_until IS
    'Ate este instante, exceder hard limit mede e sinaliza carencia; depois, recusa atomicamente.';
COMMENT ON TABLE usage_event IS
    'Livro-razao append-only, idempotente e reconciliavel por fonte, metrica, concessao e janela.';
