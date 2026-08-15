# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-08-15 10:15 (America/Sao_Paulo), branch `main`

## Onde parei

Os Prompts backend 00–19 e 21 e frontend F0–F6/F8 estão concluídos. O backend 20 foi adiado por decisão comercial (`ADR-0017`). A suíte backend tem **301 testes**, zero falhas; o frontend mantém **155 testes verdes**.
Gates A, B e D fechados; o Gate C ainda aguarda a jornada E2E reproduzível.

Fora da trilha, a Fase 4 comercial inclui papéis, equipes, preset de cinco funções, Agenda, Inbox identificada, dashboard e ficha 360º.
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

## Entitlements e medição — backend 19 (`ADR-0016`)

- V27 adiciona catálogo técnico, concessões versionadas sob RLS e fonte
  reconciliável ao ledger. Nenhum tenant recebe concessão ou limite por default.
- Evento atrasado usa a versão vigente na ocorrência. Agregação deriva janela e
  timezone explícitos; hard limit é atômico sem contador mutável paralelo.
- Contratação não concede permissão e navegação não protege API. Billing está
  desativado: a venda inicial é por implantação, sem preço recorrente fictício.

## Billing e relatórios — backend 20 e 21 (`ADR-0017`, `ADR-0018`)

- Backend 20 foi deliberadamente adiado. A fundação futura continua na V27,
  sem preço, fatura, pagamento, moeda ou provedor inventados.
- V28 cria catálogo versionado de 13 métricas e jobs de exportação sob RLS.
  Dashboard e CSV usam a mesma fotografia, fórmula, timezone, unidade e moeda.
- CSV assíncrono é idempotente, limitado e neutraliza fórmulas. O arquivo fica
  cifrado fora do web root; URL HMAC dura até 5 minutos e ainda exige sessão.
- `reports.read/TENANT` é revalidada ao processar, assinar e baixar. Revogação
  posterior ao pedido falha fechada; pedido, conclusão, cancelamento e download
  são auditados. Expiração respeita legal hold.

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
- Agenda real sobre tarefas; Inbox identifica canal, conta, contato e operadores;
  Kanban funciona por mouse, toque e teclado; ficha reúne carteira e atividades.
- Conversas ainda não entram na ficha: o domínio não publica o vínculo de modo
  confiável. Frontend: build, lint, `api:check` e 155 testes verdes.

## Migrations e verificação nesta máquina

- V1–V27 cobrem fundação, produto, canais, automações, auditoria, privacidade,
  equipes, Instagram e medição; V28 cobre relatórios. **28 migrations, 74
  caminhos OpenAPI.** Seeds e dados demonstrativos só no profile `dev`.
- A integração foi consolidada na branch `codex/integrar-versao-outra-maquina`;
  backend agora com **301 testes verdes** em Java 25 e frontend com **155**.
- A atualização V8→V28, V26→V28 e a instalação limpa V1→V28 passaram em
  PostgreSQL 17. OpenAPI e tipos TypeScript estão sincronizados.
- Fluxo de acessos exercitado no navegador contra o backend real: papel criado,
  atribuído com alcance de equipe e equipe montada, com os três eventos na
  trilha. O recorte em si continua provado por `AlcanceDeEquipeTest`, não pela
  tela — não houve sessão de um segundo usuário.

## Próximo passo

1. Avançar para `backend:22` (observabilidade e SLOs).
2. Decidir prazos de retenção; o expurgo segue desligado.
3. CRUD de etapas do funil continua pendente; conexão Instagram ficou pausada.

## Riscos restantes

- Evolution é ponte experimental; produção exige WhatsApp Cloud. Instagram
  oficial ainda depende de app/negócio Meta aprovados e não cobre mídia.
- Alcance `UNIT` segue sem registro de domínio e oculto (`ADR-0008`); `TEAM` é
  de um nível e não substitui recorte por filial.
- Revogação de acesso não é instantânea: vale na chamada seguinte à API, mas a
  sessão já emitida carrega o token por até 15 minutos. Ver também `SEC-011`.
- `PrivacidadeController` perde o evento `DENIED` ao reverter a transação.
- Storage de exportação é privado e cifrado, mas local a uma instância; escala
  horizontal exigirá storage compartilhado.
- STOMP em memória impede escala horizontal. Gate E depende de backup/SLO/CI;
  Gate C aguarda runner E2E reproduzível (`frontend:F12`).
