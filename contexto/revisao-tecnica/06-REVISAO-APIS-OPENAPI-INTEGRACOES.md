# Prompt — APIs, OpenAPI, integrações e tempo real

## Início do prompt

Revise os contratos externos do CRM PNP em modo somente leitura: APIs HTTP,
OpenAPI, adaptadores frontend, webhooks, provedores e WebSocket. Leia
`00-CONTEXTO-CANONICO.md`, `frontend/CONTRATO-HTTP.md`, snapshots OpenAPI,
controllers, DTOs, tratamento de erro, adapters e testes. Use o modelo oficial
de achado.

### Contrato HTTP

- Inventarie endpoints implementados por domínio, método, autenticação,
  autorização, idempotência, request, response e erros.
- Compare controller, DTO, validação, OpenAPI gerado, tipos TypeScript, adapter e
  uso na tela. Registre qualquer divergência vertical.
- Confirme versão do contrato, tipos de conteúdo, limites de payload, timeout,
  correlação e headers de segurança pertinentes.
- Erros seguem RFC 9457, têm código estável e campos úteis sem stack trace ou
  detalhes internos.
- Status distinguem validação, autenticação, autorização, inexistência, conflito,
  rate limit, indisponibilidade e falha de provedor de forma não enumerável.
- Examine compatibilidade de nomes e base de paginação, especialmente
  `pagina`/`tamanho` versus modelos genéricos usados pelo frontend; não conclua
  divergência sem seguir uma requisição real ponta a ponta.

### Validação e escrita segura

- Allowlist de ordenação/filtros e limites impostos no servidor.
- DTOs não permitem `tenant_id`, campos internos, timestamps ou papel/escopo por
  mass assignment.
- Operação repetível tem chave de idempotência com escopo, duração e resposta
  definidos; o cliente só repete escrita quando o contrato autoriza.
- Concorrência e retries não duplicam contato, funil, mensagem ou efeito externo.
- Coleções pequenas usam paginação limitada; históricos longos usam cursor
  estável quando necessário.

### OpenAPI e cliente frontend

- Geração 3.1 é determinística e o snapshot corresponde ao backend atual.
- `openapi-typescript` está fixado e gerar novamente não produz diff inesperado.
- Somente a camada de adapter importa o contrato gerado; páginas não dependem
  dele diretamente.
- Modelos e mapeamentos preservam nulos, datas UTC, centavos, enums e páginas.
- Cliente central implementa timeout, `AbortSignal`, correlation id, credenciais,
  CSRF e retry seguro sem mascarar erro.

### Webhooks e canais

Para Telegram e cada integração existente:

- autentique segredo/assinatura antes de confiar no payload;
- receba corpo com limite e preserve bytes crus quando a assinatura exigir;
- resolva tenant por conexão/canal persistido;
- grave evento e chave de deduplicação de forma durável antes do ACK;
- responda falha transitória quando persistência falhar;
- processe efeito posterior de modo assíncrono, idempotente e observável;
- controle retry, backoff, dead letter/falha permanente e ordem por conversa;
- mantenha credenciais write-only e erros sanitizados;
- teste fixtures oficiais versionadas e eventos fora de ordem/repetidos.

Avalie janela de atendimento, templates e ledger de uso apenas para provedores
em que isso se aplica. Não trate integração ainda planejada como entregue. APIs
oficiais são o padrão; ponte não oficial exige decisão jurídica e de negócio.

### Outbound e falhas parciais

- A requisição local persiste mensagem pendente antes de chamar provedor.
- Chamada externa não mantém transação de banco aberta sem justificativa.
- Estados pendente/enviada/entregue/lida/falha têm transições coerentes e
  monotônicas quando exigido.
- Retry não envia duplicado; erro externo não vaza credencial/payload e pode ser
  reconciliado.
- Upload/mídia, se implementado, valida tamanho, magic bytes, nome, retenção e
  armazenamento isolado.

### WebSocket STOMP

- Endpoint, allowlist de origem, autenticação no CONNECT e autorização no
  SUBSCRIBE/destino.
- Token não aparece em URL ou log.
- Tópicos não permitem adivinhar/assinar outro tenant ou unidade.
- Reconexão recupera lacunas por REST, deduplica eventos e trata backpressure.
- Broker em memória é registrado como limite de escala atual, não como falha de
  isolamento sem evidência.

### Saída

Entregue:

1. inventário de contratos e integrações;
2. rastreamento vertical de login, lista de contatos, envio outbound e inbound;
3. tabela de divergências `backend | OpenAPI | adapter | tela`;
4. matriz de falhas/retries/idempotência;
5. achados P0–P3 e testes de contrato ausentes;
6. veredito das partes pertinentes dos Gates C e D.

Não faça chamadas reais a provedores nem exponha payloads. Use mocks, fixtures
sanitizadas ou inspeção estática.

## Fim do prompt

