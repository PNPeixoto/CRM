# Banco, papéis e migrations

## Classificação

- Tenant: `tenant`, `app_user`, `refresh_token`, `channel_connection`,
  `conversation`, `message`, `channel_credential`, `inbound_event`, `contact`,
  `pipeline`, `pipeline_stage`, `deal`, `task`, `tenant_profile`, `usage_event`,
  `channel_media`, tabelas `automation_*` e `http_connector*`. Todas usam
  RLS `ENABLE + FORCE`; V10 acrescenta `organizational_unit`,
  `organization_membership`, `app_role`, `role_permission` e
  `membership_scope`; V11 acrescenta `password_reset_token`,
  `mfa_authenticator`, `mfa_recovery_code` e `mfa_enrollment`. `tenant`
  compara a própria chave e as demais carregam
  `tenant_id`.
- Operacional sem tenant: `flyway_schema_history` e `event_publication`. A
  segunda pertence ao mecanismo do Spring Modulith e não pode receber conteúdo
  sensível de evento; listeners persistentes devem publicar identificadores,
  não payload de cliente.

O teste `BancoSegurancaTest` torna essa lista fechada: uma tabela nova sem
classificação reprova o gate.

## Papéis

O migrador aplica DDL e nunca é datasource da aplicação. O runtime possui
somente conexão, uso do schema, DML e apenas as funções explicitamente
concedidas pelas migrations. Ele não
tem `SUPERUSER`, `BYPASSRLS`, `CREATEDB`, `CREATEROLE`, `CREATE` ou `TRUNCATE`.
Funções novas nascem sem `EXECUTE` para `PUBLIC` e exigem grant explícito.

O tenant é derivado de credencial confiável. O checkout da conexão zera ou
define a proteção de sessão para autocommit; toda transação reaplica o valor
com `set_config(..., true)`, equivalente parametrizado a `SET LOCAL`. O retorno
ao pool sempre zera a variável.

## Deploy e atualização

1. Faça backup verificável e execute `flyway validate`.
2. Para banco anterior à V8, execute `db/preflight/V8__referencias_multitenant.sql`.
   Qualquer contagem positiva bloqueia o deploy e precisa de correção explícita.
3. Aplique migrations com o papel migrador e `runtime_role` configurado para o
   login real da aplicação.
4. Valide catálogo, RLS, grants e papel conectado antes de liberar tráfego.
5. Só então promova a mesma imagem da aplicação.

V9 a V21 preservam as estruturas anteriores; V10 adiciona o modelo
organizacional, V11 adiciona sessões endurecidas, recuperação e MFA, V12
atribui registros históricos ao autor quando estavam órfãos e V13 adiciona a
chave idempotente de mensagens de saída. V14 torna a criação manual de contato
repetível com segurança por tenant. V15 adiciona leases, dead letter,
idempotência cifrada de entrada e medição append-only. V16 persiste o estado
sanitizado da reconciliação Telegram. V17 adiciona a quarentena tenant-scoped
de mídia, sua retenção e medição. V18 adiciona a ponte Evolution de laboratório,
V19 a Inbox em tempo real e SLA, V20 o motor de automações e V21 os conectores
HTTP aprovados, segredos cifrados e tentativas sanitizadas.
Portanto o rollback do binário não exige rollback de schema. Migrations aplicadas não são editadas nem
revertidas por script `down`; uma reversão de dados usa restauração testada ou
uma migration compensatória revisada.

Índices atuais começam por `tenant_id` nas consultas tenant-scoped. Não houve
volume representativo que justificasse mudança por `EXPLAIN ANALYZE`; planos e
índices adicionais devem nascer de medição no Prompt 27, não de estimativa.
