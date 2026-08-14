# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-08-14 19:53 (America/Sao_Paulo), branch `agent/refino-apresentacao`, head anterior `0b78d22`

## Onde parei

Os Prompts backend 00–18 e frontend F0–F6/F8 estão concluídos. A última suíte backend tinha **267 testes**; três casos novos exigem JDK 25. O frontend tem **147 testes**, zero falhas.
Gates A, B e D fechados; o Gate C ainda aguarda a jornada E2E reproduzível.

Fora da trilha, a Fase 4 comercial inclui papéis, equipes, preset de cinco funções, Agenda, Inbox identificada, dashboard e ficha 360º. Os manifestos não mudaram; `backend:19` segue como próximo prompt.
O expurgo continua desligado até a decisão dos prazos de retenção.

## Produto e segurança implementados

- PostgreSQL 17 e Redis 7, imagem Temurin 25 não privilegiada, produção
  fail-closed. Papéis de migração/runtime, RLS `ENABLE + FORCE` e tenant
  derivado só da identidade autenticada.
- Memberships temporais, papéis, permissões e alcances. Argon2id com pepper,
  JWT curto, refresh rotativo com detecção de reuso, reset de senha e MFA TOTP
  para papéis privilegiados. Autorização central por ação e registro, com o
  alcance aplicado **na consulta**; recurso coletivo exige `TENANT`.
- Onboarding atômico por segmento materializa um funil só. OpenAPI 3.1
  determinístico, erros RFC 9457, correlação, limite de corpo, CORS, CSRF e
  rate limit nas superfícies públicas. Frontend com token só em memória, refresh
  single-flight e cache segregado por contexto.

## Omnichannel — backend 12 a 14 e frontend F8

- V15: leases, recuperação, dead letter, payload cifrado e retenção. V17 mantém
  mídia Telegram em quarentena privada, valida magic bytes e limita a 20 MiB; só
  `AVAILABLE` recebe URL HMAC curta. V19: paginação keyset, versão otimista,
  stream recuperável e SLA de primeira resposta idempotente.
- Telegram Bot API 10.2 com timeout, classificação de falha, respeito a 429,
  limite Redis, webhook secreto e reconciliação sem descarte.
- WebSocket valida origem, CONNECT, sessão e SUBSCRIBE, e encerra o transporte
  em logout, revogação ou expiração. Push nunca carrega texto do cliente.
- Runbook em `backend/OPERACAO-OMNICHANNEL.md`.

## Evolution API experimental — V18

- `WHATSAPP_EVOLUTION` é **laboratório local** e não substitui o
  `WHATSAPP_CLOUD` de produção. Entrada, saída, webhook e reconciliação usam o
  pipeline idempotente do CRM; `pnp-teste` está pareada e `HEALTHY`, provada na
  Inbox em 2026-08-09.
- `POST /api/canais/{id}/pareamento` cria a instância se faltar, devolve QR e
  código, exige `CHANNELS_WRITE`, audita como credencial e recusa reparear
  sessão aberta. Um canal órfão segue em `ERROR` por decisão.

## Automações e conector HTTP — backend 15 e 16 (`ADR-0011`, `ADR-0012`)

- V20: definições e versões imutáveis, execuções e transições append-only.
  Replay converge por chave idempotente; recursão, loops e fan-out inválido são
  rejeitados antes da ativação. Worker usa leases, quotas e deadline, e só
  repete ação que declara segurança para repetição. Efeito reversível registra
  compensação; o irreversível fica marcado.
- V21: a ação `HTTP_CONNECTOR_V1` recebe **somente** `connectorId` — método,
  caminho, corpo, headers e limites pertencem ao conector aprovado. A/AAAA
  privados, reservados e metadata bloqueados, snapshot DNS fixado, redirects
  desativados, e produção não sobe sem proxy de egress.
- Templates substituem só identificadores técnicos e não executam SpEL, shell,
  classe, arquivo ou rede. Segredo é write-only, AES-256-GCM, resolvido só no
  envio, e não entra no preview.

## Auditoria — backend 17 (`ADR-0013`); privacidade e retenção — backend 18

- V22: `audit_event` append-only, RLS forçado e hash verificável; V24 dá o mesmo
  tratamento ao `legal_hold`. Nos dois, o runtime só consulta e insere. Credencial,
  configuração, papel e exportação são fail-closed; negação é best effort e
  preserva o `403`. `/auditoria` audita a própria leitura.
- V23: legal hold sob RLS e seis funções de expurgo por categoria. Nenhuma chama
  `now()` — o corte é parâmetro, o que torna a retenção verificável com relógio
  controlado. **Prazos `A VALIDAR` e worker desligado por padrão.**
- `/api/privacidade` exporta o dado do titular dizendo origem, finalidade e
  prazo de cada seção, e anonimiza sob demanda. Legal hold produz recusa
  fundamentada, nunca falha silenciosa. Exige `privacy.manage`.
- Inventário e política de backup em `contexto/privacidade/`. A API de auditoria
  é tipada: token, segredo, payload e mensagem não existem no schema.

