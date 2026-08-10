-- Completa a fundacao omnichannel com protecao do payload bruto, lease
-- observavel, fila morta explicita e medicao append-only.

-- ---------------------------------------------------------------------------
-- Payload bruto, lease e fila morta
-- ---------------------------------------------------------------------------

ALTER TABLE inbound_event
    ALTER COLUMN payload DROP NOT NULL,
    ADD COLUMN payload_ciphertext text,
    ADD COLUMN payload_sha256 varchar(64),
    ADD COLUMN payload_retain_until timestamptz NOT NULL DEFAULT (now() + interval '7 days'),
    ADD COLUMN payload_purged_at timestamptz,
    ADD COLUMN lease_owner text,
    ADD COLUMN lease_until timestamptz,
    ADD COLUMN dead_lettered_at timestamptz,
    ADD CONSTRAINT inbound_event_payload_disponivel CHECK (
        payload IS NOT NULL OR payload_ciphertext IS NOT NULL OR payload_purged_at IS NOT NULL
    );

COMMENT ON COLUMN inbound_event.payload IS
    'Compatibilidade com eventos anteriores a V15. Novos eventos usam somente payload_ciphertext.';
COMMENT ON COLUMN inbound_event.payload_ciphertext IS
    'Bytes UTF-8 exatos recebidos, cifrados em AES-256-GCM pela aplicacao.';
COMMENT ON COLUMN inbound_event.payload_sha256 IS
    'Hash para diagnostico/idempotencia depois do expurgo do corpo bruto.';

ALTER TABLE message
    ADD COLUMN lease_owner text,
    ADD COLUMN lease_until timestamptz,
    ADD COLUMN dead_lettered_at timestamptz,
    ADD CONSTRAINT message_tenant_id_unico UNIQUE (tenant_id, id);

CREATE INDEX inbound_event_fila_morta
    ON inbound_event (dead_lettered_at, received_at)
    WHERE dead_lettered_at IS NOT NULL AND processed_at IS NULL AND deleted_at IS NULL;

CREATE INDEX message_fila_morta
    ON message (dead_lettered_at, created_at)
    WHERE dead_lettered_at IS NOT NULL AND direction = 'OUTBOUND' AND deleted_at IS NULL;

