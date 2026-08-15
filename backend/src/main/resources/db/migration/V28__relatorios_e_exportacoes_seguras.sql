-- Catalogo versionado de metricas e jobs de exportacao segura.
--
-- Billing permanece deliberadamente fora desta migration: a estrategia atual
-- vende implantacao, sem preco recorrente, fatura ou provedor de pagamento.
-- A base futura continua em V27; criar politica comercial aqui inventaria um
-- contrato que o produto explicitamente decidiu nao operar agora.

CREATE TABLE report_metric_definition
(
    code             text        NOT NULL,
    version_number   integer     NOT NULL,
    report_code      text        NOT NULL,
    display_name     text        NOT NULL,
    formula          text        NOT NULL,
    granularity      text        NOT NULL,
    time_zone        text        NOT NULL,
    currency_code    text,
    unit             text        NOT NULL,
    source_modules   text[]      NOT NULL,
    active           boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (code, version_number),
    CONSTRAINT report_metric_code_formato CHECK (code ~ '^[a-z][a-z0-9_.]{2,79}$'),
    CONSTRAINT report_metric_versao_positiva CHECK (version_number > 0),
    CONSTRAINT report_metric_relatorio_formato CHECK (report_code ~ '^[A-Z][A-Z0-9_]{2,79}$'),
    CONSTRAINT report_metric_granularidade_valida CHECK (granularity IN ('TENANT_SNAPSHOT')),
    CONSTRAINT report_metric_timezone_explicito CHECK (time_zone IN ('UTC', 'America/Sao_Paulo')),
    CONSTRAINT report_metric_moeda_valida CHECK (currency_code IS NULL OR currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT report_metric_unidade_formato CHECK (unit ~ '^[a-z][a-z0-9_.]{1,39}$'),
    CONSTRAINT report_metric_fontes_presentes CHECK (cardinality(source_modules) BETWEEN 1 AND 10)
);

INSERT INTO report_metric_definition
    (code, version_number, report_code, display_name, formula, granularity,
     time_zone, currency_code, unit, source_modules)
VALUES
    ('conversations.open.count', 1, 'OVERVIEW_V1', 'Conversas abertas',
     'count(conversation where status = OPEN and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['conversation']),
    ('conversations.waiting.count', 1, 'OVERVIEW_V1', 'Conversas aguardando',
     'count(conversation where status = PENDING and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['conversation']),
    ('messages.inbound.today.count', 1, 'OVERVIEW_V1', 'Mensagens recebidas hoje',
     'count(message inbound since local day start)', 'TENANT_SNAPSHOT',
     'America/Sao_Paulo', NULL, 'count', ARRAY['conversation']),
    ('contacts.total.count', 1, 'OVERVIEW_V1', 'Total de contatos',
     'count(contact where not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['contact']),
    ('deals.open.count', 1, 'OVERVIEW_V1', 'Oportunidades abertas',
     'count(deal where status = OPEN and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['deal']),
    ('deals.open.value_minor', 1, 'OVERVIEW_V1', 'Valor aberto',
     'sum(deal.amount_minor where status = OPEN and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', 'BRL', 'minor_currency_unit', ARRAY['deal']),
    ('deals.won.count', 1, 'OVERVIEW_V1', 'Oportunidades ganhas',
     'count(deal where status = WON and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['deal']),
    ('deals.won.value_minor', 1, 'OVERVIEW_V1', 'Valor ganho',
     'sum(deal.amount_minor where status = WON and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', 'BRL', 'minor_currency_unit', ARRAY['deal']),
    ('deals.lost.count', 1, 'OVERVIEW_V1', 'Oportunidades perdidas',
     'count(deal where status = LOST and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['deal']),
    ('tasks.open.count', 1, 'OVERVIEW_V1', 'Tarefas abertas',
     'count(task where done_at is null and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['task']),
    ('tasks.overdue.count', 1, 'OVERVIEW_V1', 'Tarefas atrasadas',
     'count(task where done_at is null and due_at < snapshot_at and not deleted)',
     'TENANT_SNAPSHOT', 'UTC', NULL, 'count', ARRAY['task']),
    ('channels.active.count', 1, 'OVERVIEW_V1', 'Canais ativos',
     'count(channel_connection where active and not deleted)', 'TENANT_SNAPSHOT',
     'UTC', NULL, 'count', ARRAY['channel']),
    ('deals.conversion.percent', 1, 'OVERVIEW_V1', 'Taxa de conversao',
     'round(won * 1000 / (won + lost)) / 10; zero when no closed deal',
     'TENANT_SNAPSHOT', 'UTC', NULL, 'percentage_point', ARRAY['deal']);

-- Catalogo do release: runtime consulta, mas nao reescreve definicoes antigas.
REVOKE INSERT, UPDATE, DELETE ON report_metric_definition FROM "${runtime_role}";

CREATE TABLE report_export_job
(
    id                       uuid PRIMARY KEY,
    tenant_id                uuid        NOT NULL REFERENCES tenant (id),
    requested_by             uuid        NOT NULL,
    report_code              text        NOT NULL,
    format                   text        NOT NULL,
    idempotency_key          text        NOT NULL,
    request_hash             text        NOT NULL,
    status                   text        NOT NULL,
    attempts                 integer     NOT NULL DEFAULT 0,
    lease_owner              text,
    lease_until              timestamptz,
    snapshot_at              timestamptz,
    metric_catalog_version   text,
    storage_key              text,
    byte_size                bigint,
    content_sha256           text,
    failure_code             text,
    requested_at             timestamptz NOT NULL DEFAULT now(),
    started_at               timestamptz,
    completed_at             timestamptz,
    expires_at               timestamptz,
    canceled_at              timestamptz,
    purged_at                timestamptz,
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT report_export_report_valido CHECK (report_code IN ('OVERVIEW_V1')),
    CONSTRAINT report_export_format_valido CHECK (format IN ('CSV')),
    CONSTRAINT report_export_idempotencia_valida CHECK (
        char_length(idempotency_key) BETWEEN 8 AND 120
        AND idempotency_key ~ '^[A-Za-z0-9._:-]+$'),
    CONSTRAINT report_export_hash_valido CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT report_export_status_valido CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'DENIED', 'FAILED', 'CANCELED', 'EXPIRED'
    )),
    CONSTRAINT report_export_tentativas_validas CHECK (attempts BETWEEN 0 AND 20),
    CONSTRAINT report_export_tamanho_valido CHECK (byte_size IS NULL OR byte_size BETWEEN 1 AND 5242880),
    CONSTRAINT report_export_conteudo_hash_valido CHECK (
        content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT report_export_falha_formato CHECK (
        failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT report_export_tenant_id_unico UNIQUE (tenant_id, id),
    CONSTRAINT report_export_replay_unico UNIQUE (tenant_id, requested_by, idempotency_key),
    CONSTRAINT report_export_usuario_mesmo_tenant FOREIGN KEY (tenant_id, requested_by)
        REFERENCES app_user (tenant_id, id)
);

CREATE INDEX report_export_fila
    ON report_export_job (status, requested_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX report_export_retencao
    ON report_export_job (expires_at, id)
    WHERE storage_key IS NOT NULL AND purged_at IS NULL;

CREATE TRIGGER report_export_updated_at
    BEFORE UPDATE ON report_export_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE report_export_job ENABLE ROW LEVEL SECURITY;
ALTER TABLE report_export_job FORCE ROW LEVEL SECURITY;
CREATE POLICY report_export_isolamento ON report_export_job
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

REVOKE DELETE ON report_export_job FROM "${runtime_role}";

-- Legal hold pode preservar o artefato cifrado, mas nunca prolonga o link de
-- download. A expiracao continua invalidando acesso; a limpeza fisica aguarda
-- o encerramento do hold.
ALTER TABLE legal_hold DROP CONSTRAINT legal_hold_alvo_conhecido;
ALTER TABLE legal_hold ADD CONSTRAINT legal_hold_alvo_conhecido CHECK (target_type IN (
    'CONTACT', 'CONVERSATION', 'MESSAGE', 'AUDIT_EVENT', 'USAGE_EVENT',
    'REALTIME_EVENT', 'CONNECTOR_ATTEMPT', 'REPORT_EXPORT', 'TENANT'
));

ALTER TABLE audit_event DROP CONSTRAINT audit_event_action_valida;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_action_valida CHECK (action_code IN (
    'AUTHORIZATION_DENIED_V1', 'AUDIT_READ_V1', 'EXPORT_REQUESTED_V1',
    'EXPORT_COMPLETED_V1', 'EXPORT_DOWNLOADED_V1', 'EXPORT_CANCELED_V1',
    'CREDENTIAL_CHANGED_V1', 'ROLE_CHANGED_V1',
    'SENSITIVE_CONFIGURATION_CHANGED_V1'
));

-- Reserva cross-tenant estreita. Somente ids escapam; leitura e geracao voltam
-- ao contexto RLS do tenant antes de acessar qualquer dado.
CREATE FUNCTION reservar_exportacoes_relatorio(
    p_limite integer,
    p_lease_seconds integer,
    p_max_por_tenant integer,
    p_max_tentativas integer,
    p_worker text
) RETURNS TABLE (export_id uuid, export_tenant_id uuid)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    candidata record;
    reservadas integer := 0;
    ativas_tenant integer;
BEGIN
    IF p_limite NOT BETWEEN 1 AND 200
       OR p_lease_seconds NOT BETWEEN 5 AND 3600
       OR p_max_por_tenant NOT BETWEEN 1 AND 50
       OR p_max_tentativas NOT BETWEEN 1 AND 20
       OR p_worker IS NULL OR char_length(p_worker) NOT BETWEEN 1 AND 120 THEN
        RAISE EXCEPTION 'parametros invalidos para reserva de exportacoes';
    END IF;

    UPDATE report_export_job
       SET status = 'FAILED', failure_code = 'RETRY_LIMIT', lease_owner = NULL,
           lease_until = NULL
     WHERE status IN ('PENDING', 'PROCESSING')
       AND attempts >= p_max_tentativas
       AND (lease_until IS NULL OR lease_until <= now());

    FOR candidata IN
        SELECT e.id, e.tenant_id, e.status
          FROM report_export_job e
         WHERE (e.status = 'PENDING'
                OR (e.status = 'PROCESSING' AND e.lease_until <= now()))
           AND e.attempts < p_max_tentativas
         ORDER BY CASE WHEN e.status = 'PROCESSING' THEN 0 ELSE 1 END,
                  e.requested_at, e.id
         LIMIT LEAST(p_limite * 8, 1600)
         FOR UPDATE SKIP LOCKED
    LOOP
        EXIT WHEN reservadas >= p_limite;
        SELECT count(*) INTO ativas_tenant
          FROM report_export_job
         WHERE tenant_id = candidata.tenant_id
           AND status = 'PROCESSING'
           AND lease_until > now();
        IF ativas_tenant >= p_max_por_tenant THEN CONTINUE; END IF;

        UPDATE report_export_job
           SET status = 'PROCESSING',
               started_at = COALESCE(started_at, now()),
               attempts = attempts + 1,
               lease_owner = p_worker,
               lease_until = now() + make_interval(secs => p_lease_seconds),
               failure_code = NULL
         WHERE id = candidata.id;

        export_id := candidata.id;
        export_tenant_id := candidata.tenant_id;
        reservadas := reservadas + 1;
        RETURN NEXT;
    END LOOP;
END;
$$;

CREATE FUNCTION listar_exportacoes_para_expurgo(p_limite integer)
RETURNS TABLE (export_id uuid, export_tenant_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
    SELECT e.id, e.tenant_id
      FROM report_export_job e
     WHERE e.storage_key IS NOT NULL
       AND e.purged_at IS NULL
       AND ((e.expires_at IS NOT NULL AND e.expires_at <= now())
            OR e.status = 'CANCELED')
     ORDER BY COALESCE(e.expires_at, e.canceled_at), e.id
     LIMIT LEAST(GREATEST(p_limite, 1), 500)
$$;

REVOKE EXECUTE ON FUNCTION reservar_exportacoes_relatorio(integer, integer, integer, integer, text)
    FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION listar_exportacoes_para_expurgo(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION reservar_exportacoes_relatorio(integer, integer, integer, integer, text)
    TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION listar_exportacoes_para_expurgo(integer) TO "${runtime_role}";

COMMENT ON TABLE report_metric_definition IS
    'Catalogo estrutural versionado; formula, fonte, timezone e moeda compoem o contrato da metrica.';
COMMENT ON TABLE report_export_job IS
    'Job tenant-scoped, idempotente e sem conteudo de cliente; o arquivo cifrado vive em storage privado.';
