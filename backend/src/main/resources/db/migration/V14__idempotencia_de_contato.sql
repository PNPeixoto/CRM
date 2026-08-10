-- A criação manual de contato pode ser repetida pelo navegador quando a
-- resposta se perde. A chave é opaca, por tenant, e nunca substitui o id do
-- domínio: serve apenas para devolver o mesmo resultado no replay.

ALTER TABLE contact
    ADD COLUMN idempotency_key varchar(128);

CREATE UNIQUE INDEX contact_criacao_idempotente
    ON contact (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN contact.idempotency_key IS
    'Chave opaca do cliente para replay idempotente da criação manual.';
