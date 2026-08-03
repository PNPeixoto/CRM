# Revisão técnica integrada — CRM PNP

> Execução: 2026-08-02, 18:35–20:10 UTC (15:35–17:10 BRT).
> Modo somente leitura. Nenhum código, migration, dependência, manifesto ou
> documento de produto foi alterado por esta revisão.
>
> **Segunda passagem:** o Docker estava parado na primeira parte da revisão e
> foi restabelecido durante a execução. A suíte de backend foi reexecutada e a
> evidência de banco/RLS foi obtida. As seções abaixo já refletem esse
> resultado; o histórico do bloqueio está preservado na seção 2.

---

## 1. Veredito executivo

> **Atualização de 2026-08-03.** O veredito abaixo descreve o estado no momento
> da revisão e permanece como escrito. O que mudou desde então: AUTZ-001
> resolvido, AUTZ-002 parcialmente e GOV-001 resolvido; a suíte passou de 82
> para 112 testes, todos verdes.
>
> **Piloto controlado** deixa de estar bloqueado por ausência de autorização e
> por rastreabilidade, e passa a depender apenas de ENV-001 — operacional, ainda
> não tocado. Auditoria (AUDIT-001) segue inexistente, então **produção — não
> apto** não muda.
>
> Uma revisão corretiva em 2026-08-03 encontrou oito lacunas na primeira
> implementação do Prompt 06, três delas de exposição real: recursos coletivos
> aceitando alcance próprio, a consolidação `UNIT + OWN` anulando concessão
> válida, e controllers que buscavam o id antes de checar permissão, criando
> oráculo de existência entre 403 e 404. Todas fechadas e cobertas por teste.

O núcleo transacional está sólido: isolamento por tenant apoiado em RLS com
papel restrito, webhook que persiste antes de confirmar, credenciais de canal
write-only e regras de dinheiro/relatório coerentes com o contrato.

**Demonstração — apto, com ressalva.** As jornadas P0 existem e o frontend
está verde. A ressalva é operacional, não funcional: 203 arquivos não
versionados sustentam o estado atual.

**Piloto controlado — não apto.** Não existe autorização por ação, escopo ou
registro. Qualquer usuário autenticado tem CRUD total sobre todo o tenant.
Isso é coerente com o Gate B estar aberto, mas impede piloto com perfis
distintos.

**Produção — não apto.** Além do acima, auditoria — classificada como P0 pelo
próprio produto — não existe, e o ambiente de desenvolvimento em execução não
corresponde ao código-fonte.

O Gate A **se sustenta**: com o Docker restabelecido, os 82 testes passaram,
incluindo isolamento cross-tenant com papel runtime restrito, privilégios
mínimos das funções e caminho de atualização de migrations.

Não encontrei P0. Encontrei 4 achados P1, 3 P2 e 1 P3.

---

## 2. Baseline e limitações

| Item | Valor |
|---|---|
| Data/hora | 2026-08-02T18:35Z (UTC-3 local) |
| Branch / commit | `main` / `793777d` |
| Working tree | **203 arquivos não commitados** (113 novos, 90 modificados) |
| Java | Temurin 25.0.4 |
| Node / npm | 24.18.0 / 11.16.0 |
| Migrations | V1–V11 + `R__organizacao_desenvolvimento`, `R__tenant_profiles_desenvolvimento`, `V900` (seed dev) |
| Perfis | `application.yml`, `-dev`, `-prod`, `-test` |
| Manifest backend | v3; prompts 00–05 `completed`, 06 em diante `ready` |
| Manifest frontend | v4; F0–F3 `completed` |

### Bloqueio encontrado e resolvido durante a revisão

Na primeira passagem o **Docker Desktop estava parado**: serviço
`com.docker.service` em `Stopped` e pipe `dockerDesktopLinuxEngine`
inexistente. A suíte abortou com `Tests run: 82, Errors: 47` — todos cascata
de uma única causa, `Previous attempts to find a Docker environment failed`,
propagada de `TestcontainersConfiguration.postgresContainer`. Nenhum dos 47
era defeito de código.

O Docker foi restabelecido e a suíte reexecutada: **82 testes, 0 falhas, 0
erros, BUILD SUCCESS**. As evidências de banco, RLS, papéis e caminho de
atualização passaram a existir e estão registradas no apêndice.

