# Sessão — frontend:F3 contrato e camada de acesso

Data: 2026-08-01 23:48 (America/Montevideo)
Branch: `main`
Baseline: `793777d + working-tree`

## Resultado

F3 concluído. O backend publica OpenAPI 3.1 determinístico e o frontend consome
tipos gerados sem acoplar páginas ao transporte.

## Mudanças

- Springdoc 3.0.3 e snapshot canônico `backend/openapi/openapi.json`.
- RFC 9457 com código, campos, instância e correlação; erros estruturais 400,
  ausência 404, conflito 409, semântica 422 e falha interna segura.
- `openapi-typescript` 7.13.0 fixo, saída versionada e `api:check` sem diff.
- Fronteira `gerado -> adaptador -> apresentação` protegida por teste.
- Cliente central com URL/cookies, token em memória, timeout, cancelamento,
  correlação, paginação e retry limitado; escrita exige idempotência explícita.
- Tratamento seguro e distinto de 400/401/403/404/409/422/429/5xx, rede,
  timeout, cancelamento e quebra de contrato.
- CI executa backend, contrato sem diff, lint, testes e build.

## Evidências

- `npm run api:check`: sem diferença.
- Frontend: 56 testes em 14 arquivos, todos verdes.
- Build de produção passou; lint sem erros e com três avisos conhecidos de
  Fast Refresh.
- Backend integrado: 82 testes, todos verdes; o teste OpenAPI compara o
  endpoint real ao snapshot.
- `git diff --check` passou.

## Próximo

`frontend:F4A` está desbloqueado; depois dele, executar `frontend:F4`.
