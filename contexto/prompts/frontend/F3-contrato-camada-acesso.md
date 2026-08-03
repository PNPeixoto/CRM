---
id: "F3"
canonical_id: "frontend:F3"
title: "Contrato OpenAPI e camada de acesso"
phase: "frontend_foundation"
risk: "high"
prerequisites: ["backend:05", "frontend:F2"]
blocking: "before-external-pilot"
produces: ["cliente OpenAPI isolado", "adaptadores de domínio", "contrato de erro RFC 9457"]
gate: "C"
---

# F3 — Contrato e camada de acesso

## Objetivo

Faça o frontend consumir um contrato verificável sem acoplar a apresentação ao
código gerado nem duplicar DTOs de transporte manualmente.

## Trabalho

1. Use o OpenAPI publicado pelo backend como fonte. Se faltarem exposição
   determinística, RFC 9457 ou correlação, ajuste apenas essa superfície; não
   altere regra de domínio neste prompt.
2. Fixe gerador, versão e configuração. Versione a saída em pasta própria, nunca
   edite arquivo gerado e faça a CI regenerar e reprovar diferença inesperada.
3. Preserve a fronteira `gerado -> adaptador -> apresentação`. Componentes e
   stores não importam tipos ou funções da pasta gerada.
4. Centralize base URL/prefixo, cookies, `AbortSignal`, timeout, correlação e
   idempotência. Leitura pode repetir com limite, backoff e jitter; escrita só
   repete com chave idempotente e contrato explícito.
5. Normalize RFC 9457 com `type`, `title`, `status`, `detail` seguro, `instance`,
   código estável, erros por campo e identificador de correlação.
6. Trate distintamente 400/422 conforme contrato, 401, 403, 404, 409, 429 e 5xx.
   Nunca mostre mensagem crua, stack ou detalhe sensível do servidor.
7. Padronize paginação, ordenação, filtros e limites, inclusive quando o backend
   rejeitar limite excessivo.
8. Teste geração sem diff, adaptação, cancelamento, timeout, retry permitido,
   proibição de retry de escrita e todos os estados de erro relevantes.

## Aceite

- regenerar o cliente no mesmo commit não produz diff;
- imports gerados ficam restritos à camada de adaptação;
- nenhuma tela interpreta diretamente envelopes HTTP;
- escrita não é repetida sem idempotência comprovada;
- erros exibidos são seguros, acionáveis e preservam correlação.
