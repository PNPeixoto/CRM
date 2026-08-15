# Inventário verificável do frontend

- Prompt: `frontend:F0`
- Data: 2026-08-01
- Baseline: commit `793777d` + working tree existente, branch `main`
- Ambiente: Windows, Node.js 24.18.0, npm 11.16.0
- Responsável pela coleta: Codex

## Veredito

O frontend é uma **alpha funcional**: autenticação, onboarding, dashboard,
contatos, inbox, funil, tarefas, canais e relatórios consomem endpoints reais.
Não é apenas um protótipo, mas também não há página de negócio pronta para
produção segundo os gates atuais. Faltam contrato OpenAPI gerado, autorização
fina, paginação consistente, cobertura dos riscos de sessão/API, suporte mobile
da casca e uma política verificável de navegadores.

O registro central declara sete módulos como `pronto`; hoje esse valor significa
“não é placeholder”, e não “aprovado para produção”. Há nove módulos marcados
como `em_producao` que renderizam o placeholder comum.

## Stack e execução

| Item | Estado executável |
|---|---|
| Aplicação | React 19.2.8 + React DOM 19.2.8 + TypeScript 6.0.3 |
| Build | Vite 8.1.5, plugin React 6.0.4 |
| Estilos | Tailwind CSS 4.3.3 via plugin do Vite |
| Rotas | React Router DOM 7.18.1, `BrowserRouter` e lazy loading por página |
| Tempo real | STOMP.js 7.3.0 sobre WebSocket nativo |
| Componentes | Radix Label/Slot, CVA, clsx, tailwind-merge, Lucide |
| Testes | Vitest 4.1.10, Testing Library, jest-dom, jsdom 28.1.0 |
| Qualidade | Oxlint 1.75.0; TypeScript com `noUnused*` e resolução `bundler` |
| Pacotes | npm, lockfile v3; `packageManager`, runtime e npm não estão fixados |
| Produção | `vite build`; não há Dockerfile, pipeline ou publicação do frontend |

Scripts disponíveis: `dev`, `build`, `lint`, `test` e `preview`. O Vite sobe na
porta 5174 e encaminha `/api` para o backend local na 8080. Os runbooks usam
`npm install`, não `npm ci`; não foi encontrado workflow de CI do frontend.

### Dependências diretas instaladas

| Grupo | Pacotes e versões |
|---|---|
| Runtime | `@fontsource/jetbrains-mono@5.3.0`, `@fontsource/manrope@5.3.0`, `@radix-ui/react-label@2.1.15`, `@radix-ui/react-slot@1.3.3`, `@stomp/stompjs@7.3.0`, `class-variance-authority@0.7.1`, `clsx@2.1.1`, `lucide-react@1.27.0`, `react@19.2.8`, `react-dom@19.2.8`, `react-router-dom@7.18.1`, `tailwind-merge@3.6.0` |
| Desenvolvimento | `@tailwindcss/vite@4.3.3`, `@testing-library/jest-dom@6.9.1`, `@testing-library/react@16.3.2`, `@types/node@24.13.3`, `@types/react@19.2.17`, `@types/react-dom@19.2.3`, `@vitejs/plugin-react@6.0.4`, `jsdom@28.1.0`, `oxlint@1.75.0`, `tailwindcss@4.3.3`, `typescript@6.0.3`, `vite@8.1.5`, `vitest@4.1.10` |

Não há scripts `preinstall`/`postinstall` no pacote nem dependência marcada com
script de instalação no lockfile atual. A verificação de vulnerabilidades fica
para F4A; o estado consolidado já registra achados anteriores do `npm audit`.

## Organização do código

- `src/app`: registro central e composição do roteador;
- `src/components/ui`: quatro primitivas (`Alert`, `Button`, `Input`, `Label`);
- `src/shared/auth`: sessão em memória e rota protegida;
- `src/shared/tenant`: apresentação por segmento, onboarding e navegação;
- `src/shared/crm` e `src/shared/conversas`: APIs e tipos manuais;
- `src/shared/layouts` e `src/shared/components`: shell, página, cartões e estados;
- `src/pages`: módulos de produto, carregados por rota;
- `src/test`: setup global do Vitest/Testing Library.

Não existe store global genérica, biblioteca de server state, formulário,
i18n, telemetria, PWA/service worker ou error boundary. O estado remoto é
mantido por `useState`/`useEffect` dentro das páginas.

## Rotas e páginas

Critério deste relatório: **pronta** conclui integralmente seu objetivo atual;
**parcial** usa implementação/API real, mas tem lacuna funcional ou de gate;
**placeholder** contém apenas a mensagem de módulo em desenvolvimento.

