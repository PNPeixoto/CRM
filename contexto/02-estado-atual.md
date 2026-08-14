# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-08-10 22:57 (America/Montevideo), commit `838461d`

## Onde parei

Os Prompts backend 00–18 e frontend F0–F6/F8 estão concluídos, com **267 testes
no backend e 140 no frontend**, zero falhas. Gates A, B e D fechados; o Gate C
ainda aguarda a jornada crítica E2E reproduzível.

Fora da trilha numerada, a **Fase 4 do plano de MVP comercial** foi entregue:
administração delegada de papéis, alcance de equipe e a tela `/acessos`. Ela não
corresponde a nenhum prompt, e por isso os dois manifestos permanecem
inalterados — o roteiro segue com `backend:19` como próximo item.

O ambiente local está na **V25**, aplicada com backup prévio, prova em contêiner
descartável e sem perda de dado. O expurgo permanece **desligado**: nenhum prazo
foi decidido, e o worker não sobe sem eles. `frontend:F9` depende de evidência
real de volume e não deve ser executado só por ordem numérica.

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

## Migrations e verificação nesta máquina

- V1–V9 fundação; V10–V14 organização, sessão/MFA e idempotência; V15–V17
  omnichannel, Telegram e mídia em quarentena; V18–V21 Evolution, Inbox com SLA,
  automações e conector HTTP; V22 auditoria; V23 retenção e legal hold; V24
  legal hold imutável; V25 equipe e alcance de equipe. **25 migrations, 65
  rotas.** Seeds e dados demonstrativos só no profile `dev`.
- Branch `main` sincronizada com `origin/main` em `838461d`; Windows, JDK
  25.0.4, Node 24 e Docker. Backend **267 testes** e frontend **140**, ambos sem
  falha, com PostgreSQL e Redis reais — Evolution, retenção, direitos do
  titular, alcance de equipe, não escalonamento, OpenAPI, fronteira de módulos
  e migração, todos verdes.
- CRM local saudável na **V25**, com contadores idênticos à linha de base.
  `team_member` tem RLS forçado, e o `DELETE` foi recusado na verificação.
- Fluxo de acessos exercitado no navegador contra o backend real: papel criado,
  atribuído com alcance de equipe e equipe montada, com os três eventos na
  trilha. O recorte em si continua provado por `AlcanceDeEquipeTest`, não pela
  tela — não houve sessão de um segundo usuário.

## Próximo passo

1. Decidir os prazos de retenção por categoria para poder ligar o expurgo.
2. Semear os papéis de partida (SDR, Closer, Atendente, Gestor, Gerente) como
   papéis comuns editáveis, para o cliente não começar do zero.
3. Fase 1 do plano de MVP: CRUD de etapas do funil — `/api/funis` é só leitura.
4. Executar `backend:19` (entitlements e medição); F9 segue adiado até haver
   medição representativa.

## Riscos restantes

- Evolution é ponte experimental de sessão WhatsApp Web; produção exige o
  adaptador oficial. Sem promoção pelo scanner externo, a mídia continua
  indisponível por desenho.
- Alcance por **unidade** continua sem decidir registro de domínio, e `UNIT`
  segue oculto na API (`ADR-0008`). O alcance intermediário que existe é o de
  **equipe**, de um nível só, e ele não substitui recorte por filial.
- Revogação de acesso não é instantânea: vale na chamada seguinte à API, mas a
  sessão já emitida carrega o token por até 15 minutos. Ver também `SEC-011`.
- `PrivacidadeController` audita a recusa por legal hold **dentro** da transação
  revertida, então o registro `DENIED` é descartado. Correção em sessão própria.
- Broker STOMP em memória impede escala horizontal; revisto no Prompt 28. Gate E
  depende de backup/restore medido, SLOs e CI/CD rastreável; Gate C aguarda
  runner E2E reproduzível (`frontend:F12`).