## Administração de acessos — Fase 4 do plano de MVP

- O modelo de papéis existia sob RLS desde a V10 e não tinha API. Seis rotas
  fecham a lacuna **sem migration**: CRUD de papel, permissões, membros,
  atribuição e revogação. Decisão: `ADR-0014`.
- `GuardaDeConcessao` sustenta a invariante de não escalonamento: conceder exige
  possuir; conceder exige alcance ao menos igual, **verificado por permissão**;
  editar exige conter. O privilégio do tenant nunca cresce por delegação.
  **Definir papel exige possuir a permissão; atribuir exige o alcance** — o
  papel não concede nada até ser atribuído.
- Quatro recusas: papel de sistema imutável, última atribuição de sistema
  preservada, papel em uso não sai em cascata, código duplicado recusado com
  mensagem. Recusa **não** entra na trilha: a exceção reverte a transação, e o
  registro seria descartado junto.
- V25 traz `team_member` e o alcance `TEAM`. O recorte é por **equipe**, não por
  unidade — `contact`, `deal` e `task` já carregam `owner_user_id` indexado
  desde a V5, então não há coluna nova em domínio nem backfill inventado
  (`ADR-0015`). Composição encerra por `valid_until`, sem `DELETE` no runtime.
  `Autorizacao.recorteDe` resolve o recorte num lugar só, e as três listagens o
  aplicam **na consulta**.
- Tela `/acessos` com Papéis, Pessoas e Equipes: desabilita o que o backend
  recusaria, sem que isso seja a proteção.
- Preset comercial idempotente cria SDR, Closer, Atendente, Gestor de
  atendimento e Gerente comercial sem sobrescrever edições do cliente.

## Refino para apresentação
- `/agenda` é mensal e real sobre tarefas; a Inbox identifica canal, conta, contato/número, atendente, autor e operador que responde.
- A identidade FinUp e o cenário local de dashboard, contatos, funil, Agenda e Inbox são servidos por `npm run demo:api`.
- A ficha reúne cadastro, carteira e atividades; oportunidades e tarefas são filtradas no backend pelo contato e alcance autorizado.
- Conversas ainda não entram na ficha: o domínio não popula nem publica esse vínculo de forma confiável.
- OpenAPI/tipos sincronizados; build, lint, `api:check`, 147 testes e revisão visual desktop/móvel passaram sem overflow.

## Migrations e verificação nesta máquina

- V1–V9 fundação; V10–V14 organização, sessão/MFA e idempotência; V15–V17
  omnichannel, Telegram e mídia em quarentena; V18–V21 Evolution, Inbox com SLA,
  automações e conector HTTP; V22 auditoria; V23 retenção e legal hold; V24
  legal hold imutável; V25 equipe e alcance de equipe. **25 migrations, 67
  rotas.** Seeds e dados demonstrativos só no profile `dev`.
- Base `main` em `32bfc0b`; backend histórico com **267 testes verdes**. O host tem Java 21 e o projeto exige 25; frontend com **147 testes verdes**.
- CRM local saudável na **V25**, com contadores idênticos à linha de base.
  `team_member` tem RLS forçado, e o `DELETE` foi recusado na verificação.
- Fluxo de acessos exercitado no navegador contra o backend real: papel criado,
  atribuído com alcance de equipe e equipe montada, com os três eventos na
  trilha. O recorte em si continua provado por `AlcanceDeEquipeTest`, não pela
  tela — não houve sessão de um segundo usuário.

## Próximo passo

1. Decidir os prazos de retenção por categoria para poder ligar o expurgo.
2. Rodar os três novos testes de integração em JDK 25 e fazer o ensaio final.
3. Fase 1 do plano de MVP: CRUD de etapas do funil — `/api/funis` é só leitura.
4. Executar `backend:19` (entitlements e medição); F9 segue adiado até haver medição representativa.

## Riscos restantes

- Evolution é ponte experimental de sessão WhatsApp Web; produção exige o
  adaptador oficial. Sem promoção pelo scanner externo, a mídia continua
  indisponível por desenho.
- Alcance por **unidade** continua sem decidir registro de domínio, e `UNIT`
  segue oculto na API (`ADR-0008`). O alcance intermediário que existe é o de
  **equipe**, de um nível só, e ele não substitui recorte por filial.
- Revogação de acesso não é instantânea: vale na chamada seguinte à API, mas a
  sessão já emitida carrega o token por até 15 minutos. Ver também `SEC-011`.
- Oportunidades e tarefas da ficha são filtradas e indexadas, mas ainda não têm paginação; histórico extremo exigirá contrato paginado.
- `PrivacidadeController` audita a recusa por legal hold **dentro** da transação
  revertida, então o registro `DENIED` é descartado. Correção em sessão própria.
- Broker STOMP em memória impede escala horizontal; revisto no Prompt 28. Gate E
  depende de backup/restore medido, SLOs e CI/CD rastreável; Gate C aguarda
  runner E2E reproduzível (`frontend:F12`).