| Rota | Registro | Avaliação F0 | Evidência e lacuna principal |
|---|---|---|---|
| `/login` | fora do registro | parcial | Login, restauração e erro uniforme funcionam; não há teste de sessão, recuperação ou MFA. |
| `/primeiro-acesso` | fora do registro | parcial | Persiste segmento e atualiza navegação; guard/provider têm testes, mas não é onboarding completo. |
| `/dashboard` | `pronto` | parcial | KPIs reais; sem teste da página e sem autorização por função/escopo. |
| `/inbox` | `pronto` | parcial | REST + STOMP e envio real; sem cursor, sequence, polling, erro completo ou layout mobile. |
| `/contatos` | `pronto` | parcial | Busca paginada, criação, exclusão e ficha com oportunidades/atividades; edição e paginação das relações ainda ausentes. |
| `/funis` | `pronto` | parcial | Colunas independentes, resumo, totais, criação por etapa e movimento otimista por mouse, toque ou teclado; edição/exclusão de etapas e paginação ainda ausentes. |
| `/tarefas` | `pronto` | parcial | Lista, filtro, criação, conclusão e exclusão; edição, paginação e erros de mutação ausentes. |
| `/integracoes` | `pronto` | parcial | Lista/cria/ativa chat e Telegram sem reexibir segredos; edição/rotação/exclusão e erros por campo ausentes. |
| `/relatorios` | `pronto` | parcial | Resumo pontual real; sem período, filtros, exportação ou testes da página. |
| `/oportunidades` | legado | pronta | Seu único objetivo é redirecionar links antigos para `/funis`. |
| `/agenda` | `pronto` | parcial | Calendário mensal real sobre tarefas, com conclusão e criação; edição e paginação ainda ausentes. |
| `/reservas` | `em_producao` | placeholder | Apenas `EmProducao`. |
| `/produtos` | `em_producao` | placeholder | Apenas `EmProducao`. |
| `/unidades` | `em_producao` | placeholder | Apenas `EmProducao`. |
| `/equipes` | `em_producao` | placeholder | Apenas `EmProducao`. |
| `/automacoes` | `em_producao` | placeholder | Apenas `EmProducao`. |
| `/campanhas` | `em_producao` | placeholder | Apenas `EmProducao`. |
| `/auditoria` | `pronto` | real | Eventos auditáveis com filtros, paginação keyset no servidor, IDs mascarados e integridade verificada. |
| `/configuracoes` | `em_producao` | placeholder | Apenas `EmProducao`. |

As rotas `/` e desconhecidas redirecionam para o primeiro caminho visível; não
há página 404. Módulo conhecido sem permissão também não tem estado 403, porque
capabilities, entitlements e permissões ainda não fazem parte do contrato.

## Design, tokens e responsividade

O código confirma a decisão visual registrada: canvas claro com shell escuro.
Manrope 400/500/600/700 e JetBrains Mono 400 são auto-hospedadas via Fontsource.

| Família | Tokens existentes |
|---|---|
| Superfícies | `surface-base`, `surface-raised`, `surface-sunken`, `surface-shell`, `surface-shell-hover` |
| Texto | `text-strong`, `text-muted`, `text-on-brand`, `text-on-shell`, `text-on-shell-muted` |
| Bordas | `border-subtle`, `border-strong` |
| Marca | `brand`, `brand-hover`, `brand-soft` |
| Semântica | `success`, `danger`, `warning`, `info` e respectivos fundos `*-soft` |
| Interação/geometria | `focus-ring`, `radius-control`, `radius-surface` |

Há mapas claro e escuro completos. O escuro só ativa por
`:root[data-theme="dark"]`; nenhum código escreve esse atributo, coerente com a
decisão de deixar o seletor para depois do P0. Hexadecimais aparecem apenas no
arquivo de tokens, não nos componentes.

Não existem tokens próprios de espaçamento, breakpoint ou sombra. A interface
usa a escala padrão do Tailwind e sombras discretas em superfícies móveis. Os breakpoints 1280/1440 do
briefing não estão modelados. A casca mantém sidebar fixa de 240 px em qualquer
largura, sem navegação mobile ou skip link. IDs de ícone existem nas rotas, mas
o shell não os renderiza. O placeholder usa `text-muted`, utilitário não ligado
explicitamente ao token `--text-muted`.

O protótipo `SYS-PNP-CRM-teste.html` não está no repositório. A comparação
reproduzível limita-se à sessão de 2026-07-27 e ao briefing canônico; ambos
confirmam claro + shell escuro, `#4B2ED4`, Manrope e JetBrains Mono.

## Mapa página → API → contrato → backend

