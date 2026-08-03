# Preâmbulo canônico v3

## Fonte canônica

Este pacote v3 substitui integralmente as versões anteriores. Não use texto do
v1, v2 ou do prompt monolítico legado como requisito, salvo quando incorporado
explicitamente nesta versão. Leia `CLAUDE.md`, `contexto/00-projeto.md`,
`contexto/01-padroes-tecnicos.md` e `contexto/02-estado-atual.md`; se o documento
divergir do código executável, o código vence e a divergência é relatada.

## Protocolo de decisão

Pare apenas quando a escolha for irreversível ou cara de desfazer; envolver
segurança, privacidade, contrato ou cobrança; contrariar ADR aceito; ou depender
de informação ausente do repositório. Para escolha reversível, preserve o
comportamento atual, aplique o default menos invasivo, registre a suposição e
não invente preferência de produto.

## Alterações de contexto

Não reescreva arquivos compartilhados em toda branch. Decisão nova recebe ADR
individual em `contexto/decisoes/`. Sessão recebe timestamp e slug da tarefa em
`contexto/sessoes/`. `02-estado-atual.md` e o status do manifesto são atualizados
somente na integração à branch principal ou por consolidação explícita.

## Dados e segredos

Não registre payload de cliente, token, cookie, segredo, corpo de webhook,
conteúdo de mensagem ou arquivo em log, evidência ou documento de sessão. Use
identificadores e valores sanitizados. Credenciais só aparecem pelo nome da
variável; valores locais descartáveis ficam fora do versionamento.

## Segurança e multi-tenancy

O frontend não é fronteira de segurança. Tenant vem da identidade verificada,
nunca do body/query/header. Autorização verifica ação, escopo e registro. RLS é
testado com papel restrito. Módulo técnico, entitlement, permissão e visibilidade
de navegação são conceitos independentes.

## Dados e contratos

Migration existe apenas quando schema, índice, constraint, trigger, policy ou
seed estrutural mudar. DTOs de entrada e saída são separados. Operações
repetíveis usam idempotência. O servidor impõe paginação e limite de payload.
Regras relevantes são testáveis sem HTTP, sem criar domínio artificial para
CRUD sem invariantes.

## Webhooks

Receba bytes crus com limite, valide assinatura, persista de forma durável e só
então responda com o sucesso exigido pelo provedor. Processamento posterior é
assíncrono. Falha de persistência produz erro transitório para provocar retry.

## Auditoria e retenção

Append-only não significa retenção eterna. Cada categoria tem finalidade,
fundamento, prazo, acesso autorizado, legal hold quando aplicável e descarte ou
anonimização verificável. Auditoria é separada de logs operacionais.

## Testes e evidências

Falha não é ignorada. Quarentena exige issue, responsável, justificativa,
expiração, execução contínua e exclusão explícita do gate; segurança, tenant,
migration e cobrança não entram em quarentena. Evidência registra commit,
ambiente, data, comando, resultado, artefato e responsável, sem dados sensíveis.