-- Mantem a assinatura existente para permitir deploy expand/contract. O owner
-- identifica a sessao que reservou e o lease tem a mesma janela do backoff.
CREATE OR REPLACE FUNCTION reservar_mensagens_para_envio(
    p_limite integer,
    p_max_tentativas integer,
    p_espera_segundos integer
) RETURNS TABLE (message_id uuid, message_tenant_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatas AS (SELECT id
                    FROM message
                    WHERE direction = 'OUTBOUND'
                      AND status IN ('PENDING', 'FAILED')
                      AND attempt_count < p_max_tentativas
                      AND dead_lettered_at IS NULL
                      AND (lease_until IS NULL OR lease_until <= now())
                      AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                      AND deleted_at IS NULL
                    ORDER BY next_attempt_at NULLS FIRST, created_at
                    LIMIT p_limite
                    FOR UPDATE SKIP LOCKED)
UPDATE message m
SET attempt_count   = m.attempt_count + 1,
    next_attempt_at = now() + (p_espera_segundos * power(2, m.attempt_count)) * interval '1 second',
    lease_until     = now() + (p_espera_segundos * power(2, m.attempt_count)) * interval '1 second',
    lease_owner     = concat(current_setting('application_name', true), ':', pg_backend_pid()),
    dead_lettered_at = CASE
        WHEN m.attempt_count + 1 >= p_max_tentativas THEN now()
        ELSE NULL
    END
FROM candidatas c
WHERE m.id = c.id
RETURNING m.id, m.tenant_id;
$$;

CREATE OR REPLACE FUNCTION reservar_eventos_para_processar(
    p_limite integer,
    p_max_tentativas integer,
    p_espera_segundos integer
) RETURNS TABLE (event_id uuid, event_tenant_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
WITH candidatos AS (SELECT id
                    FROM inbound_event
                    WHERE processed_at IS NULL
                      AND attempt_count < p_max_tentativas
                      AND dead_lettered_at IS NULL
                      AND (lease_until IS NULL OR lease_until <= now())
                      AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                      AND deleted_at IS NULL
                    ORDER BY next_attempt_at NULLS FIRST, received_at
                    LIMIT p_limite
                    FOR UPDATE SKIP LOCKED)
UPDATE inbound_event e
SET attempt_count   = e.attempt_count + 1,
    next_attempt_at = now() + (p_espera_segundos * power(2, e.attempt_count)) * interval '1 second',
    lease_until     = now() + (p_espera_segundos * power(2, e.attempt_count)) * interval '1 second',
    lease_owner     = concat(current_setting('application_name', true), ':', pg_backend_pid()),
    dead_lettered_at = CASE
        WHEN e.attempt_count + 1 >= p_max_tentativas THEN now()
        ELSE NULL
    END
FROM candidatos c
WHERE e.id = c.id
RETURNING e.id, e.tenant_id;
$$;

-- Reprocessamento explicito, sempre limitado pelo RLS ao tenant corrente.
CREATE FUNCTION reprocessar_mensagem_saida(p_message_id uuid) RETURNS boolean
    LANGUAGE sql
    SET search_path = pg_catalog, public
AS $$
UPDATE message
   SET status = 'PENDING', attempt_count = 0, next_attempt_at = NULL,
       lease_owner = NULL, lease_until = NULL, dead_lettered_at = NULL,
       failure_reason = NULL
 WHERE id = p_message_id
   AND direction = 'OUTBOUND'
   AND tenant_id = current_tenant_id()
   AND deleted_at IS NULL
RETURNING true;
$$;

CREATE FUNCTION reprocessar_evento_entrada(p_event_id uuid) RETURNS boolean
    LANGUAGE sql
    SET search_path = pg_catalog, public
AS $$
UPDATE inbound_event
   SET processed_at = NULL, attempt_count = 0, next_attempt_at = NULL,
       lease_owner = NULL, lease_until = NULL, dead_lettered_at = NULL,
       failure_reason = NULL
 WHERE id = p_event_id
   AND tenant_id = current_tenant_id()
   AND payload_purged_at IS NULL
   AND deleted_at IS NULL
RETURNING true;
$$;

-- Expurgo global e estreito para o worker: nao devolve tenant nem conteudo e
-- so remove payload de evento concluido ou definitivamente morto.
CREATE FUNCTION expurgar_payloads_eventos_recebidos(p_limite integer) RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    removidos integer;
BEGIN
    WITH expirados AS (
        SELECT id
          FROM inbound_event
         WHERE payload_retain_until <= now()
           AND payload_purged_at IS NULL
           AND (processed_at IS NOT NULL OR dead_lettered_at IS NOT NULL)
         ORDER BY payload_retain_until
         LIMIT p_limite
         FOR UPDATE SKIP LOCKED
    )
    UPDATE inbound_event e
       SET payload = NULL, payload_ciphertext = NULL, payload_purged_at = now()
      FROM expirados x
     WHERE e.id = x.id;
    GET DIAGNOSTICS removidos = ROW_COUNT;
    RETURN removidos;
END;
$$;

-- ---------------------------------------------------------------------------
-- Livro-razao append-only de uso
-- ---------------------------------------------------------------------------

CREATE TABLE usage_event
(
    id                    uuid PRIMARY KEY,
    tenant_id             uuid        NOT NULL REFERENCES tenant (id),
    channel_connection_id uuid        NOT NULL,
    message_id            uuid,
    event_type            text        NOT NULL,
    quantity              bigint      NOT NULL DEFAULT 1,
    unit                  text        NOT NULL,
    idempotency_key       text        NOT NULL,
    occurred_at           timestamptz NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usage_event_quantidade_positiva CHECK (quantity > 0),
    CONSTRAINT usage_event_tipo_valido CHECK (
        event_type IN ('MESSAGE_INBOUND_ACCEPTED', 'MESSAGE_OUTBOUND_SENT', 'MEDIA_BYTES_STORED')
    ),
    CONSTRAINT usage_event_canal_mesmo_tenant
        FOREIGN KEY (tenant_id, channel_connection_id)
        REFERENCES channel_connection (tenant_id, id),
    CONSTRAINT usage_event_mensagem_mesmo_tenant
        FOREIGN KEY (tenant_id, message_id)
        REFERENCES message (tenant_id, id)
);

CREATE UNIQUE INDEX usage_event_idempotencia
    ON usage_event (tenant_id, idempotency_key);
CREATE INDEX usage_event_agregacao
    ON usage_event (tenant_id, occurred_at, event_type);

ALTER TABLE usage_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_event FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_event_isolamento ON usage_event
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

CREATE FUNCTION usage_event_imutavel() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION 'usage_event e append-only';
END;
$$;

CREATE TRIGGER usage_event_sem_update_delete
    BEFORE UPDATE OR DELETE ON usage_event
    FOR EACH ROW EXECUTE FUNCTION usage_event_imutavel();

CREATE FUNCTION medir_uso_de_mensagem() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
DECLARE
    tipo text;
    instante timestamptz;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.direction = 'INBOUND' THEN
        tipo := 'MESSAGE_INBOUND_ACCEPTED';
        instante := COALESCE(NEW.sent_at, NEW.created_at, now());
    ELSIF TG_OP = 'UPDATE'
       AND NEW.direction = 'OUTBOUND'
       AND NEW.status = 'SENT'
       AND OLD.status IS DISTINCT FROM 'SENT' THEN
        tipo := 'MESSAGE_OUTBOUND_SENT';
        instante := COALESCE(NEW.sent_at, now());
    ELSE
        RETURN NEW;
    END IF;

    INSERT INTO usage_event (
        id, tenant_id, channel_connection_id, message_id, event_type,
        quantity, unit, idempotency_key, occurred_at
    ) VALUES (
        gen_random_uuid(), NEW.tenant_id, NEW.channel_connection_id, NEW.id,
        tipo, 1, 'message', concat(lower(tipo), ':', NEW.id), instante
    ) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE TRIGGER message_medir_uso
    AFTER INSERT OR UPDATE OF status ON message
    FOR EACH ROW EXECUTE FUNCTION medir_uso_de_mensagem();

-- A V9 revogou PUBLIC e o runtime por padrao; exponha somente as operacoes
-- deliberadas. Funcoes de trigger nao recebem EXECUTE direto do runtime.
REVOKE EXECUTE ON FUNCTION reprocessar_mensagem_saida(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION reprocessar_evento_entrada(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expurgar_payloads_eventos_recebidos(integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION usage_event_imutavel() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION medir_uso_de_mensagem() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION reprocessar_mensagem_saida(uuid) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION reprocessar_evento_entrada(uuid) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION expurgar_payloads_eventos_recebidos(integer) TO "${runtime_role}";

COMMENT ON TABLE usage_event IS
    'Livro-razao append-only e idempotente. Agregacoes sao projecoes posteriores, nunca UPDATE desta tabela.';
