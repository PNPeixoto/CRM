-- Registros manuais criados antes da identificação obrigatória podiam nascer
-- sem responsável quando o autor tinha alcance TENANT. O autor persistido é a
-- única atribuição factual disponível; o backfill não tenta inferir dono por
-- nome, data ou papel.

UPDATE contact c
   SET owner_user_id = c.created_by,
       updated_at = now(),
       updated_by = c.created_by
  FROM app_user u
 WHERE c.owner_user_id IS NULL
   AND c.created_by IS NOT NULL
   AND u.tenant_id = c.tenant_id
   AND u.id = c.created_by;

UPDATE deal d
   SET owner_user_id = d.created_by,
       updated_at = now(),
       updated_by = d.created_by
  FROM app_user u
 WHERE d.owner_user_id IS NULL
   AND d.created_by IS NOT NULL
   AND u.tenant_id = d.tenant_id
   AND u.id = d.created_by;

UPDATE task t
   SET assigned_user_id = t.created_by,
       updated_at = now(),
       updated_by = t.created_by
  FROM app_user u
 WHERE t.assigned_user_id IS NULL
   AND t.created_by IS NOT NULL
   AND u.tenant_id = t.tenant_id
   AND u.id = t.created_by;