### O que permanece não verificado

- **UX e WCAG:** nenhuma auditoria de acessibilidade foi executada.
- **Build e scan da imagem:** não reconstruí nem reescaneei a imagem; a
  evidência de imagem é a do Prompt 01, de 2026-08-01.
- **Backup, restore, rollback, SLO e alertas:** não exercitados.
- **Carga e escala:** não medidos.

Não instalei, atualizei nem alterei dependências. Não executei teste
destrutivo nem removi volumes. Segredos não foram lidos nem reproduzidos;
propriedades de teste aparecem apenas por nome.

---

## 3. Mapa de cobertura

| Dimensão | Arquivos/comandos | Status |
|---|---|---|
| Jornadas P0 e invariantes | controllers + suíte de integração | comprovado |
| Contato, funil, oportunidade, tarefa | `*Controller.java`, `FunilPorSegmentoTest` | comprovado |
| Conversa, canal, roteamento | `TelegramWebhookController`, `IngestaoTransacionalTest` | comprovado |
| Onboarding, segmento, navegação | `FunilPadraoService`, `SegmentPresetCatalogTest` | comprovado |
| Fronteiras do monólito | `FronteiraDeModulosTest` | **comprovado** |
| Transações, eventos, concorrência | `IngestaoTransacionalTest`, `FilaDeSaidaTest` | comprovado |
| Login, sessão, reset, MFA | `AutenticacaoSeguraTest`, `PasswordSecurityTest`, `RefreshTokenRotacaoTest` | **comprovado** |
| Autorização e IDOR | busca por enforcement em todos os controllers | **comprovado — ausente** |
| RLS e papéis PostgreSQL | `BancoSegurancaTest`, `IsolamentoEntreTenantsTest` | **comprovado** |
| Migrations e upgrade | `MigracaoDeAtualizacaoTest` | **comprovado** |
| Contrato HTTP/OpenAPI | `npm run api:check` | **comprovado** (sem diff) |
| Webhooks e provedores | `TelegramWebhookController:63–120` | comprovado (estático) |
| WebSocket | `TopicosTempoReal`, testes unitários | parcial |
| Frontend e contrato gerado | `npm test`, `lint`, `build` | **comprovado** |
| UX e WCAG | não auditado nesta execução | não verificado |
| Testes e CI | `mvnw test`, `npm test` | comprovado com limitação |
| Dependências e segredos | inspeção de `.env.example`, perfis | parcial |
| Containers e ambientes | Dockerfile/compose lidos, não executados | parcial |
| Backup, rollback, observabilidade | não auditado | não verificado |
| LGPD, auditoria e retenção | módulo `audit` inspecionado | **comprovado — ausente** |

### Resultado dos comandos executados

| Comando | Resultado |
|---|---|
| `backend> mvnw test` (1ª passagem, sem Docker) | BUILD FAILURE — `82 run, 0 falhas, 47 erros`, todos por ausência de runtime de container |
| `backend> mvnw test` (2ª passagem, com Docker) | **BUILD SUCCESS — `82 run, 0 falhas, 0 erros`**, 23 classes |
| `frontend> npm run api:check` | passou, sem diferença de contrato |
| `frontend> npm run lint` | 0 erros, 3 avisos `react(only-export-components)` |
| `frontend> npm test -- --run` | **56 testes em 14 arquivos, todos passaram** |
| `frontend> npm run build` | passou |
| `docker info` / `docker ps` | daemon indisponível |

---

## 4. Achados

> **Nota de fechamento — 2026-08-02, execução do Prompt 06.** AUTZ-001 está
> **resolvido**; AUTZ-002 está **parcialmente resolvido**. O detalhe de cada um
> vem ao fim da respectiva seção, preservado o texto original do achado: um
> relatório reescrito para parecer certo desde o início perde a única coisa que
> ele tem de útil, que é o registro do que estava errado.

### `[P1] AUTZ-001 — Nenhum endpoint de domínio decide autorização por ação, escopo ou registro`

