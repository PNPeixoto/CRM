-- Copia propria de midia do provedor, fora do web root e em quarentena.

CREATE TABLE channel_media
(
    id                    uuid PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    channel_connection_id uuid        NOT NULL,
    external_file_id      text        NOT NULL,
    storage_key           text,
    declared_mime_type    text,
    detected_mime_type    text        NOT NULL,
    byte_size             bigint      NOT NULL,
    sha256                varchar(64) NOT NULL,
    status                text        NOT NULL,
    retain_until          timestamptz NOT NULL DEFAULT (now() + interval '30 days'),
    purge_lease_until     timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    deleted_at            timestamptz,
    CONSTRAINT channel_media_canal_mesmo_tenant
        FOREIGN KEY (tenant_id, channel_connection_id)
        REFERENCES channel_connection (tenant_id, id),
    CONSTRAINT channel_media_tamanho_valido CHECK (byte_size BETWEEN 1 AND 20971520),
    CONSTRAINT channel_media_status_valido CHECK (
        status IN ('QUARANTINED', 'AVAILABLE', 'PURGING', 'PURGED')
    ),
    CONSTRAINT channel_media_storage_consistente CHECK (
        (status = 'PURGED' AND storage_key IS NULL)
        OR (status <> 'PURGED' AND storage_key IS NOT NULL)
    )
);

CREATE UNIQUE INDEX channel_media_idempotencia
    ON channel_media (channel_connection_id, external_file_id)
    WHERE deleted_at IS NULL;
CREATE INDEX channel_media_retencao
    ON channel_media (retain_until, purge_lease_until, id)
    WHERE status IN ('QUARANTINED', 'AVAILABLE', 'PURGING') AND deleted_at IS NULL;

CREATE TRIGGER channel_media_updated_at
    BEFORE UPDATE ON channel_media
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE channel_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE channel_media FORCE ROW LEVEL SECURITY;
CREATE POLICY channel_media_isolamento ON channel_media
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE FUNCTION medir_uso_de_midia() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    INSERT INTO usage_event (
        id, tenant_id, channel_connection_id, message_id, event_type,
        quantity, unit, idempotency_key, occurred_at
    ) VALUES (
        gen_random_uuid(), NEW.tenant_id, NEW.channel_connection_id, NULL,
        'MEDIA_BYTES_STORED', NEW.byte_size, 'byte',
        concat('media_bytes_stored:', NEW.id), NEW.created_at
    ) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE TRIGGER channel_media_medir_uso
    AFTER INSERT ON channel_media
    FOR EACH ROW EXECUTE FUNCTION medir_uso_de_midia();

CREATE FUNCTION reservar_midias_expiradas(p_limite integer)
RETURNS TABLE (media_id uuid, media_tenant_id uuid, media_storage_key text)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatas AS (
    SELECT id
      FROM channel_media
     WHERE ((status IN ('QUARANTINED', 'AVAILABLE') AND retain_until <= now())
         OR (status = 'PURGING' AND purge_lease_until <= now()))
       AND deleted_at IS NULL
     ORDER BY retain_until, id
     LIMIT p_limite
     FOR UPDATE SKIP LOCKED
)
UPDATE channel_media m
   SET status = 'PURGING', purge_lease_until = now() + interval '10 minutes'
  FROM candidatas c
 WHERE m.id = c.id
RETURNING m.id, m.tenant_id, m.storage_key;
$$;

REVOKE EXECUTE ON FUNCTION medir_uso_de_midia() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION reservar_midias_expiradas(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reservar_midias_expiradas(integer) TO "${runtime_role}";
