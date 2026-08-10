-- Estado remoto normalizado e reserva multitenant para reconciliacao periodica.

ALTER TABLE channel_connection
    ADD COLUMN remote_status text,
    ADD COLUMN remote_pending_count integer,
    ADD COLUMN last_reconciled_at timestamptz,
    ADD COLUMN last_remote_error_at timestamptz,
    ADD CONSTRAINT channel_connection_remote_status_valido CHECK (
        remote_status IS NULL OR remote_status IN ('CHECKING', 'HEALTHY', 'DEGRADED', 'REPAIRED', 'ERROR')
    ),
    ADD CONSTRAINT channel_connection_remote_pending_nao_negativo CHECK (
        remote_pending_count IS NULL OR remote_pending_count >= 0
    );

CREATE INDEX channel_connection_reconciliacao
    ON channel_connection (last_reconciled_at NULLS FIRST, id)
    WHERE kind = 'TELEGRAM' AND active AND deleted_at IS NULL;

CREATE FUNCTION reservar_canais_telegram_para_reconciliar(
    p_limite integer,
    p_intervalo_segundos integer
) RETURNS TABLE (connection_id uuid, connection_tenant_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatos AS (
    SELECT id
      FROM channel_connection
     WHERE kind = 'TELEGRAM'
       AND active
       AND deleted_at IS NULL
       AND (last_reconciled_at IS NULL
            OR last_reconciled_at <= now() - p_intervalo_segundos * interval '1 second')
     ORDER BY last_reconciled_at NULLS FIRST, id
     LIMIT p_limite
     FOR UPDATE SKIP LOCKED
)
UPDATE channel_connection c
   SET last_reconciled_at = now(), remote_status = 'CHECKING'
  FROM candidatos x
 WHERE c.id = x.id
RETURNING c.id, c.tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION reservar_canais_telegram_para_reconciliar(integer, integer)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reservar_canais_telegram_para_reconciliar(integer, integer)
    TO "${runtime_role}";

COMMENT ON FUNCTION reservar_canais_telegram_para_reconciliar(integer, integer) IS
    'Reserva somente ids de conexoes Telegram ativas. O estado remoto e lido depois sob RLS.';