- **Tipo:** desvio arquitetural / controle ausente
- **Certeza:** confirmado
- **Gate afetado:** B
- **Regra:** `00-CONTEXTO-CANONICO.md` §2 — "Autorização considera ação, escopo
  e registro". `GATES.md` Gate B — "autorização cobre ação, escopo e registro".
- **Evidência:** busca por `@PreAuthorize|hasPermission|hasAnyRole|permissions()`
  em `backend/src/main/java/br/com/pnp/crm` retorna apenas
  `identity/internal/MfaService.java`, `organization/api/OrganizationAccess.java`,
  `organization/internal/OrganizationAccessService.java` e o handler de exceção.
  Nenhum dos 11 `@RestController` consulta permissão.
  `OrganizationAccess` é injetado em **um único** consumidor —
  `MfaService.java:35` — e apenas para decidir obrigatoriedade de MFA.
- **Cenário:** usuário autenticado comum do tenant T obtém token válido e chama
  `DELETE /api/contatos/{id}`, `POST /api/oportunidades/{id}/mover` ou
  `POST /api/canais` de qualquer registro de T. A requisição é aceita: o único
  controle é "estar autenticado" mais o RLS, que isola tenant e não perfil.
- **Resultado esperado:** a ação é negada quando o perfil não tem a permissão,
  quando o registro está fora do escopo (UNIT/OWN) ou quando o membership não
  está vigente.
- **Impacto:** dentro de um tenant não há separação de privilégio. Um atendente
  pode apagar contatos, reconfigurar canais e mover oportunidades alheias.
- **Abrangência:** contact, deal, task, conversation, channel, report — todos os
  fluxos P0 com escrita.
- **Correção mínima:** aplicar a decisão de `OrganizationAccess` na camada de
  aplicação de cada módulo, começando pelas escritas; derivar escopo do
  membership e filtrar/validar o registro alvo.
- **Teste de regressão:** para cada recurso, um teste em que perfil sem
  permissão recebe 403 e perfil com escopo UNIT não alcança registro de outra
  unidade.
- **Dependências:** é exatamente o conteúdo do Prompt 06. **Não é surpresa** —
  é a lacuna que mantém o Gate B aberto. Está listado aqui porque a matriz de
  cobertura exige o registro explícito e porque a severidade real precisa ficar
  visível antes de qualquer piloto.

> **Resolvido em 2026-08-02 pelo Prompt 06.** Os onze controllers passaram a
> decidir por ação e por registro através de `Autorizacao`; o alcance vem do
> membership vigente e o recorte por responsável é aplicado na consulta.
> Usuário sem membership vigente perde acesso na requisição seguinte, sem
> esperar o token expirar. Provas em `AutorizacaoPorAlcanceTest` (10 cenários,
> todos com dono e intruso) e `EscutaDeTopicoTest` (4). Ver
> `contexto/sessoes/2026-08-02T2340-main-prompt-06-autorizacao.md`.
>
> A revisão não alcançou dois defeitos que só apareceram ao escrever o caso
> negativo, e vale registrá-los porque explicam o limite de uma leitura
> estática: (1) o alcance próprio era inalcançável por construção, porque
> `AuthorizedContext.scopes()` sempre contém o próprio tipo do contexto — a
> primeira implementação da decisão parecia correta e concedia o tenant inteiro;
> (2) o SUBSCRIBE de WebSocket conferia o tenant do destino e não a permissão,
> entregando mensagem de cliente em tempo real a quem o REST negava.

### `[P1] AUTZ-002 — Modelo organizacional persistido existe, mas está desconectado da aplicação e do frontend`

- **Tipo:** desvio arquitetural
- **Certeza:** confirmado
- **Gate afetado:** B, C
- **Regra:** `00-CONTEXTO-CANONICO.md` §4 — "A API pública `OrganizationAccess`
  é a fronteira organizacional esperada"; alcances TENANT/UNIT/OWN.
- **Evidência:**
  - `grep -rn "unitId|unit_id|UNIT"` nos módulos `contact`, `deal`, `task` e
    `conversation` retorna **zero ocorrências**;
  - `ContactRepository.buscar(...)` recebe apenas `tenantId` (linha 35);
  - `OrganizationController` expõe somente `@GetMapping("/contextos")` — não há
    endpoint que aplique `selectUnit`;
  - `frontend/src/app/router.tsx:44` fabrica o contexto no cliente:
    `[{ id: usuario.tenantId, rotulo: 'Empresa atual' }]` — o frontend **não
    consome** `GET /api/organizacao/contextos`;
  - `AppLayout.tsx:188` só renderiza o seletor quando
    `contextosAutorizados.length > 1`, condição inalcançável com a lista
    fabricada acima.
