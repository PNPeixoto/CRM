# Sessão — auditoria de entrada frontend:F3

- Data: 2026-08-01
- Branch observada: `main`
- Baseline: `793777d + working-tree`
- Ambiente: Windows, Node.js 24.18.0, npm 11.16.0
- Responsável: Codex

## Resultado

`frontend:F3` não foi iniciado porque seu pré-requisito canônico `backend:05`
permanece `ready`, não `completed`. Por sua vez, o Prompt 05 depende dos
Prompts 03 e 04, também não executados. Avançar criaria um contrato de transporte
sem a identidade, os escopos e a autorização que deveriam defini-lo.

## Evidência da lacuna

- nenhuma ocorrência executável de Springdoc, OpenAPI ou Swagger existe no
  `pom.xml`, código principal ou testes do backend;
- não existe especificação OpenAPI versionada nem comando de geração do cliente;
- `GlobalExceptionHandler` devolve `ErroResponse` próprio, não RFC 9457;
- o frontend ainda declara DTOs de transporte manuais e usa genéricos diretos
  sobre `api.get/post/put/delete`;
- capabilities, entitlements, permissões e unidades autorizadas ainda não fazem
  parte de `/auth/me` ou de outro contrato publicado.

## Decisão segura

Não foi escolhido gerador, versão, retry, paginação ou envelope de erro sem a
fonte do backend. F3 continua `ready` no manifesto e deve ser retomado somente
após os Prompts 03–05, quando o OpenAPI determinístico e RFC 9457 existirem.
