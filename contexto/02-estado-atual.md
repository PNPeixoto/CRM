# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-08-09 14:20 (America/Montevideo)

## Onde parei

Os Prompts backend 00–17 e frontend F0–F6/F8 estão concluídos. A suíte backend
fechou em **193 testes, zero falhas**, o que encerrou a revisão do Prompt 17.
Os Gates A, B e D estão fechados. O Gate C ainda aguarda a jornada crítica E2E
reproduzível.

O ambiente local está na **V22**, aplicada em 2026-08-09 com backup prévio e
sem perda de dado. `frontend:F9` depende de evidência real de volume e não deve
ser executado somente por ordem numérica.

## Produto e segurança implementados

- PostgreSQL 17 e Redis 7, imagem Temurin 25 não privilegiada, profiles
  separados e produção fail-closed. Papéis de migração/runtime, RLS
  `ENABLE + FORCE`, integridade composta e tenant derivado só da identidade.
- Modelo organizacional com memberships temporais, papéis, permissões e
  alcances; a UI identifica o responsável de contato, tarefa e oportunidade.
- Argon2id com pepper, JWT curto, refresh rotativo, detecção de reuso, logout,
  reset de senha e MFA TOTP/recovery code para papéis privilegiados.
- Autorização central por ação e registro; listagens aplicam alcance antes da
  paginação. Recursos coletivos exigem alcance `TENANT`.
- Onboarding atômico por segmento materializa um funil só e não reescreve dado
  personalizado quando a apresentação muda.
- OpenAPI 3.1 determinístico, erros RFC 9457, correlação, limite de corpo,
  CORS, CSRF e rate limit nas superfícies públicas.
- Frontend com access token somente em memória, refresh single-flight, cache
  remoto segregado, formulários acessíveis e identificação do responsável.

## Omnichannel — backend 12 a 14 e frontend F8

- V15 adiciona leases, recuperação, dead letter, payload cifrado e retenção;
  `usage_event` é tenant-scoped e append-only.
- Telegram Bot API 10.2 tem timeout, classificação de falha, respeito a 429,
  limite Redis, webhook secreto e reconciliação sem descarte.
- V17 mantém mídia Telegram em quarentena privada, valida magic bytes e limita
  a 20 MiB. Somente `AVAILABLE` recebe URL HMAC curta e autenticada.
- V19 adiciona paginação keyset, versão otimista, stream recuperável e SLA de
  primeira resposta idempotente.
- WebSocket valida origem, CONNECT, sessão e SUBSCRIBE e encerra o transporte
  em logout, revogação ou expiração. Push nunca carrega texto do cliente.
- O runbook operacional está em `backend/OPERACAO-OMNICHANNEL.md`.

## Evolution API experimental — V18

- `WHATSAPP_EVOLUTION` é exclusivo do laboratório local e não substitui o
  `WHATSAPP_CLOUD` de produção. Compose fixa a 2.3.7 com PostgreSQL, Redis e
  volumes próprios em `127.0.0.1:8081`.
- Entrada `MESSAGES_UPSERT`, saída de texto, webhook e reconciliação usam o
  mesmo pipeline idempotente do CRM.
- `pnp-teste` está **pareada e `HEALTHY`**: entrada e saída provadas na Inbox em
  2026-08-09, uma tentativa cada, sem retry nem dead letter.
- O pareamento passou a existir no CRM: `POST /api/canais/{id}/pareamento` cria
  a instância se faltar, devolve QR e código, exige `CHANNELS_WRITE`, audita
  como credencial e recusa reparear sessão já aberta. **Ainda não implantado** —
  subir exige aceitar a V22.
- Um canal órfão (`teste` → instância inexistente `peixoto`) segue em `ERROR`
  por decisão, reconciliando a cada dez segundos contra um nome que não existe.

## Motor de automações — backend 15

- V20 adiciona definições e versões imutáveis, execuções, passos, compensações
  e transições append-only, todos tenant-scoped com RLS forçado.
- Ações e gatilhos são versionados. Replay converge por chave idempotente e
  recursão, loops e fan-out inválido são rejeitados antes da ativação.
- Worker usa leases, limite de concorrência, quotas e deadline. Retry acontece
  somente quando a ação declara segurança para repetição.
- Pausa, retomada e cancelamento são idempotentes; dry-run não chama efeito
  externo.