- **Cenário:** um tenant com duas unidades e um gestor de unidade. O backend
  conhece a unidade (V10), o `AccessSummary` sabe informá-la, e nenhuma
  consulta de contato/oportunidade/tarefa a utiliza. O gestor vê o tenant
  inteiro; a UI sequer oferece a troca.
- **Resultado esperado:** ou o escopo de unidade é aplicado às consultas, ou o
  modelo é declarado explicitamente como preparação e a UI não carrega código
  de troca de contexto.
- **Impacto:** investimento de schema e API sem efeito; risco de alguém supor
  que o escopo já funciona. O seletor de unidade é código não alcançável.
- **Correção mínima:** consumir `/organizacao/contextos` no frontend **ou**
  remover o seletor até o Prompt 06; no backend, propagar o contexto escolhido
  às consultas.
- **Teste de regressão:** teste que prove que usuário com escopo UNIT não lista
  registro de outra unidade.

> **Parcialmente resolvido em 2026-08-02 pelo Prompt 06.** O achado oferecia
> duas saídas; foi tomada a segunda — declarar o escopo de unidade como
> preparação, explicitamente. `ADR-0008` registra que `UNIT` não decide sobre
> registro de domínio enquanto nenhuma tabela de domínio declarar unidade, e por
> quê: inferir a unidade do registro pela unidade de quem o criou faria a
> autorização reescrever o passado toda vez que alguém fosse transferido.
> A decisão falha fechada — `EscopoDeUnidadeNaoDecide` está coberto em
> `AutorizacaoPorAlcanceTest`, que prova negação, não permissão silenciosa.
>
> **Permanece aberto:** o seletor de contexto do frontend continua alimentado
> por lista fabricada em `router.tsx`, e `GET /api/organizacao/contextos` segue
> sem consumidor. O backend ganhou `GET /api/organizacao/permissoes`, que
> devolve permissão → alcance para o menu, e ele também ainda não é consumido.
> Pertence à trilha de frontend.
>
> O teste de regressão pedido — usuário com escopo UNIT não lista registro de
> outra unidade — **não é escrevível hoje** e não foi escrito: sem `unit_id` nas
> tabelas de domínio não existe "registro de outra unidade" a ser negado. Ele
> nasce junto com a migration prevista no ADR-0008.

### `[P1] ENV-001 — O ambiente de desenvolvimento em execução não corresponde ao código-fonte`

- **Tipo:** operação / evidência
- **Certeza:** confirmado
- **Gate afetado:** A, E
- **Regra:** `GATES.md` Gate A — "dev sobe do zero e não depende de estado
  manual oculto"; `00-CONTEXTO-CANONICO.md` §6 — "Banco está em V11, com
  modelo organizacional, sessões, recuperação e MFA".
- **Evidência:**
  - imagem em execução: `crm-pnp-backend:0.0.1-793777d-p01-r3`, criada em
    **2026-08-01T22:51Z** — o artefato do Prompt 01;
  - `flyway_schema_history` do banco em execução para em **V8 + V900**;
    V9, V10 e V11 existem no disco e **não estão aplicadas**;
  - consulta por tabelas de membership/unit/role/mfa/session no banco em
    execução retorna **0**.
- **Cenário:** qualquer verificação manual contra `localhost:8080` — inclusive
  um login bem-sucedido — exercita o build de 01/08, sem modelo
  organizacional, sem MFA e sem sessões. O resultado parece confirmar o estado
  declarado e não confirma.
- **Resultado esperado:** o ambiente de desenvolvimento reflete o código atual,
  ou está explicitamente marcado como artefato congelado de uma fase anterior.
- **Impacto:** risco de falso positivo em toda evidência manual. É o tipo de
  divergência que faz alguém declarar pronto o que nunca foi executado.
