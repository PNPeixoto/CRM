-- Retencao, legal hold e direitos do titular.
--
-- Esta migration cria o MECANISMO. Ela nao decide prazo: nenhuma funcao aqui
-- usa `now()` nem embute quantidade de dias. O corte chega como parametro, e
-- os prazos vivem na configuracao da aplicacao, desligados por padrao.
--
-- O motivo e direto: um prazo errado apaga dado de cliente de forma
-- irreversivel. Um numero fixado em migration seria pior ainda, porque
-- migration aplicada e imutavel e o erro viajaria para producao sem revisao.
--
-- Receber o instante por parametro tambem e o que torna o aceite verificavel:
-- "relogio controlado prova retencao e expurgo idempotente" so e demonstravel
-- se o teste conseguir escolher o agora.

-- ---------------------------------------------------------------------------
-- Legal hold
-- ---------------------------------------------------------------------------
--
-- Bloqueia expurgo dentro de um escopo declarado. Fica no banco, e nao na
-- configuracao, porque precisa valer para qualquer caminho que apague — worker,
-- pedido de titular ou operacao manual. Hold que dependesse da aplicacao
-- lembrar de consultar nao seria hold.

CREATE TABLE legal_hold
(
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    -- Escopo: uma categoria inteira do tenant, ou um registro especifico.
    -- `target_id` nulo significa a categoria toda.
    target_type   text        NOT NULL,
    target_id     uuid,
    reason        text        NOT NULL,
    declared_by   uuid        NOT NULL,
    valid_from    timestamptz NOT NULL DEFAULT now(),
    -- Nulo significa vigente por tempo indeterminado. Hold que expira sozinho
    -- em silencio derrota o proprio proposito.
    valid_until   timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    deleted_at    timestamptz,
    CONSTRAINT legal_hold_alvo_conhecido CHECK (target_type IN (
        'CONTACT', 'CONVERSATION', 'MESSAGE', 'AUDIT_EVENT',
        'USAGE_EVENT', 'REALTIME_EVENT', 'CONNECTOR_ATTEMPT', 'TENANT')),
    CONSTRAINT legal_hold_vigencia CHECK (valid_until IS NULL OR valid_until > valid_from),
    CONSTRAINT legal_hold_tenant_id_unico UNIQUE (tenant_id, id)
);

CREATE INDEX legal_hold_vigente
    ON legal_hold (tenant_id, target_type, target_id)
    WHERE deleted_at IS NULL;

ALTER TABLE legal_hold ENABLE ROW LEVEL SECURITY;
ALTER TABLE legal_hold FORCE ROW LEVEL SECURITY;
CREATE POLICY legal_hold_isolamento ON legal_hold
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ---------------------------------------------------------------------------
-- Consulta de hold
-- ---------------------------------------------------------------------------
--
-- `SECURITY DEFINER` com search_path fixo, como as demais funcoes de decisao
-- do projeto. Ela le apenas o tenant corrente: o RLS continua valendo porque a
-- funcao filtra por `current_tenant_id()` explicitamente.

CREATE FUNCTION ha_legal_hold(
    p_target_type text,
    p_target_id   uuid,
    p_agora       timestamptz
) RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM legal_hold h
         WHERE h.tenant_id = current_tenant_id()
           AND h.deleted_at IS NULL
           AND h.valid_from <= p_agora
           AND (h.valid_until IS NULL OR h.valid_until > p_agora)
           -- Um hold sobre o tenant inteiro cobre qualquer categoria; um hold
           -- de categoria cobre qualquer registro dela; `target_id` preenchido
           -- cobre so aquele registro.
           AND (h.target_type = 'TENANT'
                OR (h.target_type = p_target_type
                    AND (h.target_id IS NULL OR h.target_id = p_target_id)))
    );
$$;

-- ---------------------------------------------------------------------------
-- Expurgo por categoria
-- ---------------------------------------------------------------------------
--
-- Todas seguem o mesmo contrato: recebem o instante de corte e um limite de
-- lote, devolvem quantas linhas trataram, e sao idempotentes — rodar de novo
-- sobre o mesmo corte nao encontra mais nada e devolve zero.

CREATE FUNCTION expurgar_conversas_encerradas(
    p_corte  timestamptz,
    p_limite integer
) RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS $$
DECLARE
    v_ids   uuid[];
    v_total integer;
BEGIN
    SELECT array_agg(id) INTO v_ids FROM (
        SELECT c.id FROM conversation c
         WHERE c.tenant_id = current_tenant_id()
           AND c.status = 'CLOSED'
           AND c.updated_at < p_corte
           AND NOT ha_legal_hold('CONVERSATION', c.id, p_corte)
           AND NOT ha_legal_hold('MESSAGE', c.id, p_corte)
         ORDER BY c.updated_at
         LIMIT p_limite
    ) alvo;

    IF v_ids IS NULL THEN RETURN 0; END IF;

    DELETE FROM message WHERE tenant_id = current_tenant_id()
                          AND conversation_id = ANY (v_ids);
    DELETE FROM conversation WHERE tenant_id = current_tenant_id()
                               AND id = ANY (v_ids);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN v_total;
END;
$$;

