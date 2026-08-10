# Operacao omnichannel

Este documento cobre a operacao introduzida pelos Prompts 12 a 16, alem do
laboratorio local da Evolution API. O contrato de dados correspondente esta
nas migrations V15 a V21.

## Segredos e configuracao

- `CHANNEL_SECRET_KEY`: AES-256-GCM, em base64, usado por credenciais de canal
  e pelo corpo bruto dos eventos recebidos.
- `MEDIA_SIGNING_KEY`: HMAC independente, com ao menos 32 bytes em base64,
  usado somente nas URLs de download de midia.
- `TELEGRAM_PUBLIC_BASE_URL`: URL HTTPS publica do backend, sem caminho final.
- `TELEGRAM_RECONCILIATION_ENABLED`: ativa a reconciliacao periodica do
  webhook. O default e `false` para evitar chamadas externas acidentais.
- `TELEGRAM_MEDIA_STORAGE_PATH`: raiz privada da quarentena de anexos.
- `EVOLUTION_ENABLED`: habilita somente o adaptador experimental de
  desenvolvimento; fora do Compose local o default e `false`.
- `EVOLUTION_API_BASE_URL`, `EVOLUTION_API_KEY` e `EVOLUTION_WEBHOOK_BASE_URL`:
  acesso servidor-servidor e callback interno da Evolution.
- `HTTP_CONNECTOR_SECRET_KEY`: AES-256 exclusivo dos Bearer tokens e API keys
  dos conectores HTTP.
- `HTTP_CONNECTOR_EGRESS_PROXY_URL`: proxy obrigatorio em producao. Ele deve
  repetir bloqueio de redes privadas/metadata e pinning DNS ao resolver o
  destino do `CONNECT`.

Segredos nunca devem ser registrados em log, enviados ao frontend ou
reutilizados entre finalidades.

## Entrada e idempotencia

O webhook valida `X-Telegram-Bot-Api-Secret-Token` em tempo constante e recebe
o corpo como bytes. O evento e identificado por id externo; quando ele nao
pode ser extraido, usa-se o SHA-256 do corpo para convergir replays. O payload
e cifrado antes de persistir e o texto em claro nao e gravado.

Reservas de entrada e saida usam lease com `FOR UPDATE SKIP LOCKED`. Ao esgotar
o limite, o item vai para dead letter. A recuperacao operacional e feita pelas
funcoes tenant-scoped:

```sql
SELECT reprocessar_evento_entrada('<event-id>');
SELECT reprocessar_mensagem_saida('<message-id>');
```

Nao altere contadores ou datas diretamente: as funcoes preservam o isolamento
por tenant e limpam o lease de forma atomica.

## Retencao do payload recebido

O corpo cifrado e temporario. O worker remove o ciphertext vencido, preserva o
hash e os metadados necessarios para idempotencia e auditoria operacional. O
prazo padrao e sete dias. Para uma execucao manual controlada, o papel
migrador/operacional pode chamar:

```sql
SELECT expurgar_payloads_eventos_recebidos(500);
```

## Telegram Bot API

O adaptador esta fixado na Bot API 10.2 e falha na inicializacao se a versao
configurada divergir. Respostas 429 respeitam `retry_after`; erros temporarios
mantem a mensagem na fila e erros permanentes vao para dead letter. O limite
distribuido usa Redis por tenant e por conexao.

A reconciliacao compara o `getWebhookInfo` com a URL esperada e repara somente
o necessario. Ela nunca usa `drop_pending_updates` e persiste apenas estado
sanitizado, contagem pendente e data da ultima falha.

## WhatsApp experimental com Evolution API

O profile `evolution` sobe a Evolution API 2.3.7, PostgreSQL e Redis proprios.
Esses volumes nao compartilham dados com o CRM. O adaptador oficial
`WHATSAPP_CLOUD` continua reservado para producao; `WHATSAPP_EVOLUTION` existe
para laboratorio local e nao o substitui.