- **Abrangência:** todo teste manual, demonstração e captura feitos contra o
  ambiente local desde 01/08.
- **Correção mínima:** reconstruir a imagem a partir do código atual e subir o
  compose com volume novo, ou documentar que o ambiente persistente é um
  artefato histórico.
- **Teste de regressão:** verificação de readiness que compare a versão de
  schema esperada pela aplicação com a aplicada no banco, falhando quando
  divergirem.

> **EVID-001 (Gate A sem evidência reproduzível) — resolvido durante esta
> revisão.** Era consequência do Docker parado. Com o daemon restabelecido, a
> suíte completa passou e a evidência do Gate A foi obtida. Permanece apenas a
> recomendação de TEST-001 sobre como a falha de infraestrutura se apresenta.

### `[P1] GOV-001 — 203 arquivos não versionados sustentam o estado declarado`

> **Resolvido em 2026-08-03.** O acervo foi versionado na `main` em cinco
> commits por área sobre `793777d`, com ambas as suítes verdes no momento do
> commit e nenhum segredo entre os 476 arquivos rastreados.

- **Tipo:** operação
- **Certeza:** confirmado
- **Gate afetado:** A, E
- **Regra:** `GATES.md` Gate E — "CI/CD produz artefato rastreável"; toda
  evidência exige commit identificável.
- **Evidência:** `git status --porcelain` → 113 não rastreados + 90
  modificados, sobre `793777d`. Entre eles: `V6`, `V7`, `V8`, `V9`, `V10`,
  `V11`, `R__organizacao_desenvolvimento.sql` e uma **modificação em
  `V1__estrutura_inicial.sql`**, que já foi aplicada em bancos locais.
- **Cenário:** qualquer perda do working tree destrói seis migrations e o
  modelo organizacional. Além disso, toda evidência registrada como
  "`793777d` + working-tree" é irreproduzível por terceiros.
- **Resultado esperado:** o estado avaliado corresponde a um commit.
- **Impacto:** rastreabilidade e recuperação. A alteração da V1 pós-aplicação
  é o item mais delicado: contraria "migrations aplicadas são imutáveis"
  (`00-CONTEXTO-CANONICO.md` §2) e pode gerar divergência de checksum em
  bancos que já a aplicaram.
- **Correção mínima:** commitar o trabalho; para a V1, decidir explicitamente
  entre aceitar o rebaseline local documentado ou reverter a edição e mover a
  mudança para uma migration nova.
- **Teste de regressão:** subir banco vazio e aplicar V1→V11; e aplicar sobre
  um banco no estado anterior, verificando `flyway validate`.
- **Nota:** não foi possível comparar checksums hoje (Docker parado).

### `[P2] AUDIT-001 — Módulo de auditoria não existe`

- **Tipo:** planejado/não implementado com impacto de conformidade
- **Certeza:** confirmado
- **Gate afetado:** E
- **Regra:** `contexto/00-projeto.md` §9 lista auditoria como **P0**;
  ADR-0002 define auditoria append-only com retenção.
- **Evidência:** `backend/.../audit/` contém apenas `package-info.java`.
  Nenhuma migration cria tabela de auditoria (as ocorrências de "audit" em
  V1/V5/V9 são comentários e colunas `created_by`/`updated_by`).
- **Impacto:** nenhuma trilha de quem alterou o quê. Sem isso não há
  investigação de incidente nem atendimento a direito do titular.
- **Correção mínima:** Prompt 17, conforme já planejado.
- **Classificação:** P2 e não P1 porque está explicitamente planejado e o
  Gate E não é pré-requisito de demonstração — mas registro que o produto
  classifica auditoria como P0, o que conflita com sua posição no roteiro.

### `[P2] ORD-001 — A ordem do roteiro coloca trabalho P1 antes de item P0`

- **Tipo:** documentação / planejamento
- **Certeza:** confirmado
- **Gate afetado:** D, E
- **Regra:** `contexto/00-projeto.md` §9 — "Nada de P1 ou P2 deve ser iniciado
  enquanto houver item de P0 aberto". P0 inclui auditoria; P1 inclui WhatsApp,
  Instagram, automações e integrações.
