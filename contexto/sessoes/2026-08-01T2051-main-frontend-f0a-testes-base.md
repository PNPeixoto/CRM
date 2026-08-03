# Sessão — frontend:F0A testes base

- Data: 2026-08-01
- Branch observada: `main`
- Baseline: `793777d + working-tree`
- Ambiente: Windows, Node.js 24.18.0, npm 11.16.0
- Responsável: Codex

## Entregue

- Vitest, Testing Library e jsdom preservados;
- setup global determinístico para DOM, mocks, globals, timers, storage,
  cookies visíveis, tema e histórico;
- rede real bloqueada por padrão, com diagnóstico restrito a método/caminho;
- helper `src/test/http.ts` para fixtures sintéticas na fronteira `fetch`;
- axe-core 4.12.1 como única dependência nova, fixada no lockfile;
- helper de acessibilidade com diagnóstico sanitizado e contraste excluído do
  jsdom por depender de layout real;
- smoke do login consultado por heading, labels e botão acessíveis;
- prova de que o auditor detecta botão sem nome acessível;
- regressões da camada HTTP para fixture válida, JSON inválido, erro seguro e
  dez respostas 401 concorrentes com um único refresh;
- scripts separados para gate rápido, testes unitários e componentes;
- README frontend com política de E2E, fixtures, quarentena e testes por risco.

## Decisões proporcionais

Não foi adicionada biblioteca de simulação HTTP: o `fetch` nativo e Vitest
cobrem a fronteira atual com menos dependências. Axe-core foi necessário porque
não havia auditor semântico e o aceite exige que uma violação plantada seja
detectada. E2E continua sem runner porque nenhuma jornada concreta foi definida
para este prompt; arquivos E2E já ficam excluídos do gate rápido.

Não foi aplicado `npm audit fix --force`. A instalação reproduziu os dois
achados altos já registrados no estado e a correção sugerida envolve mudança
potencialmente incompatível; análise e decisão pertencem a F4A.

## Evidência

- `npm test`, execução 1: 6 arquivos, 14 testes, todos aprovados;
- `npm test`, execução 2 consecutiva: mesmo conjunto e mesmo resultado;
- `npm run test:unit`: 3 arquivos, 9 testes aprovados;
- `npm run test:component`: 3 arquivos, 5 testes aprovados;
- `npm test` com `CI=true`: os mesmos 6 arquivos e 14 testes aprovados;
- `npm run lint`: 0 erros e 3 avisos de Fast Refresh preexistentes;
- `npm run build`: passou, 1.864 módulos transformados;
- nenhum teste ignorado, em `.only` ou em quarentena.

## Próximo passo

`frontend:F1` pode consolidar tokens e primitivas usando esta base. Cada prompt
seguinte deve entregar seu teste no mesmo PR; F12 continua sendo auditoria de
lacunas, não o começo tardio da cobertura.
