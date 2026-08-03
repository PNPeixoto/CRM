# Contrato HTTP do frontend

## Fonte e geração

O backend publica OpenAPI 3.1 em `/v3/api-docs`. O snapshot canônico fica em
`backend/openapi/openapi.json`; o teste `OpenApiContractTest` compara o endpoint
real com esse arquivo. A versão fixa `openapi-typescript@7.13.0` e todas as
opções de geração estão nos scripts do `package.json`.

```text
npm run api:generate  # atualiza src/generated/api-schema.d.ts
npm run api:check     # falha se a saída versionada estiver desatualizada
```

O arquivo gerado nunca é editado. Mudança intencional começa no backend,
regenera o snapshot e depois regenera o TypeScript no mesmo conjunto de
alterações. A CI executa o contrato real, `api:check`, testes e build.

## Fronteira arquitetural

O fluxo permitido é:

```text
OpenAPI -> src/generated -> src/adapters/http -> shared/domínio -> páginas
```

Somente `src/adapters/http/contracts.ts` importa o arquivo gerado. Adaptadores
validam campos obrigatórios, traduzem enums e normalizam ausências antes de
entregar modelos próprios às páginas. Um teste de arquitetura reprova qualquer
importação direta fora dessa fronteira.

## Cliente central

`src/lib/api.ts` concentra prefixo `/api`, cookies com `credentials: include`,
access token em memória, CSRF, correlation id, timeout e `AbortSignal`. Leituras
podem fazer até três tentativas com backoff exponencial, jitter e espera máxima
limitada. Escritas não repetem por padrão; só repetem quando o chamador declara
`retry: 'idempotent-write'` e fornece uma `idempotencyKey` estável.

Paginação começa em zero, usa `size` entre 1 e 100, aceita vários parâmetros
`sort` e serializa filtros definidos. Valores fora do limite falham antes da
rede.

## RFC 9457 e mensagens seguras

O backend responde `application/problem+json` com `type`, `title`, `status`,
`detail`, `instance`, `codigo`, `campos` e `correlacaoId`. O cliente preserva
código, campos, instância e correlação para tratamento, mas nunca apresenta
`detail` ou corpo cru. A mensagem visível é escolhida localmente por status:

- 400: solicitação malformada;
- 401: sessão ausente ou expirada;
- 403: permissão insuficiente;
- 404: recurso não encontrado;
- 409: conflito de estado;
- 422: dados semanticamente inválidos;
- 429: limite temporário;
- 5xx: falha interna genérica.

Falhas de rede, timeout, cancelamento e quebra de contrato são categorias
distintas. O identificador de correlação pode ser apresentado ao suporte, sem
expor stack, segredo ou detalhe interno.
