# Sessao 2026-08-08 — backend:13 adaptador Telegram oficial

- Branch observada: `main`
- Ambiente: Windows, JDK 25, Docker disponivel
- Responsavel: Codex

## Provedor e contrato

Telegram foi mantido como primeiro provedor. O adaptador esta fixado na Bot API
10.2, versao oficial vigente em 2026-08-08, e falha na inicializacao se a
configuracao divergir. Fixtures versionadas cobrem o formato de update.

## Entregue

- transporte HTTP injetavel, timeouts e classificacao segura de erros;
- HTTP 429 respeita `parameters.retry_after`, com fallback para o header;
- limite distribuido Redis por tenant e conexao;
- validacao de URL HTTPS e do formato oficial do segredo do webhook;
- reconciliacao por `getWebhookInfo`, reparo sem descartar updates pendentes e
  persistencia somente de estado remoto sanitizado;
- chamadas ao provedor ficam fora da transacao que atualiza o estado local;
- V17 e servicos de midia: streaming autenticado, limite de 20 MiB, magic
  bytes, raiz privada, nome aleatorio, hash e quarentena tenant-scoped;
- HTML, SVG, executaveis, formato desconhecido e path traversal falham fechados;
- midia `QUARANTINED` nunca e servida. Somente `AVAILABLE`, apos scanner
  externo, recebe URL HMAC de cinco minutos ainda protegida por autenticacao e
  permissao;
- retencao segura remove apenas o arquivo resolvido sob a raiz configurada,
  retoma expurgo abandonado por lease e limpa arquivo novo no rollback;
- snapshot OpenAPI e tipos TypeScript regenerados.

## Evidencias

- contratos do adaptador cobrem sucesso oficial, 429, timeout sem vazamento,
  resposta malformada, estado remoto sanitizado e segredo invalido;
- API do webhook prova rejeicao sem persistencia e replay valido idempotente;
- quarentena prova formatos aceitos/rejeitados, nomes aleatorios e isolamento
  de caminho; HMAC prova vinculo a tenant, midia, expiracao e conteudo;
- suite completa compartilhada com o Prompt 12: 149 testes backend.

## Operacao e risco residual

Reconciliacao e retencao sao opt-in/configuraveis. Producao precisa de
`CHANNEL_SECRET_KEY`, `MEDIA_SIGNING_KEY`, URL HTTPS publica e armazenamento
privado persistente. O scanner antimalware nao foi embutido: a fronteira foi
deliberadamente deixada fail-closed, portanto anexos permanecem indisponiveis
ate a promocao operacional para `AVAILABLE`.

## Gate e proximo passo

O Prompt 13 esta concluido. O proximo backend canonico e `backend:14`; o Gate D
permanece aberto ate o Prompt 16.
