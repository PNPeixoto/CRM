-- Execute somente para diagnóstico antes de aplicar V8 em um banco legado.
-- Qualquer contagem maior que zero bloqueia o deploy: corrija a origem e os
-- dados explicitamente; não apague nem reatribua linhas de forma automática.

SELECT 'refresh_token.usuario' AS referencia, count(*) AS invalidas
  FROM refresh_token c LEFT JOIN app_user p
    ON p.tenant_id = c.tenant_id AND p.id = c.user_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'contact.responsavel', count(*) FROM contact c LEFT JOIN app_user p
    ON p.tenant_id = c.tenant_id AND p.id = c.owner_user_id
 WHERE c.owner_user_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'conversation.canal', count(*) FROM conversation c LEFT JOIN channel_connection p
    ON p.tenant_id = c.tenant_id AND p.id = c.channel_connection_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'conversation.atendente', count(*) FROM conversation c LEFT JOIN app_user p
    ON p.tenant_id = c.tenant_id AND p.id = c.assigned_user_id
 WHERE c.assigned_user_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'conversation.contato', count(*) FROM conversation c LEFT JOIN contact p
    ON p.tenant_id = c.tenant_id AND p.id = c.contact_id
 WHERE c.contact_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'message.conversa_canal', count(*) FROM message c LEFT JOIN conversation p
    ON p.tenant_id = c.tenant_id AND p.id = c.conversation_id
   AND p.channel_connection_id = c.channel_connection_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'message.autor', count(*) FROM message c LEFT JOIN app_user p
    ON p.tenant_id = c.tenant_id AND p.id = c.author_user_id
 WHERE c.author_user_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'channel_credential.canal', count(*) FROM channel_credential c LEFT JOIN channel_connection p
    ON p.tenant_id = c.tenant_id AND p.id = c.channel_connection_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'inbound_event.canal', count(*) FROM inbound_event c LEFT JOIN channel_connection p
    ON p.tenant_id = c.tenant_id AND p.id = c.channel_connection_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'pipeline_stage.funil', count(*) FROM pipeline_stage c LEFT JOIN pipeline p
    ON p.tenant_id = c.tenant_id AND p.id = c.pipeline_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'deal.etapa_funil', count(*) FROM deal c LEFT JOIN pipeline_stage p
    ON p.tenant_id = c.tenant_id AND p.pipeline_id = c.pipeline_id AND p.id = c.stage_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'deal.contato', count(*) FROM deal c LEFT JOIN contact p
    ON p.tenant_id = c.tenant_id AND p.id = c.contact_id
 WHERE c.contact_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'deal.responsavel', count(*) FROM deal c LEFT JOIN app_user p
    ON p.tenant_id = c.tenant_id AND p.id = c.owner_user_id
 WHERE c.owner_user_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'task.contato', count(*) FROM task c LEFT JOIN contact p
    ON p.tenant_id = c.tenant_id AND p.id = c.contact_id
 WHERE c.contact_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'task.oportunidade', count(*) FROM task c LEFT JOIN deal p
    ON p.tenant_id = c.tenant_id AND p.id = c.deal_id
 WHERE c.deal_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'task.responsavel', count(*) FROM task c LEFT JOIN app_user p
    ON p.tenant_id = c.tenant_id AND p.id = c.assigned_user_id
 WHERE c.assigned_user_id IS NOT NULL AND p.id IS NULL
ORDER BY referencia;