- **Evidência:** `manifest.yaml` posiciona 13 (adaptadores de provedor),
  15 (automações) e 16 (conector HTTP) no Gate D, antes de 17 (auditoria) no
  Gate E.
- **Impacto:** o roteiro, se seguido literalmente, viola o próprio princípio de
  priorização do produto.
- **Nota:** o `02-estado-atual.md` já registra essa pendência. Confirmo a
  inconsistência e mantenho o achado para que a correção tenha dono.

### `[P2] TEST-001 — Falha de infraestrutura de teste se apresenta como 47 erros de teste`

- **Tipo:** operação / evidência
- **Certeza:** confirmado
- **Gate afetado:** A
- **Evidência:** todos os 47 erros são
  `ApplicationContext failure threshold (1) exceeded`, cascata de uma única
  causa: `Previous attempts to find a Docker environment failed`.
- **Impacto:** o relatório de teste esconde a causa. Um leitor apressado
  concluiria que há 47 defeitos.
- **Correção mínima:** verificação explícita de pré-requisito (Docker
  disponível) antes da suíte, falhando com mensagem única e clara.

### `[P3] FE-001 — Três avisos de Fast Refresh por exportação mista`

- **Tipo:** manutenção
- **Certeza:** confirmado
- **Evidência:** `button.tsx:54`, `TenantPresentationContext.tsx:62`,
  `AuthContext.tsx:59` — `react(only-export-components)`.
- **Impacto:** apenas experiência de desenvolvimento (HMR recarrega o módulo
  inteiro). Sem risco funcional.

---

## 5. Regras de negócio — invariantes verificadas

| Regra | Implementação | Teste | Resultado | Evidência |
|---|---|---|---|---|
| Webhook persiste antes de confirmar | `TelegramWebhookController` | não executável hoje | **conforme** (estático) | grava em `:84`, responde `200` em `:90` |
| Segredo de webhook em tempo constante | idem | — | **conforme** | `MessageDigest.isEqual` em `:118` |
| Conexão desconhecida não se denuncia | idem | — | **conforme** | `404` em `:72`, `403` em `:80` |
| Credenciais de canal write-only | `CanalController` | — | **conforme** | `CanalResponse` expõe só `temToken`/`temSegredoWebhook` (`:134-136`) |
| Conversão = ganhos/(ganhos+perdidos), 1 casa | `DashboardController` | — | **conforme** | `:70-76`, divisor zero tratado |
| Dinheiro em centavos, inteiro | `DealEntity`, DTOs | — | **conforme** | `value_cents bigint`, sem ponto flutuante |
| Relatório usa API pública dos módulos | `DashboardController` | — | **conforme** | injeta `*Metrics` de cada módulo, sem repositório alheio |
| Referências validadas no mesmo tenant | `DealController`, `TaskController` | não executável hoje | **conforme** (estático) | `contacts.exists(...)`, `users.existsActive(...)` antes de aplicar |
| Funil padrão criado uma única vez | `FunilPadraoService` | não executável hoje | **conforme** (estático) | `insertDefaultIfAbsent` com `ON CONFLICT` sobre índice parcial |
| Onboarding precede o funil | idem | — | **conforme** | `PerfilInicialPendenteException` quando incompleto |
| Autorização por ação/escopo/registro | **ausente** | ausente | **não conforme** | ver AUTZ-001 |
| Escopo de unidade aplicado a consultas | **ausente** | ausente | **não conforme** | ver AUTZ-002 |
| Auditoria append-only | **ausente** | ausente | **não implementado** | ver AUDIT-001 |

---

## 6. Arquitetura e segurança

**O que está bem resolvido.** A fronteira de módulos é real: 17 pacotes `api/`
com tipos públicos e nenhum repositório cruzado; o módulo `report` compõe
métricas pelas APIs dos donos em vez de consultar tabelas alheias; a
comunicação `channel → conversation` usa evento para não criar ciclo. O
`TenantAwareDataSource` aplica o tenant na obtenção da conexão, que é o único
ponto por onde toda consulta passa. A V9 fecha a superfície de funções,
revogando `EXECUTE` de `PUBLIC` e do runtime e concedendo apenas o necessário.

