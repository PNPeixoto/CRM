# Configurar o canal Telegram

Este guia cobre o caminho de uma mensagem real ate a caixa de entrada usando o
adaptador Telegram Bot API 10.2. O Telegram exige HTTPS para o webhook.

## 1. Criar o bot

Converse com [@BotFather](https://t.me/BotFather), execute `/newbot` e guarde o
token entregue. Ele autentica o CRM perante o Telegram e deve ser tratado como
segredo. Em caso de exposicao, revogue-o imediatamente com `/revoke`.

## 2. Preparar as chaves

Em producao, gere chaves independentes:

```bash
openssl rand -base64 32 # CHANNEL_SECRET_KEY
openssl rand -base64 32 # MEDIA_SIGNING_KEY
```

`CHANNEL_SECRET_KEY` cifra credenciais e eventos brutos com AES-256-GCM.
`MEDIA_SIGNING_KEY` assina URLs curtas de download e nunca deve reutilizar a
mesma chave. O CRM gera um segredo exclusivo para o webhook ao criar o canal,
cifra-o e nunca o devolve ao navegador.

## 3. Expor o backend

O endpoint precisa estar acessivel por uma URL HTTPS publica. Em
desenvolvimento, o perfil `telegram` inicia um Quick Tunnel temporario:

```bash
docker compose --profile telegram up -d telegram-tunnel
```

Leia a URL `https://...trycloudflare.com` nos logs do servico e configure-a em
`TELEGRAM_PUBLIC_BASE_URL`. Quick Tunnels servem apenas para desenvolvimento e
o endereco muda quando o container do tunel e recriado.

Configure a raiz publica, sem barra final:

```dotenv
TELEGRAM_PUBLIC_BASE_URL=https://seu-endereco-publico.example
TELEGRAM_RECONCILIATION_ENABLED=true
TELEGRAM_MEDIA_STORAGE_PATH=./var/media-quarantine
```

## 4. Criar a conexao

Use a pagina **Integracoes** com uma conta que tenha permissao tenant-wide para
criar o canal Telegram e gravar somente o token do bot. A aplicacao gera o
segredo do webhook e cifra ambas as credenciais antes de persistir; nao insira
ciphertext manualmente no banco e nao cole o token em chat, log ou arquivo.

O identificador da conexao compoe a URL:

```text
https://seu-endereco-publico.example/api/webhooks/telegram/{connectionId}
```

## 5. Registrar e reconciliar o webhook

A propria integracao registra o webhook com o segredo configurado. Quando a
reconciliacao esta ativa, o worker compara periodicamente `getWebhookInfo` com
a URL esperada e repara divergencias sem descartar updates pendentes.

A tela atualiza o estado automaticamente: **Confirmando**, **Sincronizado** ou
um aviso sanitizado. O texto de erro remoto e os segredos nao chegam ao
navegador.

Para uma conferencia operacional direta:

```bash
curl "https://api.telegram.org/bot<SEU_TOKEN>/getWebhookInfo"
```

Observe `pending_update_count`, `last_error_date` e `last_error_message`. O CRM
persiste apenas o estado sanitizado, a contagem e a data; o texto remoto nao e
armazenado.

## 6. Testar entrada e saida

Envie uma mensagem ao bot. O caminho esperado e:

1. Telegram envia o header `X-Telegram-Bot-Api-Secret-Token` e o corpo bruto.
2. O CRM compara o segredo em tempo constante e responde rapidamente.
3. O corpo e cifrado; id externo ou SHA-256 torna o replay idempotente.
4. O worker traduz o update e grava conversa e mensagem normalizadas.
5. A resposta criada no inbox passa pela fila de saida e recebe o id externo.

Falhas temporarias, inclusive HTTP 429, respeitam `retry_after`. Falhas
permanentes e itens que esgotam tentativas ficam em dead letter.

## 7. Midia

Fotos, documentos, voz, audio e video suportados sao baixados por streaming,
limitados a 20 MiB e validados pelo conteudo real. Arquivos aceitos entram como
`QUARANTINED` sob uma raiz privada, com nome aleatorio.

Um scanner externo precisa promover uma midia limpa para `AVAILABLE`. Antes
disso ela nao pode ser servida. Depois da promocao, a API gera uma URL HMAC de
cinco minutos, vinculada ao tenant e ainda protegida por autenticacao e
permissao. HTML, SVG, executaveis e formatos desconhecidos sao rejeitados.

## 8. Diagnostico e recuperacao

**403 no webhook:** segredo divergente.

**404 no webhook:** conexao inexistente, inativa, excluida ou de outro tenant.
O retorno indistinguivel evita enumeracao.

**200 sem mensagem no inbox:** consulte o evento pelo tenant. O payload em
claro nao existe no banco; use metadados, `failure_reason`, hash e correlacao.

**Dead letter de entrada ou saida:** recupere pelas funcoes tenant-scoped, sem
alterar a fila diretamente:

```sql
SELECT reprocessar_evento_entrada('<event-id>');
SELECT reprocessar_mensagem_saida('<message-id>');
```

**Mensagem parada em `PENDING`:** confirme o worker de saida, a credencial
`TELEGRAM_BOT_TOKEN`, o Redis e os limites por tenant/conexao.

**Status remoto degradado:** confira URL publica, DNS/TLS e a saida para
`api.telegram.org`. A reconciliacao nunca registra token, segredo ou corpo
bruto em log.

Mais detalhes operacionais estao em
[`backend/OPERACAO-OMNICHANNEL.md`](backend/OPERACAO-OMNICHANNEL.md).
