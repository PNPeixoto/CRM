# Configurar o canal Telegram

Do zero até uma mensagem real aparecendo na caixa de entrada. **Não exige
CNPJ, verificação de negócio nem cartão de crédito** — é essa a razão de o
Telegram vir antes do WhatsApp (ver `contexto/03-decisoes.md`, 2026-07-29).

**Pré-requisito:** os testes de integração passando. Ver
[`SETUP-LINUX.md`](SETUP-LINUX.md).

---

## 1. Criar o bot

No Telegram, converse com [@BotFather](https://t.me/BotFather):

```
/newbot
```

Ele pede um nome de exibição e um username terminado em `bot`. No fim entrega
um token no formato:

```
1234567890:AAF...
```

> **Este token é a credencial completa do bot.** Quem o tiver lê e envia
> mensagens como você. Ele não vai para o `.env` nem para arquivo nenhum: será
> gravado cifrado no banco (AES-256-GCM), como toda credencial de canal.
> Se vazar, revogue imediatamente com `/revoke` no @BotFather.

---

## 2. Gerar a chave de cifra das credenciais

A aplicação recusa subir sem ela, fora do profile `dev`.

```bash
openssl rand -base64 32
```

Coloque em `CHANNEL_SECRET_KEY` no seu `.env`. Precisa decodificar para
**exatamente 32 bytes** — o cofre valida isso na subida, porque chave curta
produz criptografia que funciona e não protege.

> Em `dev` há um valor padrão marcado `TESTE — TROCAR ANTES DE PRODUÇÃO`.
> Trocar a chave torna ilegíveis as credenciais já gravadas com a anterior.

---

## 3. Expor o backend para a internet

O Telegram precisa alcançar seu webhook por **HTTPS**. Em desenvolvimento:

```bash
cloudflared tunnel --url http://localhost:8080
```

Instalar no Nobara/Fedora:

```bash
sudo dnf install -y cloudflared
```

Ele imprime uma URL do tipo `https://algo-aleatorio.trycloudflare.com`. Guarde.

> O plano gratuito gera uma URL nova a cada execução, e o webhook precisa ser
> reregistrado toda vez. É incômodo e é o preço de não expor a máquina.
> Alternativa: `ngrok`, mesma limitação.

---

## 4. Criar a conexão de canal

Ainda **não existe tela** para isso — está na lista do que falta. Por enquanto,
direto no banco.

Conecte:

```bash
docker compose exec postgres psql -U crm -d crm
```

O RLS está ativo e **forçado**, inclusive para o dono do banco. Sem definir o
tenant, o `INSERT` é rejeitado pela política — o que é a prova mais barata de
que o isolamento está funcionando:

```sql
BEGIN;

-- Tenant de desenvolvimento criado pelo seed (empresa 'pnp').
SET LOCAL app.tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42';

INSERT INTO channel_connection (id, tenant_id, kind, name, external_account_id)
VALUES (
  gen_random_uuid(),   -- em produção o id vem da aplicação, em UUID v7
  '019fa91c-0f63-75f7-b4a0-1494c1304c42',
  'TELEGRAM',
  'Bot de atendimento',
  '1234567890'         -- a parte ANTES dos dois-pontos no token
)
RETURNING id;

COMMIT;
```

Anote o `id` devolvido — ele é o `{channelConnectionId}` da URL do webhook.

---

## 5. Gravar as credenciais

São **dois segredos distintos**, e a diferença importa:

| Credencial | Autentica quem, perante quem |
|---|---|
| `TELEGRAM_BOT_TOKEN` | o CRM perante o Telegram (para enviar) |
| `TELEGRAM_WEBHOOK_SECRET` | o Telegram perante o CRM (para receber) |

Gere o segredo do webhook:

```bash
openssl rand -hex 32
```

As credenciais são gravadas **cifradas**, então não dá para inseri-las por SQL
direto — a cifragem acontece na aplicação. Enquanto a tela de configuração não
existe, use o `CofreDeCredenciais` por um teste ou um `CommandLineRunner`
temporário no profile `dev`.

> **Atalho que NÃO funciona:** inserir o token em claro na coluna
> `ciphertext`. A decifragem falha na verificação de autenticidade do GCM, que
> é exatamente o comportamento desejado — bytes adulterados são detectados.

---

## 6. Registrar o webhook no Telegram

Com a URL do túnel e o `channelConnectionId` em mãos:

```bash
curl -X POST "https://api.telegram.org/bot<SEU_TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://SEU-TUNEL.trycloudflare.com/api/webhooks/telegram/<CONNECTION_ID>",
    "secret_token": "<SEGREDO_DO_PASSO_5>",
    "drop_pending_updates": true
  }'
```

`drop_pending_updates` descarta a fila acumulada. Sem ele, um webhook novo
herda todos os updates que o Telegram guardou enquanto ninguém escutava.

Conferir:

```bash
curl "https://api.telegram.org/bot<SEU_TOKEN>/getWebhookInfo"
```

`pending_update_count` alto ou `last_error_message` preenchido indicam que o
Telegram está tentando e falhando.

---

## 7. Testar

Mande uma mensagem para o bot no Telegram. O caminho esperado:

1. Telegram chama `POST /api/webhooks/telegram/{id}` com o header
   `X-Telegram-Bot-Api-Secret-Token`
2. O CRM compara o segredo **em tempo constante** e responde `200`
3. O evento cru é gravado em `inbound_event`
4. O worker de entrada traduz e publica a mensagem normalizada
5. A ingestão cria a conversa e a mensagem
6. O push STOMP acende a conversa na caixa de entrada

Conferir cada etapa:

```sql
SET LOCAL app.tenant_id = '019fa91c-0f63-75f7-b4a0-1494c1304c42';

SELECT external_event_id, processed_at, attempt_count, failure_reason
  FROM inbound_event ORDER BY received_at DESC LIMIT 5;

SELECT id, external_contact_id, contact_display_name, status, last_message_at
  FROM conversation ORDER BY last_message_at DESC LIMIT 5;

SELECT direction, content_type, text_content, status
  FROM message ORDER BY created_at DESC LIMIT 10;
```

Responder pelo inbox grava a mensagem como `PENDING`; o worker da fila de
saída a entrega e grava o `external_id` devolvido pelo Telegram.

---

## 8. Diagnóstico

**`403` no webhook**
Segredo divergente. O valor do `setWebhook` precisa ser idêntico ao gravado em
`TELEGRAM_WEBHOOK_SECRET`.

**`404` no webhook**
`channelConnectionId` inexistente, inativo ou excluído. É `404` de propósito:
para quem sonda, conexão inexistente e conexão de outro cliente precisam ser
indistinguíveis.

**`200` mas nada aparece**
O evento chegou e o processamento falhou:
```sql
SELECT external_event_id, attempt_count, failure_reason, payload
  FROM inbound_event WHERE processed_at IS NULL;
```
O `payload` cru está ali justamente para isto — é a única cópia, porque o
Telegram não guarda.

**Nada é reprocessado**
Teto de tentativas atingido (`max-tentativas`, padrão 5). Reprocessar é zerar:
```sql
UPDATE inbound_event SET attempt_count = 0, next_attempt_at = NULL
 WHERE id = '<id>';
```

**Mensagem enviada fica em `PENDING`**
Worker da fila de saída desligado ou sem token. Ver
`app.fila-de-saida.habilitada` e a credencial `TELEGRAM_BOT_TOKEN`.

---

## O que ainda não existe

Sendo explícito para não parecer pronto:

- **Tela de configuração de canal.** Conexão e credenciais são criadas na mão.
- **Mídia.** Foto, áudio e documento são classificados e registrados, mas o
  arquivo **não é baixado**. Os links do Telegram expiram, então guardar só a
  referência perde o anexo — o download, a validação por magic bytes e o
  storage próprio são trabalho pendente.
- **Grupos** funcionam, mas cada grupo vira uma conversa única. Distinguir
  quem falou dentro dele depende do módulo `contact`.
- **Nada disso foi executado contra um Postgres real.** Ver
  `contexto/02-estado-atual.md`.