**A lacuna estrutural.** Existem hoje dois modelos de acesso: o **efetivo**
(autenticado + RLS por tenant) e o **pretendido** (`OrganizationAccess` com
papéis, permissões e escopos). O segundo está persistido e exposto, mas não
governa nenhuma decisão de domínio. Enquanto os dois coexistirem, qualquer
leitura do schema sugere um controle que não existe em runtime — e é assim que
um controle "esquecido" vira incidente.

**Ameaças e cobertura.**

| Ameaça | Controle atual | Situação |
|---|---|---|
| Tenant malicioso lendo dados alheios | RLS `FORCE` + papel runtime restrito | conforme por inspeção; **não reexecutado hoje** |
| Provedor forjando webhook | segredo em tempo constante + 404/403 uniformes | conforme |
| Usuário comum agindo como administrador | `Autorizacao` por ação e por registro | coberto desde o Prompt 06 |
| Gestor de unidade acessando outra unidade | escopo `UNIT` falha fechada | mitigado por negação, **não** por recorte — ADR-0008 |
| Atendente sem acesso à caixa ouvindo o tempo real | permissão revalidada no SUBSCRIBE | coberto desde o Prompt 06 |
| Token roubado | 15 min + refresh rotativo com família | não reexecutado hoje |
| XSS lendo credencial de canal | resposta write-only | conforme |
| Segredo em log/resposta | `.env.example` só com nomes; perfis prod sem default | conforme por inspeção |

---

## 7. Gates

| Gate | Veredito | Justificativa |
|---|---|---|
| **A — Fundação** | **aprovado, com ressalva operacional** | Evidência positiva reproduzida hoje: 82/82 verdes, incluindo `BancoSegurancaTest` (RLS forçado, runtime sem `SUPERUSER`/`BYPASSRLS`, funções com `search_path` fixo), `IsolamentoEntreTenantsTest` (SELECT/UPDATE/DELETE cross-tenant negados) e `MigracaoDeAtualizacaoTest`. Ressalva: ENV-001 — o ambiente em execução não reflete o código, então "dev sobe do zero" está provado pelos Testcontainers, não pelo compose persistente. |
| **B — Segurança do núcleo** | **reprovado na revisão; parte de autorização suprida em 2026-08-02** | Na revisão: AUTZ-001 e AUTZ-002, autorização inexistente. Após o Prompt 06: autorização por ação e registro aplicada e provada (96/96 verdes, `AutorizacaoPorAlcanceTest` com caso negativo real sob runtime restrito); escopo de unidade declarado como preparação em ADR-0008. O gate **continua aberto** até o Prompt 07 — baseline ASVS e threat model — e as evidências frontend F4A/F4. |
| **C — Produto utilizável** | **aberto por planejamento** | Frontend verde e contratos gerados sem diff, mas F4A/F4/F7 pendentes e WCAG não auditado nesta execução. |
| **D — Omnichannel** | **aberto por planejamento** | Webhook e idempotência conformes por inspeção; adapters, mídia e reconciliação não auditados. |
| **E — Operação e conformidade** | **reprovado** | Auditoria inexistente (AUDIT-001); backup/rollback/SLO não auditados; rastreabilidade comprometida por GOV-001. |
| **F — Integrações privadas e escala** | **não aplicável** | Fase não iniciada. Broker STOMP em memória segue impedindo escala horizontal. |

---

## 8. Riscos aceitos e dívidas declaradas

Separados dos achados novos — já constam no contexto do projeto:

- blocklist inicial de senha limitada;
- entrega externa de reset de senha ainda não integrada;
- TOTP suscetível a phishing por natureza do fator;
- janela residual de 15 minutos do access token após revogação;
- duas vulnerabilidades altas em `npm audit` sob avaliação de alcance;
- broker STOMP em memória impede mais de uma instância;
- override de `postgresql.version` em 42.7.12 até o BOM incorporar.

---

## 9. Roadmap de correção

Ordenado por dependência e redução de risco. Sem estimativa de data — não há
dado de capacidade.

1. **Versionar o estado (GOV-001).** Commitar o working tree e decidir o
   tratamento da V1. *Saída:* `flyway validate` limpo em banco novo e em banco
   pré-existente. **É o item mais urgente:** 203 arquivos sem commit sustentam
   tudo o que foi provado hoje.
