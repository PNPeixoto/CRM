-- A chave pertence à operação HTTP de envio, não ao provedor. Ela permite que
-- o cliente repita uma requisição cujo resultado se perdeu sem criar uma
-- segunda mensagem na fila de saída.
ALTER TABLE message
    ADD COLUMN idempotency_key varchar(128);

CREATE UNIQUE INDEX message_idempotencia_de_envio
    ON message (tenant_id, conversation_id, idempotency_key)
    WHERE direction = 'OUTBOUND' AND idempotency_key IS NOT NULL;

COMMENT ON COLUMN message.idempotency_key IS
    'Chave opaca do cliente para replay idempotente do envio; nunca é registrada em log.';