Para iniciar ou retomar o laboratorio sem apagar volumes:

```powershell
docker compose --profile evolution up -d evolution-api app
```

Cadastre o canal na tela Integracoes informando o nome da instancia. Em
seguida use **Conectar WhatsApp** no cartao do canal: esse botao cria a
instancia se ela ainda nao existir e devolve o QR e o codigo de pareamento. A
leitura no aparelho continua sendo acao humana. QR expirado deve ser
solicitado novamente; nunca registre o conteudo do QR, a API key ou
credenciais do canal em log.

A **reconciliacao nao cria instancia** — ela apenas repara o webhook e observa
o estado da conexao. Criar sessao de WhatsApp e ato deliberado de uma pessoa;
um worker que criasse sozinho recriaria a instancia toda vez que alguem
apagasse, sem ninguem pedir. Ate 2026-08-09 este documento afirmava o
contrario, e quem o seguia esperava uma instancia que nunca aparecia: o
resultado era um canal preso em `ERROR`, reconciliando a cada dez segundos
contra um nome inexistente.

O pareamento **nunca reconecta uma sessao saudavel**. Com a instancia em
`open`, o endpoint devolve o estado e recusa emitir material novo — pedir
reiniciaria o pareamento no provedor e derrubaria um WhatsApp em atendimento.

O estado `DEGRADED` enquanto a instancia esta `connecting` ou `close` e
esperado. Somente `open` confirma que testes de entrada e saida representam o
canal real.

O manager web da Evolution (`/manager`) **nao funciona nesta configuracao**: a
`CORS_ORIGIN` esta restrita, e navegacao de topo do navegador nao envia
cabecalho `Origin`, entao a API responde 500. Isso e deliberado — o pareamento
pertence ao CRM, que aplica permissao, auditoria e isolamento por tenant. Nao
afrouxe a `CORS_ORIGIN` para usar o manager: seria trocar um caminho auditado
por outro que entrega a chave de administracao do provedor a quem so precisa
conectar um numero.

## Inbox, stream e SLA

A V19 adiciona paginacao por cursor/keyset para conversas e mensagens, versao
otimista dos recursos, sequencia monotonica por tenant e o livro append-only
`realtime_event`. O cliente recupera `/api/conversas/eventos?apos=N` apos uma
lacuna e refaz o snapshot REST quando `resetObrigatorio=true`.

O WebSocket aceita apenas origens configuradas, autentica `CONNECT`, autoriza
cada `SUBSCRIBE`, limita conexoes/frames e encerra o transporte em logout,
revogacao ou expiracao do access token. O payload do push contem somente IDs,
tipo, versao, sequencia e horario — nunca o texto da mensagem.

O SLA de primeira resposta persiste `due_at` e os eventos imutaveis `STARTED`,
`SATISFIED` e `BREACHED`. O verificador reserva lotes com `SKIP LOCKED`, executa
sob o RLS de cada tenant e converge por chave idempotente. O fuso IANA fica em
`tenant_profile.time_zone`.

## Midia

Anexos Telegram sao baixados por streaming autenticado, limitados a 20 MiB,
validados por magic bytes e gravados sob uma raiz privada com nome aleatorio.
HTML, SVG, executaveis e formatos desconhecidos sao rejeitados. O estado
inicial e `QUARANTINED` e nenhum endpoint entrega esse arquivo.

Um scanner externo deve inspecionar o arquivo e promover a linha para
`AVAILABLE`. Somente entao a API gera uma URL HMAC vinculada ao tenant, midia e
expiracao de cinco minutos. O download exige autenticacao e permissao, envia
`Content-Disposition: attachment` e `Cache-Control: private, no-store`.

O worker de retencao reserva metadados vencidos com lease, valida o caminho
contra a raiz configurada, remove o arquivo exato e marca a linha como
`PURGED`. Uma falha pode ser retomada quando o lease vencer. Se a transacao de
ingestao for revertida depois da gravacao, o arquivo novo e removido.