- Efeitos reversíveis registram compensação; irreversíveis e efeitos em voo
  ficam marcados. A trilha não guarda payload, mensagem nem segredo.
  Semântica fixada em `ADR-0011`.

## Conector HTTP seguro — backend 16

- V21 adiciona conectores aprovados, segredos cifrados e tentativas
  sanitizadas, todos tenant-scoped com RLS forçado.
- A ação `HTTP_CONNECTOR_V1` recebe somente `connectorId`; origem, método,
  caminho, corpo, headers e limites pertencem ao conector aprovado.
- A/AAAA privados, reservados, locais e metadata são bloqueados. O snapshot DNS
  é fixado durante a conexão e redirects ficam desativados.
- Cliente dedicado usa TLS 1.2/1.3, timeout, teto de resposta, concorrência e
  orçamento por tenant/conector. Produção não sobe sem proxy de egress.
- Templates substituem só identificadores técnicos e não executam SpEL,
  JavaScript, shell, classe, arquivo ou rede. Segredo é write-only,
  AES-256-GCM e resolvido só no envio; request, response, token e URL
  renderizada não entram no preview. Decisão: `ADR-0012`.

## Auditoria corporativa — backend 17 em revisão, V22 aplicada

- V22 adiciona `audit_event` sem `updated_at`, com RLS forçado, paginação
  keyset, catálogo versionado e hash verificável. Aplicada em 2026-08-09.
- Runtime só consulta e insere — confirmado no banco após a aplicação:
  `INSERT, SELECT` e nada mais. `UPDATE`/`DELETE` é revogado e bloqueado por
  gatilho; retenção e expurgo pertencem ao Prompt 18.
- API interna tipada não aceita texto livre. Token, cookie, segredo, payload,
  mensagem, arquivo e URL renderizada não existem no schema.
- Credencial, configuração, role e exportação são fail-closed; negação é best
  effort e preserva o `403`. `/auditoria` exige `audit.read`, audita a própria
  leitura, mascara IDs e pagina no servidor. Decisão: `ADR-0013`.

## Migrations atuais

- V1–V9: CRM/canais/fila, eventos, perfil, integridade e privilégios mínimos.
- V10–V14: organização/escopos, sessão/MFA, responsáveis e idempotência.
- V15–V17: fundação operacional omnichannel, Telegram e mídia em quarentena.
- V18: adaptador experimental Evolution.
- V19: Inbox paginada, stream recuperável e SLA.
- V20: motor interno de automações.
- V21: conector HTTP seguro.
- V22: auditoria corporativa append-only.
- Seeds e dados demonstrativos existem somente no profile `dev`.

## Verificado nesta máquina

- Branch observada `main`; Windows, JDK 25.0.4, Node 24 e Docker disponíveis.
- Backend: **193 testes, 0 falhas**, com PostgreSQL e Redis reais. O lambda
  `void` que impedia a compilação foi corrigido em 2026-08-09.
- Evolution: 7 testes de contrato, OpenAPI, fronteira de módulos e
  `MigracaoDeAtualizacaoTest` verdes.
- Frontend: 130 testes, typecheck e lint verdes; três avisos conhecidos.
- CRM local saudável na **V22**. A aplicação da migration preservou todos os
  contadores (2 tenants, 3 usuários, 4 canais, 3 conversas, 32 mensagens, 1
  contato, 18 eventos) e `audit_event` recebeu apenas INSERT/SELECT no runtime.
- Telegram tunnel, Evolution API e dependências seguem ativos; `pnp-teste`
  permaneceu `open` durante o deploy.

## Próximo passo

1. Executar `backend:18` — LGPD e retenção. A V22 está aplicada, o Prompt 17
   fechou e o pareamento pelo CRM está no ar.
2. Manter F9 adiado até haver medição representativa.

## Riscos restantes

- Evolution é ponte experimental baseada em sessão WhatsApp Web; produção deve
  usar o adaptador oficial e credenciais próprias.
- O scanner antimalware é uma fronteira externa; sem promoção, a mídia continua
  indisponível por desenho.
- Alcance por unidade ainda não decide registros de domínio; UNIT permanece
  oculto até migration e backfill próprios.
- Broker STOMP em memória impede escala horizontal e será revisto no Prompt 28.
- O Gate E depende ainda de backup/restore medido, SLOs e CI/CD rastreável.
- Gate C aguarda runner E2E reproduzível (`frontend:F12`).
