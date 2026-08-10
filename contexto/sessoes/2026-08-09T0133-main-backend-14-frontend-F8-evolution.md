# Sessao 2026-08-09 — backend:14, frontend:F8 e laboratorio Evolution

- Branch observada: `main`
- Ambiente: Windows, JDK 25, Node 24 e Docker Desktop
- Responsavel: Codex

## Escopo entregue

- V18 adiciona `WHATSAPP_EVOLUTION` como canal experimental de desenvolvimento,
  sem substituir `WHATSAPP_CLOUD`.
- Compose com Evolution API 2.3.7, PostgreSQL e Redis isolados, volumes
  persistentes e porta publicada somente em `127.0.0.1:8081`.
- Adaptador, traducao de `MESSAGES_UPSERT`, webhook tenant-scoped, envio de
  texto e reconciliacao da instancia/webhook.
- V19 adiciona versao otimista, `due_at`, eventos de SLA, cursor de stream e
  livro de eventos append-only com RLS.
- Conversas e mensagens usam paginacao keyset limitada. O endpoint de eventos
  recupera uma sequencia perdida ou solicita reset do snapshot quando o cursor
  ficou anterior a janela disponivel.
- WebSocket com allowlist de origem, JWT no CONNECT, permissao no SUBSCRIBE,
  heartbeat, limites de conexao/frequencia, fila limitada e fechamento em
  logout, revogacao ou expiracao.
- Cliente F8 usa REST como verdade, cursor somente em memoria, deduplicacao por
  ID/versao, backoff exponencial com jitter e polling adaptativo offline/background.
- A Inbox carrega paginas anteriores sem perder a ancora e anuncia apenas o
  estado de conectividade, sem conteudo privado em `aria-live`.

## Evidencias

- Backend: 164 testes, zero falhas, incluindo migration vazia e V8→V19,
  paginação sem duplicata, recuperação/reset do stream, SLA idempotente,
  limites/revogação STOMP e contratos Evolution 2.3.7.
- Frontend: 128 testes, build e `api:check` verdes.
- Lint verde com somente os tres avisos preexistentes de Fast Refresh.
- Evolution, PostgreSQL e Redis proprios iniciaram; instancia `pnp-teste` e
  canal CRM foram criados e o webhook `MESSAGES_UPSERT` foi reconciliado.

## Estado operacional

A API Evolution esta ativa, mas a instancia WhatsApp permanece `close` ate um
novo QR ser lido pelo aparelho. Esse passo humano e a unica pendencia para o
teste real de entrada/saida pelo WhatsApp. Telegram e Evolution podem continuar
rodando juntos; nenhum volume do CRM foi apagado.

## Gate e proximo passo

`backend:14` e `frontend:F8` estao concluidos. O Gate D permanece aberto ate
`backend:16`. O proximo prompt backend canonico e `backend:15` (motor interno de
automacoes). `frontend:F9` continua condicionado a evidencia de volume e nao
deve ser executado apenas por sequencia.