2. **Realinhar o ambiente local (ENV-001).** Reconstruir a imagem do código
   atual e recriar o volume. *Saída:* `flyway_schema_history` em V11 e
   readiness comparando schema esperado com aplicado.
3. **Aplicar autorização nas escritas (AUTZ-001).** Prompt 06, começando por
   contact/deal/task. *Saída:* perfil sem permissão recebe 403.
4. **Ligar ou remover o escopo de unidade (AUTZ-002).** *Saída:* escopo UNIT não
   alcança outra unidade; ou o seletor sai da casca até existir suporte.
5. **Corrigir a ordem do roteiro (ORD-001).** Mover auditoria para antes do
   trabalho P1. *Saída:* manifesto coerente com a prioridade do produto.
6. **Implementar auditoria (AUDIT-001).** Prompt 17. *Saída:* trilha
   append-only com retenção e teste de direito do titular.
7. **FE-001.** Separar constantes de componentes. *Saída:* lint sem avisos.

---

## 10. Apêndice de evidências

| ID | Comando / inspeção | Resultado | Data |
|---|---|---|---|
| A01 | `git status --porcelain` | 113 `??` + 90 `M` sobre `793777d` | 2026-08-02 |
| A02 | `backend> mvnw test` | `Tests run: 82, Failures: 0, Errors: 47` — BUILD FAILURE | 2026-08-02 |
| A03 | `surefire-reports/…FunilPorSegmentoTest.txt` | causa raiz: `Previous attempts to find a Docker environment failed` | 2026-08-02 |
| A04 | `docker info`, `Get-Service com.docker.service` | daemon indisponível; serviço `Stopped` | 2026-08-02 |
| A05 | `frontend> npm run api:check` | sem diferença de contrato | 2026-08-02 |
| A06 | `frontend> npm run lint` | 0 erros, 3 avisos | 2026-08-02 |
| A07 | `frontend> npm test -- --run` | 56 testes, 14 arquivos, todos passaram | 2026-08-02 |
| A08 | `frontend> npm run build` | sucesso | 2026-08-02 |
| A09 | grep de enforcement de autorização | só `MfaService` consome `OrganizationAccess` | 2026-08-02 |
| A10 | grep `unitId\|unit_id\|UNIT` em contact/deal/task/conversation | zero ocorrências | 2026-08-02 |
| A11 | `router.tsx:44` | contexto organizacional fabricado no cliente | 2026-08-02 |
| A12 | `find audit/ -type f` | apenas `package-info.java` | 2026-08-02 |
| A13 | inventário de migrations | V1–V11 + 2 repetíveis + seed dev | 2026-08-02 |
| A14 | `application-prod.yml` | `out-of-order: false`, `clean-disabled: true` | 2026-08-02 |
| A15 | `backend> mvnw test` (Docker ativo) | **82 testes, 0 falhas, 0 erros, BUILD SUCCESS**, 23 classes | 2026-08-02 |
| A16 | `BancoSegurancaTest` | RLS forçado nas tabelas tenant; runtime sem `SUPERUSER`/`BYPASSRLS`; funções privilegiadas com `search_path` fixo e superfície mínima | 2026-08-02 |
| A17 | `IsolamentoEntreTenantsTest` | SELECT/UPDATE/DELETE sem filtro não alcançam outro tenant; sem tenant, nenhuma linha; conexão confirmada como runtime restrito | 2026-08-02 |
| A18 | `MigracaoDeAtualizacaoTest`, `OpenApiContractTest`, `PoliticaDeTestesTest` | caminho de atualização, contrato e política de testes verdes | 2026-08-02 |
| A19 | `pg_roles` no compose | `crm_migrator super=true bypassrls=true`; `crm_runtime super=false bypassrls=false` | 2026-08-02 |
| A20 | `flyway_schema_history` + `docker image inspect` no compose | banco em **V8+V900**; imagem `0.0.1-793777d-p01-r3` de 2026-08-01T22:51Z; 0 tabelas de organização/sessão/MFA | 2026-08-02 |

Nenhum segredo, token, cookie, payload de cliente ou dado pessoal foi lido,
transcrito ou registrado nesta revisão. Propriedades de teste aparecem apenas
por nome.
