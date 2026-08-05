# Sessão — conta máxima e Prompt 08

- **Data:** 2026-08-03 23:42 (America/Montevideo)
- **Escopo:** visão consolidada da conta máxima e execução do backend Prompt 08
- **Resultado:** concluído; ambiente local atualizado e saudável

## Conta máxima da empresa

- `OWNER` mantém alcance `TENANT` em contatos, tarefas, oportunidades,
  conversas, canais e relatórios.
- Criações sem responsável explícito passam a pertencer ao usuário autor; não
  nascem mais registros órfãos por omissão.
- Respostas de contato, tarefa e oportunidade incluem id, login e nome do
  responsável. A resolução é em lote para não introduzir N+1.
- Contatos, tarefas e cards do funil exibem `Nome (@login)`; ausência factual é
  mostrada como `Sem responsável`.
- V12 atribuiu ao `created_by` registros históricos sem responsável. No banco
  local PNP ficaram 1 contato, 9 oportunidades e 1 tarefa atribuídos a
  `peixoto`, com zero órfãos.

## Prompt 08 — correções transversais

- `HttpProtectionFilter`: correlation id validado antes da cadeia, MDC e
  cabeçalho em toda resposta; corpo máximo de 1 MiB inclusive chunked; janela
  por origem nas rotas públicas de autenticação e webhook.
- Consulta de contatos rejeita página, tamanho, filtro e ordenação inválidos em
  vez de corrigir silenciosamente.
- Histórico de mensagens usa keyset `(createdAt,id)` e lote máximo de 100.
- Envio aceita `Idempotency-Key`; V13 persiste a chave com índice único. Replay
  do mesmo conteúdo devolve a mesma mensagem, e chave reutilizada com outro
  conteúdo retorna 409.
- Readiness `schemaVersion` compara versão esperada pela imagem e versão
  estrutural do Flyway. A comparação falha para banco atrasado e imagem antiga.
- API docs e Swagger UI são desabilitados em produção; a allowlist estrita do
  Jackson é verificada também na composição efetiva desse profile.
- A suíte completa ganhou preflight único do Docker; o gate rápido continua
  independente de containers.
- O CI constrói a imagem final e a varre com Trivy para severidade alta/crítica.
  A action v0.36.0 está fixada no SHA completo seguro publicado após o incidente
  de cadeia de suprimentos de março de 2026.

## Evidência

- Backend: **123/123** testes verdes; `ApplicationModules.verify()`, migrations,
  isolamento, idempotência, payload, rate limit, produção e readiness incluídos.
- Frontend: **101/101** testes em 22 arquivos; lint sem erro, build e
  `api:check` verdes.
- OpenAPI regenerado e validado; TypeScript gerado sem divergência.
- Ambiente local reconstruído preservando o volume. Flyway aplicou V12/V13;
  `/actuator/health/readiness` respondeu `UP`.
- Permissões de `peixoto` conferidas no banco: as nove permissões relevantes
  estão em alcance `TENANT`.

## Backlog

- Resolvidos: `SEC-001`, `SEC-003`, `SEC-006`, `SEC-012`, `SEC-015`, `SEC-017`.
- Permanecem médios: `SEC-011` (inscrição WebSocket já ativa) e `SEC-016`
  (observar externamente a primeira execução do CI no repositório privado).
- Próximo prompt backend: **09 — design system e casca**.
