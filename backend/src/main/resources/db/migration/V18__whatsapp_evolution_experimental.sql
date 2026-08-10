-- Adaptador experimental da Evolution API, restrito a desenvolvimento.
-- O canal oficial de producao continua sendo WHATSAPP_CLOUD.

ALTER TABLE channel_connection
    DROP CONSTRAINT channel_connection_kind_valido,
    ADD CONSTRAINT channel_connection_kind_valido CHECK (
        kind IN ('LIVE_CHAT', 'TELEGRAM', 'WHATSAPP_CLOUD',
                 'WHATSAPP_EVOLUTION', 'INSTAGRAM')
    );

ALTER TABLE channel_credential
    DROP CONSTRAINT channel_credential_kind_valido,
    ADD CONSTRAINT channel_credential_kind_valido CHECK (
        kind IN ('TELEGRAM_BOT_TOKEN', 'TELEGRAM_WEBHOOK_SECRET',
                 'META_ACCESS_TOKEN', 'META_APP_SECRET',
                 'EVOLUTION_WEBHOOK_SECRET')
    );

CREATE INDEX channel_connection_reconciliacao_evolution
    ON channel_connection (last_reconciled_at NULLS FIRST, id)
    WHERE kind = 'WHATSAPP_EVOLUTION' AND active AND deleted_at IS NULL;

CREATE FUNCTION reservar_canais_evolution_para_reconciliar(
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
     WHERE kind = 'WHATSAPP_EVOLUTION'
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

REVOKE EXECUTE ON FUNCTION reservar_canais_evolution_para_reconciliar(integer, integer)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reservar_canais_evolution_para_reconciliar(integer, integer)
    TO "${runtime_role}";

COMMENT ON FUNCTION reservar_canais_evolution_para_reconciliar(integer, integer) IS
    'Reserva somente canais Evolution experimentais ativos para reconciliacao local.';