ALTER TABLE contact ADD COLUMN anonymized_at timestamptz;
COMMENT ON COLUMN contact.anonymized_at IS
    'Quando o dado pessoal foi substituido. Nulo significa contato intacto.';

-- Contato nao e apagado: `deal`, `task` e `conversation` o referenciam, e
-- apagar quebraria historico comercial que nao pertence ao titular do dado
-- pessoal. Anonimizar preserva a integridade e cumpre o mesmo fim — e o caso
-- que o aceite chama de "exclusao impossivel vira anonimizacao".
CREATE FUNCTION anonimizar_contatos(
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
    WITH alvo AS (
        SELECT c.id FROM contact c
         WHERE c.tenant_id = current_tenant_id()
           AND c.deleted_at IS NOT NULL
           AND c.deleted_at < p_corte
           AND c.anonymized_at IS NULL
           AND NOT ha_legal_hold('CONTACT', c.id, p_corte)
         ORDER BY c.deleted_at
         LIMIT p_limite
    )
    UPDATE contact c
       SET name          = 'Titular anonimizado',
           email         = NULL,
           phone         = NULL,
           company_name  = NULL,
           notes         = NULL,
           anonymized_at = p_corte,
           updated_at    = p_corte
      FROM alvo
     WHERE c.id = alvo.id AND c.tenant_id = current_tenant_id();

    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN v_total;
END;
$$;

CREATE FUNCTION expurgar_telemetria(
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
    RETURN v_total;
END;
$$;

CREATE FUNCTION expurgar_eventos_tempo_real(
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
    DELETE FROM realtime_event r
     WHERE r.tenant_id = current_tenant_id()
       AND r.id IN (
           SELECT id FROM realtime_event
            WHERE tenant_id = current_tenant_id()
              AND occurred_at < p_corte
              AND NOT ha_legal_hold('REALTIME_EVENT', id, p_corte)
            ORDER BY occurred_at
            LIMIT p_limite);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN v_total;
END;
$$;

CREATE FUNCTION expurgar_tentativas_de_conector(
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
    DELETE FROM http_connector_attempt a
     WHERE a.tenant_id = current_tenant_id()
       AND a.id IN (
           SELECT id FROM http_connector_attempt
            WHERE tenant_id = current_tenant_id()
              AND started_at < p_corte
              AND NOT ha_legal_hold('CONNECTOR_ATTEMPT', id, p_corte)
            ORDER BY started_at
            LIMIT p_limite);
    GET DIAGNOSTICS v_total = ROW_COUNT;
    RETURN v_total;
END;
$$;

-- A trilha e append-only e o gatilho da V22 recusa DELETE, exceto sob o modo
-- controlado que ela mesma previu. Este e o unico caminho legitimo de descarte
-- da auditoria: dentro da funcao, com corte explicito e respeitando hold.
CREATE FUNCTION expurgar_auditoria(
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
    PERFORM set_config('app.audit_retention_mode', 'controlled', true);

    DELETE FROM audit_event e
     WHERE e.tenant_id = current_tenant_id()
       AND e.id IN (
           SELECT id FROM audit_event
            WHERE tenant_id = current_tenant_id()
              AND occurred_at < p_corte
              AND NOT ha_legal_hold('AUDIT_EVENT', id, p_corte)
            ORDER BY occurred_at
            LIMIT p_limite);
    GET DIAGNOSTICS v_total = ROW_COUNT;

    -- Devolve o modo ao padrao dentro da propria transacao: deixar 'controlled'
    -- ativo transformaria a trilha em tabela comum para o resto da requisicao.
    PERFORM set_config('app.audit_retention_mode', '', true);
    RETURN v_total;
END;
$$;

-- ---------------------------------------------------------------------------
-- Privilegios
-- ---------------------------------------------------------------------------
--
-- O runtime executa as funcoes, e nao recebe DELETE direto nas tabelas que
-- elas tratam. Assim todo descarte passa pelo corte explicito e pela checagem
-- de hold, em vez de depender de a aplicacao lembrar.

REVOKE EXECUTE ON FUNCTION ha_legal_hold(text, uuid, timestamptz) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expurgar_conversas_encerradas(timestamptz, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION anonimizar_contatos(timestamptz, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expurgar_telemetria(timestamptz, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expurgar_eventos_tempo_real(timestamptz, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expurgar_tentativas_de_conector(timestamptz, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expurgar_auditoria(timestamptz, integer) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION ha_legal_hold(text, uuid, timestamptz) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION expurgar_conversas_encerradas(timestamptz, integer) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION anonimizar_contatos(timestamptz, integer) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION expurgar_telemetria(timestamptz, integer) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION expurgar_eventos_tempo_real(timestamptz, integer) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION expurgar_tentativas_de_conector(timestamptz, integer) TO "${runtime_role}";
GRANT EXECUTE ON FUNCTION expurgar_auditoria(timestamptz, integer) TO "${runtime_role}";

COMMENT ON TABLE legal_hold IS
    'Bloqueia expurgo no escopo declarado; vive no banco para valer em qualquer caminho de exclusao.';
COMMENT ON FUNCTION ha_legal_hold(text, uuid, timestamptz) IS
    'Verdadeiro quando ha hold vigente sobre o tenant, a categoria ou o registro.';
