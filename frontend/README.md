# Frontend do FinUp CRM

Cliente React/TypeScript construído com Vite e Tailwind. Em desenvolvimento,
`/api` é encaminhado para o backend local pela configuração do Vite.

## Executar

```bash
npm install
npm run dev
```

Use os mesmos comandos no Windows, Linux e na futura CI. A instalação
reprodutível da CI deve usar `npm ci`; os runbooks gerais ainda serão alinhados
no prompt de CI/CD.

### Demonstração local

Quando o backend completo não estiver disponível, execute a API sintética em
um terminal:

```bash
npm run demo:api
```

Em outro terminal, inicie o Vite normalmente. O proxy local conecta o frontend
à API de demonstração na porta 8080. O cenário inclui Visão geral, Contatos,
Oportunidades, Conversas, Agenda, Tarefas e Acessos; criações e movimentações
ficam em memória e são reiniciadas junto com o processo. Essa API existe apenas
para apresentação e desenvolvimento visual, nunca para produção.

## Testes

```bash
npm test
npm run test:unit
npm run test:component
npm run lint
npm run build
```

- `npm test` é o gate rápido: todos os testes unitários e de componente, nunca
  arquivos `*.e2e.test.*`;
- `test:unit` executa arquivos TypeScript sem JSX;
- `test:component` executa arquivos TSX em jsdom;
- E2E ainda não possui runner nem jornada crítica concreta. Quando existir,
  terá comando próprio e continuará fora do gate rápido.

O setup global restaura DOM, mocks, globals, timers, storage, cookies visíveis,
tema e histórico depois de cada teste. Rede real é bloqueada; use
`src/test/http.ts` para fixtures sintéticas na fronteira `fetch`. Chamada não
simulada falha mostrando apenas método e caminho.

`src/test/accessibility.ts` aplica axe-core aos componentes. O resultado
automatizado não substitui teclado, leitor de tela, contraste, zoom e reflow em
navegador real.

## Design system

`src/index.css` é a fonte única dos tokens. As variáveis `--palette-*` são
primitivas privadas e não podem aparecer em componentes; páginas e componentes
consomem apenas tokens semânticos como `--surface-raised`, `--text-muted`,
`--border-control` e `--danger`. O mapa claro é o padrão e o escuro continua
disponível somente por `data-theme="dark"`, sem seletor antecipado.

As primitivas atuais possuem consumidor real: botão, input, select, label,
alerta, skeleton e estados de conteúdo. Avatar, tooltip, toast, menu composto e
modal só serão criados quando uma jornada exigir. `EstadoDeConteudo` distingue
carregamento, vazio inicial, filtro sem resultado, erro, falta de permissão e
offline; F2 acrescenta o 404 real. JetBrains Mono fica reservada a
identificadores e dados técnicos.

O contrato automatizado impede literal de cor fora da paleta, uso direto de
escala de cor nos componentes, contraste insuficiente nos pares semânticos,
tema escuro incompleto e ausência de foco ou redução de movimento.

## Casca e navegação

`src/app/routes.ts` é o único registro de páginas: contém caminho, apresentação
e carregador lazy consumidos pelo roteador e pelo menu. Capability, entitlement,
permissão e apresentação do segmento são resolvidos separadamente; esconder um
item é conveniência de interface, nunca autorização.

A casca oferece skip link, disclosure mobile com foco inicial e Escape, menu
desktop recolhível e estados 403/404 distintos. A preferência do menu grava
somente `1` em chave v1 escopada por hash do usuário; sessão, tenant, login e
autorização não são persistidos. Os contextos vêm de
`/api/organizacao/contextos`; unidades não aparecem até o backend conseguir
aplicar o recorte aos agregados. Um futuro seletor só pode aparecer com mais de
uma opção realmente navegável e deve desmontar o conteúdo anterior na troca.

## Estado de servidor

Consultas e mutações passam pela fronteira `shared/server-state`, descrita em
`ESTADO-SERVIDOR.md`. Toda chave inclui tenant, unidade, identidade, recurso e
parâmetros; sessão nova e logout cancelam e limpam o cache em memória antes de
alterar o usuário visível. Não existe cache persistente/offline. Páginas não
importam TanStack Query diretamente, o que mantém a decisão reversível.

## Formulários

Validação local, erros RFC 9457 por campo, foco, submissão repetível e as
fronteiras de data, horário, dinheiro e segredo estão em `FORMULARIOS.md`.

## Política

- teste consulta por papel, nome ou rótulo acessível, não por classe CSS;
- fixtures seguem o contrato e nunca copiam payload real;
- relógio usa timers falsos e data explícita quando afeta o resultado;
- cada prompt F1–F13 entrega seus testes no mesmo PR;
- não existe meta global de cobertura: risco determina o teste necessário;
- `.only` é proibido pelo runner;
- quarentena exige issue, responsável, justificativa, expiração, execução
  contínua e exclusão explícita do gate. Sessão, isolamento, segurança,
  migration e cobrança não podem ser quarentenados;
- expiração automática da quarentena será configurada quando a CI existir; não
  há teste em quarentena hoje.