## Motor interno de automacoes

A V20 introduz definicoes versionadas, execucoes, passos, compensacoes e uma
trilha append-only de transicoes. Todas essas tabelas recebem tenant, RLS
forcado e referencias compostas. O worker global reserva somente IDs por uma
funcao estreita; cada item volta ao `TenantContext` antes de ler ou alterar
dados.

Configuracoes operacionais ficam sob `CRM_AUTOMATION_*`: habilitacao do worker,
intervalo, tamanho do lote, duracao do lease e limites de quota. Em testes o
worker deve permanecer desabilitado. Reduzir lease ou deadline exige observar
o maior tempo real das acoes para nao classificar efeito ainda em voo como
falha.

Somente acoes que declaram retry seguro podem ser repetidas. Dry-run valida e
registra o plano, mas nunca chama handler externo. Cancelamento, timeout ou
falha registram compensacao para efeito reversivel e `IMPOSSIBLE` para efeito
irreversivel ou externo em voo. A compensacao e um registro operacional; sua
execucao automatica nao faz parte do Prompt 15.

Diagnostico deve usar status, `reason_code`, IDs, versao e horarios presentes
nas transicoes. Payload de gatilho, texto de mensagem, resposta externa e
segredos nao pertencem a essas tabelas nem aos logs. Mudanca nessa semantica
exige nova decisao arquitetural.

## Conector HTTP seguro

A V21 armazena somente destinos previamente aprovados. A origem e HTTPS e
estatica; o passo da automacao referencia o `connectorId` e nao recebe URL,
metodo, headers ou linguagem de expressao. `POST`, `PUT` e `PATCH` exigem um
header de idempotencia; `GET` nao aceita corpo.

O executor valida todos os A/AAAA, rejeita o destino se qualquer IP for
privado, reservado, local, multicast ou metadata, e fixa o snapshot DNS na
conexao. Redirects, cookies, retry automatico e descompressao ficam
desativados. Timeout, resposta, concorrencia e requisicoes por minuto possuem
limites globais e por conector.

Em producao, a ausencia de `HTTP_CONNECTOR_EGRESS_PROXY_URL` impede a
inicializacao. O proxy nao e apenas transporte: precisa aplicar a mesma
politica anti-SSRF e impedir uma nova resolucao insegura. Validar apenas no CRM
e deixar o proxy resolver livremente reabriria DNS rebinding.

Credenciais sao write-only e cifradas. Preview mostra apenas metodo, template
de destino, tipo e nomes de headers. A tabela de tentativas guarda hashes,
status, codigo de falha, bytes e duracao; request, response, segredo e URL
renderizada nao podem entrar no diagnostico ou log.

Codigos `HTTP_BUDGET_EXCEEDED`, `HTTP_CONCURRENCY_LIMIT`, `HTTP_TIMEOUT` e
`HTTP_DNS_FAILURE` sao transitorios. Destino bloqueado, redirect, resposta
excessiva e rejeicao 4xx (exceto 408/425/429) sao permanentes. Nao altere uma
tentativa concluida para forcar replay; crie uma nova execucao controlada.

## Medicao

`usage_event` e append-only e mede entrada aceita, saida entregue e bytes de
midia armazenados. UPDATE e DELETE sao bloqueados pelo banco; produtores usam
chave idempotente para evitar dupla contagem.

## Alertas minimos

- crescimento de itens em dead letter;
- `remote_status` Telegram em `ERROR` ou `DEGRADED`;
- `remote_pending_count` crescente;
- itens `QUARANTINED` antigos sem decisao do scanner;
- falhas repetidas nos workers de retencao;
- saturacao do limite por tenant ou conexao.
- lacunas frequentes ou `resetObrigatorio` no stream da Inbox;
- SLA vencido crescente ou worker sem reservas processadas;
- Evolution em `DEGRADED`/`ERROR` depois de concluido o pareamento.