| Página/capacidade | Transporte usado | Contrato frontend | Estado backend |
|---|---|---|---|
| Sessão | `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me`, `POST /auth/logout` | Interfaces locais em `AuthContext` | Implementado; access token JWT e refresh rotativo. |
| Onboarding | `GET /empresa/apresentacao`, `PUT /empresa/perfil-inicial` | `shared/tenant/tipos.ts` | Implementado e testado no backend. |
| Dashboard/relatórios | `GET /relatorios/visao-geral` | `VisaoGeral` manual | Implementado; regra de agregação fica no backend. |
| Contatos | `GET/POST/PUT/DELETE /contatos`, GET por ID e relações por contato; UI não usa PUT | `Contato`, `Oportunidade` e `Tarefa` mapeados do OpenAPI | Implementado; lista principal é paginada, relações ainda devolvem array. |
| Funis/oportunidades | GET funis/lista, POST criar/mover; UI não usa PUT/DELETE | `Funil`/`Oportunidade` manuais | Implementado com movimento otimista e rollback; lista não paginada. |
| Tarefas | GET/POST, POST concluir, DELETE; UI não usa PUT | `Tarefa` manual | Implementado; lista não é paginada. |
| Inbox | GET conversas/mensagens, POST mensagem e STOMP `/ws` | Tipos REST/push manuais | Implementado sem cursor/sequence no contrato atual. |
| Canais | GET/POST, POST ativação; UI não usa PUT | `Canal` manual | Implementado; segredo é entrada, nunca resposta. |
| Placeholders | nenhum | nenhum | Módulos variam entre fundação backend e ausência de API pública. |

`src/lib/api.ts` concentra os únicos dois `fetch` do código; páginas não usam
transporte direto. Access token fica em memória, todas as chamadas usam cookies
com `credentials: include`, mutações reenviam o token anti-CSRF e refreshes
concorrentes compartilham uma promessa.

Lacunas contratuais:

- não há dependência/configuração OpenAPI, especificação publicada ou client
  TypeScript gerado;
- DTOs de autenticação, tenant, CRM e conversas são escritos à mão;
- erro do backend usa `codigo/mensagem/correlacaoId/campos/momento`, não RFC 9457;
- o cliente lê apenas `mensagem` e descarta código, correlação e erros de campo;
- não há timeout, cancelamento, idempotency key, retry de leitura ou correlação
  no cliente HTTP;
- só contatos impõe página no backend, mas o frontend omite parâmetros e vê no
  máximo os 50 primeiros sem saber o total;
- não foram encontrados controles por papel/ação/escopo/registro nos controllers;
  a proteção atual é autenticação + isolamento de tenant/RLS;
- a navegação recebida do backend é apresentação por segmento, não autorização.

## Navegador, armazenamento e segurança

| Superfície | Estado encontrado |
|---|---|
| Access token | Somente em variável de módulo; morre com a aba. |
| Refresh | Cookie HttpOnly, Secure fora de dev, SameSite Strict e caminho restrito. |
| Anti-CSRF | Cookie legível + cabeçalho em mutações comuns; login e refresh estão isentos no backend. |
| Storage | Nenhum uso real de localStorage, sessionStorage ou IndexedDB. |
| URL/histórico | Nenhum token ou dado de formulário é gravado. |
| HTML dinâmico | Não há `dangerouslySetInnerHTML`, `eval` ou `new Function`. Mensagens renderizam como texto. |
| Conteúdo externo | Fontes e SVGs são locais; nenhuma URL externa aparece no código de produção. |
| CSP | Scripts `self`; estilos `self` + `unsafe-inline`; imagens `self/data/blob`; conexão `self` + WebSocket; objetos e frames bloqueados. |
| CORS | Allowlist; arquitetura documentada como mesma origem. Em topologia cross-origin, `Authorization` não está na lista de headers. |
| Source maps | Vite não os habilita e o build atual não contém `.map`. Não há fluxo privado de upload. |
| Telemetria | Ausente; sem error boundary, analytics, screenshot ou session replay. |
| Offline/PWA | Ausente; sem service worker, manifest ou cache persistente. |

O backend isenta refresh de CSRF e depende de SameSite Strict + topologia de
mesma origem. Isso diverge do contrato v4, que exige avaliar proteção explícita
para refresh/logout conforme a topologia. A CSP ainda precisa de
`style-src 'unsafe-inline'` porque os componentes usam `style={...}`.

## Matriz de navegadores

Não existe `browserslist`, matriz contratual, telemetria de browser nem política
de polyfill. O TypeScript compila para ES2023 e o código usa `fetch`,
`WebSocket`, `Intl`, CSS variables/grid/flex e `Array.prototype.toSorted`.

