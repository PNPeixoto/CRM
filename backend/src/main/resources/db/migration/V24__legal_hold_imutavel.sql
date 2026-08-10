-- Torna o legal hold imune a quem executa o expurgo.
--
-- Corrige `LGPD-005`. A V23 criou `legal_hold` e deixou o papel de runtime
-- herdar `INSERT, SELECT, UPDATE, DELETE` do `ALTER DEFAULT PRIVILEGES` da V9.
-- O resultado e que o mesmo papel que executa o expurgo podia apagar a trava
-- que deveria impedi-lo — uma trava que o proprio guarda pode remover nao e
-- trava.
--
-- A V22 ja havia tratado `audit_event` assim, e pela mesma razao: superficie de
-- controle nao pode ser reescrita por quem ela controla. `legal_hold` e da
-- mesma natureza e passou sem esse cuidado.

-- ---------------------------------------------------------------------------
-- Privilegios do runtime
-- ---------------------------------------------------------------------------
--
-- Declarar e encerrar hold continua sendo trabalho da aplicacao. Apagar o
-- registro, nao: um hold que some leva junto a prova de por que o dado foi
-- retido, que e exatamente o que precisa ser demonstravel depois.

REVOKE DELETE ON legal_hold FROM "${runtime_role}";

-- UPDATE por coluna. O runtime encerra um hold escrevendo `deleted_at` ou
-- `valid_until`, e nao consegue reescrever escopo, motivo nem quem declarou.
-- Sem isso, bastaria trocar o `target_type` para um valor que nao cobre nada e
-- o hold continuaria existindo enquanto deixava de proteger — falha pior que a
-- remocao, porque nao aparece.
REVOKE UPDATE ON legal_hold FROM "${runtime_role}";
GRANT UPDATE (valid_until, deleted_at, updated_at, updated_by)
    ON legal_hold TO "${runtime_role}";

-- ---------------------------------------------------------------------------
-- Imutabilidade independente de privilegio
-- ---------------------------------------------------------------------------
--
-- O gatilho vale para qualquer papel, inclusive o de migracao. Privilegio e
-- concedido e revogado; o gatilho e propriedade da tabela, e sobrevive a quem
-- rodar um `GRANT` distraido depois.
--
-- `TRUNCATE` nao dispara gatilho de linha, e isso e deliberado: a limpeza de
-- teste usa `TRUNCATE` por uma conexao administrativa privada, e nao deve ser
-- confundida com operacao de produto.

CREATE FUNCTION proteger_legal_hold() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'legal_hold nao e removido; encerre com deleted_at ou valid_until';
    END IF;

    IF NEW.tenant_id   IS DISTINCT FROM OLD.tenant_id
       OR NEW.target_type IS DISTINCT FROM OLD.target_type
       OR NEW.target_id   IS DISTINCT FROM OLD.target_id
       OR NEW.reason      IS DISTINCT FROM OLD.reason
       OR NEW.declared_by IS DISTINCT FROM OLD.declared_by
       OR NEW.valid_from  IS DISTINCT FROM OLD.valid_from THEN
        RAISE EXCEPTION
            'escopo, motivo e origem do legal_hold sao imutaveis';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER legal_hold_imutavel
    BEFORE UPDATE OR DELETE ON legal_hold
    FOR EACH ROW EXECUTE FUNCTION proteger_legal_hold();

REVOKE EXECUTE ON FUNCTION proteger_legal_hold() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION proteger_legal_hold() FROM "${runtime_role}";

COMMENT ON TABLE legal_hold IS
    'Bloqueia expurgo no escopo declarado. Escopo e origem sao imutaveis; encerramento e logico.';
