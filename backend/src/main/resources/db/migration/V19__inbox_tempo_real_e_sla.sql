-- Inbox paginada, stream recuperavel e SLA persistido.

ALTER TABLE tenant_profile
    ADD COLUMN time_zone text NOT NULL DEFAULT 'America/Sao_Paulo',
    ADD CONSTRAINT tenant_profile_time_zone_formato CHECK (
        time_zone ~ '^[A-Za-z_]+(/[A-Za-z0-9_+\-]+)+$'
    );

ALTER TABLE conversation
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN due_at timestamptz,
    ADD COLUMN sla_started_at timestamptz,
    ADD COLUMN sla_breach_reserved_until timestamptz;

ALTER TABLE message
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

CREATE INDEX conversation_inbox_keyset
    ON conversation (tenant_id, last_message_at DESC NULLS LAST, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX conversation_sla_due
    ON conversation (due_at, sla_breach_reserved_until, id)
    WHERE due_at IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE realtime_stream_cursor
(
    tenant_id       uuid PRIMARY KEY REFERENCES tenant (id),
    last_sequence   bigint      NOT NULL DEFAULT 0,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT realtime_stream_cursor_sequence_valida CHECK (last_sequence >= 0)
);

CREATE TABLE realtime_event
(
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    sequence         bigint      NOT NULL,
    event_type       text        NOT NULL,
    conversation_id  uuid        NOT NULL,
    message_id       uuid,
    resource_version bigint      NOT NULL DEFAULT 0,
    occurred_at      timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT realtime_event_sequence_valida CHECK (sequence > 0),
    CONSTRAINT realtime_event_versao_valida CHECK (resource_version >= 0),
    CONSTRAINT realtime_event_tipo_valido CHECK (event_type IN (
        'MESSAGE_CREATED', 'MESSAGE_STATUS_CHANGED', 'SLA_CHANGED'
    )),
    CONSTRAINT realtime_event_conversa_mesmo_tenant
        FOREIGN KEY (tenant_id, conversation_id)
        REFERENCES conversation (tenant_id, id),
    CONSTRAINT realtime_event_mensagem_mesmo_tenant
        FOREIGN KEY (tenant_id, message_id)
        REFERENCES message (tenant_id, id),
    CONSTRAINT realtime_event_sequence_unica UNIQUE (tenant_id, sequence)
);

CREATE INDEX realtime_event_recuperacao
    ON realtime_event (tenant_id, sequence);

CREATE TABLE conversation_sla_event
(
    id               uuid PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    conversation_id  uuid        NOT NULL,
    event_type       text        NOT NULL,
    due_at           timestamptz NOT NULL,
    occurred_at      timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversation_sla_event_tipo_valido CHECK (
        event_type IN ('STARTED', 'SATISFIED', 'BREACHED')
    ),
    CONSTRAINT conversation_sla_event_conversa_mesmo_tenant
        FOREIGN KEY (tenant_id, conversation_id)
        REFERENCES conversation (tenant_id, id),
    CONSTRAINT conversation_sla_event_idempotente
        UNIQUE (tenant_id, conversation_id, event_type, due_at)
);

CREATE INDEX conversation_sla_event_timeline
    ON conversation_sla_event (tenant_id, conversation_id, occurred_at);

ALTER TABLE realtime_stream_cursor ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_stream_cursor FORCE ROW LEVEL SECURITY;
CREATE POLICY realtime_stream_cursor_isolamento ON realtime_stream_cursor
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE realtime_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE realtime_event FORCE ROW LEVEL SECURITY;
CREATE POLICY realtime_event_isolamento ON realtime_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE conversation_sla_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_sla_event FORCE ROW LEVEL SECURITY;
CREATE POLICY conversation_sla_event_isolamento ON conversation_sla_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE FUNCTION next_realtime_sequence(p_tenant_id uuid) RETURNS bigint
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
DECLARE
    proxima bigint;
BEGIN
    IF p_tenant_id IS DISTINCT FROM current_tenant_id() THEN
        RAISE EXCEPTION 'tenant do stream nao corresponde ao contexto';
    END IF;

    INSERT INTO realtime_stream_cursor (tenant_id, last_sequence)
    VALUES (p_tenant_id, 1)
    ON CONFLICT (tenant_id) DO UPDATE
       SET last_sequence = realtime_stream_cursor.last_sequence + 1,
           updated_at = now()
    RETURNING last_sequence INTO proxima;
    RETURN proxima;
END;
$$;

CREATE FUNCTION realtime_event_immutable() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION 'realtime_event e append-only';
END;
$$;

CREATE TRIGGER realtime_event_sem_update_delete
    BEFORE UPDATE OR DELETE ON realtime_event
    FOR EACH ROW EXECUTE FUNCTION realtime_event_immutable();

CREATE FUNCTION conversation_sla_event_immutable() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION 'conversation_sla_event e append-only';
END;
$$;

CREATE TRIGGER conversation_sla_event_sem_update_delete
    BEFORE UPDATE OR DELETE ON conversation_sla_event
    FOR EACH ROW EXECUTE FUNCTION conversation_sla_event_immutable();

-- Reserva estreita para o verificador cross-tenant. O processamento posterior
-- volta ao contexto RLS de cada tenant e a tabela de evento elimina repeticao.
CREATE FUNCTION reservar_slas_vencidos(p_limite integer)
RETURNS TABLE (conversation_id uuid, conversation_tenant_id uuid, conversation_due_at timestamptz)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatas AS (
    SELECT id
      FROM conversation
     WHERE due_at <= now()
       AND (sla_breach_reserved_until IS NULL OR sla_breach_reserved_until <= now())
       AND deleted_at IS NULL
     ORDER BY due_at, id
     LIMIT p_limite
     FOR UPDATE SKIP LOCKED
)
UPDATE conversation c
   SET sla_breach_reserved_until = now() + interval '5 minutes'
  FROM candidatas x
 WHERE c.id = x.id
RETURNING c.id, c.tenant_id, c.due_at;
$$;

REVOKE EXECUTE ON FUNCTION next_realtime_sequence(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION realtime_event_immutable() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION conversation_sla_event_immutable() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION reservar_slas_vencidos(integer) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION next_realtime_sequence(uuid) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION reservar_slas_vencidos(integer) TO "${runtime_role}";

COMMENT ON TABLE realtime_event IS
    'Stream tenant-scoped append-only sem conteudo de mensagem, usado para recuperar lacunas do WebSocket.';
COMMENT ON COLUMN tenant_profile.time_zone IS
    'Fuso IANA do tenant usado em prazos operacionais; unidade podera sobrescrever quando tiver configuracao propria.';