| Plataforma | Mínimo declarado | Teste nesta sessão | Situação |
|---|---|---|---|
| Chromium desktop (Chrome/Edge) | nenhum | não executado | decisão pendente |
| Firefox desktop | nenhum | não executado | decisão pendente |
| Safari macOS | nenhum | não executado | decisão pendente |
| Safari iOS | nenhum | não executado | decisão pendente |
| Chrome Android | nenhum | não executado | decisão pendente |

Política atual de polyfill: nenhuma. Antes do piloto externo é necessário definir
público/mínimos, testar as APIs acima e escolher entre elevar o mínimo ou incluir
polyfill específico; não se deve adicionar pacote preventivamente.

## Testes atuais

Há três arquivos e seis testes, todos na área de tenant/apresentação:

- resolução, ordenação, visibilidade e fallback da navegação;
- redirecionamento do primeiro acesso;
- atualização e recarga da apresentação do tenant.

Não há teste do cliente HTTP, login/refresh/logout, rotas protegidas, páginas de
domínio, formulários, estados de erro, WebSocket, acessibilidade automatizada ou
contrato. F0A deve fechar a infraestrutura e o smoke mínimo antes de novas telas.

## Divergências e decisões pendentes

| Pergunta verificável | Evidência atual | Próxima prova/dono |
|---|---|---|
| `pronto` significa implementado ou liberável? | Sete rotas reais ainda têm lacunas de gate. | Definir semântica em F1/F2; não usar o campo como gate. |
| Qual é o artefato visual canônico? | Protótipo citado não está versionado. | Produto/design fornece arquivo ou confirma que briefing + tokens bastam. |
| Quais browsers e versões têm suporte? | Nenhum contrato ou teste real. | Decisão de produto antes do piloto; automatizar smoke depois. |
| API e frontend serão sempre mesma origem? | Cookies, CSP e WebSocket pressupõem isso; `VITE_API_URL` permite separar apenas o HTTP. | Arquitetura de deploy define topologia em F3/F4A. |
| Como representar unidade, entitlement e permissão? | Usuário expõe tenant; navegação é apenas preset visual. | Backend 04/06 + frontend F2. |
| Qual timezone e moeda vêm do tenant/unidade? | Código fixa `America/Sao_Paulo`, BRL e `number` em centavos. | Publicar contrato em F3 e migrar formulários em F6. |
| Onde o frontend de produção será servido? | Build existe, mas não há imagem/pipeline/proxy documentado. | Prompt backend 24 + F11/F13. |

Também divergem do acabamento esperado: `index.html` usa `lang="en"`, título
`frontend`, o README ainda é o template do Vite e a casca não é responsiva.

## Riscos priorizados e encadeamento

| Impacto | Achado | Prompt que deve tratar |
|---|---|---|
| Segurança/P0 | Autorização fina e estados 403/404 ausentes. | backend 04/06, frontend F2 |
| Contrato/P0 | DTO manual, erro não RFC 9457 e OpenAPI ausente. | backend 05, frontend F3 |
| Segurança/P0 | Refresh CSRF depende da topologia; CSP mantém inline style. | F4A/F4 |
| Dados/P0 | Listas incompletas/sem cursor e primeira página invisível em contatos. | backend 10/14, F7/F8 |
| Sessão/P0 | Sem sincronização entre abas ou teste de rajada de 401. | F0A/F4 |
| Qualidade/P0 | 6 testes cobrem só apresentação do tenant. | F0A e testes em cada PR |
| UX/P0 | Shell e inbox não têm fluxo mobile; 403/404 não existem. | F2/F7/F10 |
| Modelo/P0 | Moeda/fuso fixos e parsing monetário com ponto flutuante. | F3/F6 |
| Operação | Sem CI/deploy frontend, telemetry ou error boundary. | backend 24, F13 |
| Desempenho | Bundle inicial 276,08 kB e fontes de vários alfabetos sem budget. | F11, depois de baseline por rota |

## Evidência executável

| Comando | Resultado | Artefato |
|---|---|---|
| `npm ls --depth=0` | 25 dependências diretas resolvidas, sem pacote inválido | árvore local de dependências |
| `npm run lint` | passou, 0 erros e 3 avisos de Fast Refresh | saída do Oxlint |
| `npm test` | 3 arquivos e 6 testes passaram | saída Vitest 4.1.10 |
| `npm run build` | passou, 1.864 módulos transformados em 571 ms | `frontend/dist/` |

Build atual: JS inicial 276,08 kB (88,72 kB gzip), CSS 58,18 kB
(27,25 kB gzip) e chunk do inbox 31,17 kB (9,35 kB gzip). Não há source maps.
Esses números são fotografia local, ainda não budgets de aprovação.

Nenhum código de produção, dependência ou configuração foi alterado por este
diagnóstico.
