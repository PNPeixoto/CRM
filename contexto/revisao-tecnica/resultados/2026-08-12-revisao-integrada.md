# Revisão técnica integrada — CRM PNP

- Data local da revisão: 2026-08-12
- Encerramento da coleta: 2026-08-13T02:08Z
- Baseline: `838461d90e46610b3b2ebbf3b3fc8da5c526cad2` (`main`)
- Revisor: Codex
- Método: inspeção estática, execução local das suítes, validação do ambiente Docker,
  consulta do CI público e varredura atual de dependências. Nenhuma correção de
  produto foi aplicada nesta revisão.

## 1. Veredito executivo

O CRM PNP avançou de forma material: a base atual sobe saudável na migration V25,
267 testes de backend e 140 de frontend passam, o contrato OpenAPI está sincronizado
e os controles de autenticação, tenant, RLS, escopos `OWN`/`TEAM`/`TENANT`, canais,
tempo real, auditoria append-only e retenção possuem provas automatizadas relevantes.
Não foi encontrada evidência de bypass entre tenants, exposição de segredo ou perda
ativa de dados. A demonstração interna está pronta, desde que apresentada como MVP
e sem dados reais. Um piloto externo ainda não está pronto: a imagem atual contém
quatro ocorrências `HIGH` corrigíveis, as novas invariantes de acesso podem ser
violadas por operações concorrentes e a trilha de pedidos de privacidade perde ou
classifica incorretamente eventos. Produção também permanece bloqueada pelos itens
19–24 do backend e F10–F13 do frontend, incluindo SLOs, restore medido, CI/CD,
telemetria, E2E e as provas manuais finais de WCAG. Não há P0; há 3 P1, 3 P2 e 1 P3.

## 2. Baseline, escopo e limitações

### 2.1 Estado exato revisado

| Item | Evidência atual |
| --- | --- |
| Git | `main` limpa em `838461d`; seis commits à frente de `origin/main` (`b0b4c22`) |
| Backend | Java 25.0.4, Maven 3.9.16, Spring Boot 4.1.0, Spring Modulith 2.1.0 |
| Frontend | Node 24.18.0, npm 11.16.0, React 19.2.4, Vite 8.1.5 |
| Banco | PostgreSQL 17; 25 migrations versionadas; ambiente vivo em `25:true` |
| Infra local | Docker 29.6.2 / Compose 5.3.1; aplicação, PostgreSQL e Redis saudáveis |
| API | readiness `UP`; snapshot com 65 paths e 78 operações HTTP |
| Código | 282 arquivos Java de produção, 63 de teste, 128 arquivos no `frontend/src`, 30 arquivos de teste frontend |
| Funcionalidade entregue | Prompts backend 00–18 e frontend F0/F0A/F1–F6/F8 marcados como concluídos |
| Planejado | backend 19–28 e frontend F7/F9–F13 permanecem `ready` conforme manifests |

### 2.2 Fontes inspecionadas

Foram lidos os documentos canônicos do projeto, glossário, modelo organizacional,
ADRs 0001–0015, preâmbulos, gates, manifests backend/frontend, documentação de
autenticação, banco, navegador, segurança, privacidade e sessões recentes. No código,
o escopo incluiu configurações, migrations, módulos Spring Modulith, controladores,
repositórios, filtros, autorização, auditoria, privacidade, canais, contrato OpenAPI,
rotas, navegação, sessão, componentes, páginas e testes.

### 2.3 Referências externas vigentes

- OWASP ASVS 5.0.0 continua sendo a versão estável indicada pela fonte oficial:
  <https://owasp.org/www-project-application-security-verification-standard/>.
- NIST SP 800-63-4, incluindo SP 800-63B-4, é final desde julho de 2025 e substitui
  a revisão 3: <https://csrc.nist.gov/pubs/sp/800/63/4/final>.
- WCAG 2.2 é a recomendação vigente adotada pelo projeto:
  <https://www.w3.org/TR/WCAG22/>.

Essas referências confirmam que as versões normativas escolhidas pelo projeto estão
atuais. Este relatório não declara conformidade legal ou certificação ASVS/WCAG; ele
registra somente controles e evidências observados.

### 2.4 Limitações

- Não foram usados dados pessoais, tokens, cookies, chaves nem payloads reais.
- Não foram executadas jornadas manuais com leitor de tela nem zoom nativo a 200%; a
  auditoria de 2026-08-08 já registra esses dois resíduos para F10.
- Não existe runner E2E reproduzível no repositório; F12 continua planejado.
- Não foram ensaiados restore, RPO/RTO, rollback de aplicação/migration, carga ou
  escala horizontal; são entregas explicitamente futuras.
- Não foi feita chamada real a Telegram, WhatsApp oficial ou Evolution nesta revisão.
  Os contract/integration tests e o estado local dos contêineres foram inspecionados.
- O CI público cobre apenas `origin/main`, seis commits atrás do baseline local.

## 3. Contagem e mapa de cobertura

### 3.1 Contagem dos achados

| Severidade | Quantidade |
| --- | ---: |
| P0 | 0 |
| P1 | 3 |
| P2 | 3 |
| P3 | 1 |
| Total | 7 |

| Tipo | Quantidade |
| --- | ---: |
| Vulnerabilidade | 1 |
| Regra de negócio | 1 |
| Privacidade | 1 |
| Contrato/UX de autorização | 1 |
| Desvio arquitetural | 1 |
| Documentação | 1 |
| Qualidade/manutenção | 1 |

Todos os sete achados são confirmados no baseline revisado.

### 3.2 Resumo tabular

| ID | Sev. | Tipo | Título | Evidência principal | Gate | Certeza |
| --- | --- | --- | --- | --- | --- | --- |
| SUPPLY-001 | P1 | vulnerabilidade | Imagem atual contém quatro ocorrências `HIGH` com correção disponível | Trivy 0.70.0 + `backend/Dockerfile:4-7,26` | B | confirmado |
| AUTHZ-001 | P1 | regra de negócio | Concorrência pode remover todos os proprietários ou formar ciclo de equipe | `PapelController.java:224-230`; `EquipeController.java:96-110` | B | confirmado |
| PRIV-001 | P1 | privacidade | Pedido/recusa de privacidade não gera trilha durável e semanticamente correta | `PrivacidadeController.java:62-120`; `AuditWriter.java:19-26` | E | confirmado |
| FE-AUTHZ-001 | P2 | contrato | Rota Acessos é oferecida sem `organization.manage` e informa latência errada | `routes.ts:73-76`; `TeamsPage.tsx:92-100` | C | confirmado |
| ARCH-001 | P2 | desvio arquitetural | SQL nativo atravessa módulos e não é detectado pelo gate Modulith | `PapelRepository.java:113-143`; `DireitosDoTitularService.java:144-225` | F | confirmado |
| DOC-001 | P2 | documentação | Documentos canônicos e de segurança descrevem V24/estado pré-V25 | `contexto/02-estado-atual.md:9-13,123-128` | A–F | confirmado |
| QA-001 | P3 | qualidade | Warnings conhecidos reduzem a margem para futuras atualizações | saída de lint/compilação/teste | nenhum | confirmado |

### 3.3 Mapa de cobertura

| Dimensão | Arquivos/provas principais | Execução | Status |
| --- | --- | --- | --- |
| Regras de negócio e domínio | controllers/repositories de organização, contatos, negócios, tarefas, privacidade; testes de alcance e integrações | suíte backend completa | parcial: regras sequenciais verdes; AUTHZ-001 falha sob concorrência por construção |
| Arquitetura modular | `contexto/01-padroes-tecnicos.md`, ADRs, `package-info.java`, `FronteiraDeModulosTest` | `ApplicationModules.verify()` dentro da suíte | parcial: fronteira Java comprovada; ARCH-001 contorna por SQL |
| Autenticação e sessão | `identity`, `SecurityConfig`, Redis, MFA, refresh, recuperação | testes de login/MFA/rotação/revogação | comprovado para o escopo atual |
| Autorização e multitenancy | `AutorizacaoService`, RLS, escopos, IDOR, mass assignment | testes com PostgreSQL real e usuário runtime | parcial: isolamento comprovado; concorrência administrativa falha |
| Banco e migrations | V1–V25, roles, RLS, constraints, upgrade | 25 migrations do zero + banco vivo V25 | comprovado, ressalvada a regra concorrente não expressa no banco |
| API e contratos | OpenAPI, Problem Details, limites, CSRF, webhooks | `api:check`, `OpenApiContractTest` | comprovado |
| Integrações/omnichannel | Telegram, Evolution experimental, outbox, inbox, STOMP, automações, conector HTTP | contract/integration tests da suíte | comprovado no escopo local; provedores reais não verificados nesta rodada |
| Frontend/UX | rotas, guards, cache, formulários, páginas e componentes | 30 arquivos / 140 testes, axe, lint, build | parcial: FE-AUTHZ-001 e provas manuais F10/F12 pendentes |
| Dependências/supply chain | lockfile, auditor npm, Dockerfile, CI, imagem final | npm audit + Trivy 0.70.0 | falhou: SUPPLY-001 |
| Operação/LGPD | audit, retenção, legal hold, direitos, Compose, readiness | testes + ambiente local | parcial: PRIV-001; SLO/restore/CI-CD planejados |
| Documentação/governança | contexto, ADRs, manifests, banco, backlog, ASVS e threat models | comparação cruzada com código/testes | falhou: DOC-001 |

## 4. Top bloqueadores e causas raiz

1. **Imagem reprovada pelo gate de vulnerabilidades.** A imagem reconstruída mantém
   base Temurin/Alpine com pacotes corrigíveis e uma dependência Java vulnerável. O
   último CI público também falhou no passo Trivy. Causa: versões fixadas ainda não
   incorporam os patches publicados.
2. **Invariantes administrativas são “consultar e depois alterar” sob READ COMMITTED.**
   Os testes cobrem chamadas sequenciais, mas não há serialização para duas alterações
   simultâneas. Causa: regras críticas existem só no controller, sem lock/constraint.
3. **Auditoria de privacidade participa da transação que termina em rollback.** A
   implementação promete registrar pedido e recusa, mas o writer `MANDATORY` perde o
   evento ao propagar a exceção; anonimização ainda reutiliza códigos de exportação.

## 5. Achados completos

## `[P1] SUPPLY-001 — Imagem atual contém vulnerabilidades altas com correção disponível`

- **Tipo:** vulnerabilidade
- **Certeza:** confirmado
- **Gate afetado:** B
- **Regra ou requisito:** o gate de dependency scan deve terminar sem achado alto ou
  crítico não tratado; exceções precisam ser explícitas, justificadas e temporárias.
- **Evidência:** `backend/Dockerfile:4-7,26` fixa Temurin 25.0.3_9/Alpine 3.23. A
  reconstrução `docker build --pull --tag crm-pnp-backend:review ./backend` preservou
  essa base. Trivy 0.70.0 encontrou quatro ocorrências `HIGH` em três CVEs:
  `libexpat` 2.8.1-r0 / CVE-2026-56408 (fix 2.8.2-r0), `p11-kit` e
  `p11-kit-trust` 0.25.5-r2 / CVE-2026-2100 (fix 0.26.2-r0), e
  `httpcore5` 5.4.2 / CVE-2026-54399 (fix 5.4.3). Não houve ocorrência crítica.
  O CI público de `b0b4c22` falhou em “Auditar dependências do backend”.
- **Cenário de reprodução ou ataque:** construir a imagem a partir do baseline e
  executar Trivy com scanners de vulnerabilidade, severidade `HIGH,CRITICAL`,
  `ignore-unfixed` e `exit-code 1`; a execução termina em código 1.
- **Resultado esperado:** imagem sem vulnerabilidade alta/crítica corrigível ou com
  exceção formal que demonstre inaplicabilidade, compensação, responsável e prazo.
- **Impacto:** risco herdado de negação de serviço e de componentes nativos
  vulneráveis; bloqueia o gate de segurança e qualquer promoção externa.
- **Abrangência:** toda implantação feita com a imagem backend atual.
- **Correção mínima recomendada:** atualizar a imagem Temurin/Alpine para uma base
  que contenha os pacotes corrigidos e alinhar `httpcore5` a uma versão estável
  corrigida compatível com o BOM; fixar digest após validar a atualização.
- **Teste de regressão:** reconstruir sem cache de base e exigir Trivy 0.70.0 ou
  posterior com zero `HIGH/CRITICAL` não excepcionados; executar os 267 testes.
- **Dependências ou bloqueios:** compatibilidade da versão `httpcore5` com Spring Boot
  e `httpclient5`; atualização do CI para o mesmo baseline.

## `[P1] AUTHZ-001 — Invariantes de acesso podem ser violadas por operações concorrentes`

- **Tipo:** regra de negócio
- **Certeza:** confirmado
- **Gate afetado:** B
- **Regra ou requisito:** deve sempre existir ao menos uma atribuição viva de papel
  de sistema; ninguém responde a si mesmo e o ciclo direto de equipe deve ser
  impossível, inclusive sob concorrência.
- **Evidência:** `PapelController.java:224-230` executa `count(*) <= 1` e depois
  revoga; `PapelRepository.java:179-188` faz leitura comum, sem lock. Com duas
  atribuições, duas transações podem ler 2 e revogar linhas diferentes, deixando 0.
  `EquipeController.java:96-110` consulta a aresta inversa e depois insere;
  `EquipeRepository.java:39-54` não serializa o par. O índice em
  `V25__equipe_e_alcance_de_equipe.sql:38-42` impede somente a mesma aresta, não
  `A→B` e `B→A`. Os testes `PapeisPersonalizaveisTest:205-227` e
  `AlcanceDeEquipeTest:249-268` são sequenciais; não existe teste concorrente.
- **Cenário de reprodução ou ataque:** dois administradores revogam simultaneamente
  as duas últimas atribuições de sistema; ambas veem contagem 2 e confirmam. De modo
  análogo, duas inclusões simultâneas criam `A→B` e `B→A`, pois nenhuma transação vê
  a aresta ainda não confirmada da outra.
- **Resultado esperado:** no máximo uma operação concorrente confirma; a outra recebe
  erro de domínio e o banco conserva a invariável após todos os commits.
- **Impacto:** o tenant pode ficar sem pessoa capaz de administrar acesso, exigindo
  intervenção no banco. Um ciclo de equipe amplia reciprocamente o conjunto de
  registros visíveis e torna o organograma/auditoria contraditórios.
- **Abrangência:** todos os tenants com dois ou mais administradores capazes de
  executar alterações simultâneas.
- **Correção mínima recomendada:** serializar as alterações por tenant (por exemplo,
  lock transacional em uma linha estável ou advisory lock) antes de verificar e
  escrever; para equipe, usar lock por par normalizado ou regra equivalente no banco.
- **Teste de regressão:** iniciar duas transações reais sincronizadas com barrier,
  confirmar em paralelo e provar: uma revogação é recusada e sobra um proprietário;
  somente uma direção do par de equipe fica vigente.
- **Dependências ou bloqueios:** decisão de estratégia de lock PostgreSQL e mapeamento
  do erro de conflito para Problem Details.

## `[P1] PRIV-001 — Trilha de pedidos de privacidade não é durável nem semanticamente correta`

- **Tipo:** privacidade
- **Certeza:** confirmado
- **Gate afetado:** E
- **Regra ou requisito:** pedido, execução e recusa de exportação/anonimização devem
  ser demonstráveis com ação, alvo, motivo e resultado verdadeiros, inclusive quando
  a operação de negócio falha ou é impedida por legal hold.
- **Evidência:** `PrivacidadeController.java:62-79` registra o pedido de exportação na
  mesma transação que pode falhar, apesar do comentário dizer que ele sobreviverá.
  `PrivacidadeController.java:93-101` captura a recusa, audita e relança a exceção.
  `AuditTrailService.java:52-55` chama `AuditWriter.gravarNoContexto`, definido como
  `Propagation.MANDATORY` em `AuditWriter.java:19-22`; o rollback remove o evento.
  Além disso, anonimização usa `SENSITIVE_CONFIGURATION_CHANGED`, motivos
  `EXPORT_REQUESTED/COMPLETED` e alvo `EXPORT` (`PrivacidadeController.java:98-120`).
  `AuditTrail.java:62-70,99-133` não possui vocabulário próprio de anonimização.
  `DireitosDoTitularTest.java:130-144` valida 422 e preservação do dado, mas não a
  existência do evento negado.
- **Cenário de reprodução ou ataque:** declarar legal hold e pedir anonimização; a
  API responde 422, mas a transação faz rollback do evento `DENIED`. Se a montagem da
  exportação falhar após o primeiro registro, o pedido também desaparece.
- **Resultado esperado:** pedido e recusa persistem independentemente do rollback da
  operação; sucesso de anonimização usa códigos próprios e é atômico com a mutação.
- **Impacto:** a empresa não consegue demonstrar corretamente tratamento de uma
  solicitação de titular; relatórios podem chamar anonimização de exportação/configuração.
- **Abrangência:** titulares submetidos a exportação que falha ou anonimização,
  especialmente registros sob legal hold.
- **Correção mínima recomendada:** criar ações/motivos/alvo próprios para
  anonimização; separar a orquestração de tentativa/recusa da transação de negócio,
  mantendo fail-closed para eventos obrigatórios e persistência independente quando
  a recusa precisa sobreviver ao rollback.
- **Teste de regressão:** forçar falha na exportação e recusa por legal hold; consultar
  `audit_event` e exigir eventos duráveis com ação, motivo, alvo e outcome exatos.
- **Dependências ou bloqueios:** validar com jurídico/produto a nomenclatura e a
  categoria de retenção dos eventos de direitos do titular.

## `[P2] FE-AUTHZ-001 — A rota Acessos é oferecida sem permissão e a mensagem de revogação é imprecisa`

- **Tipo:** contrato
- **Certeza:** confirmado
- **Gate afetado:** C
- **Regra ou requisito:** funcionalidades sem permissão devem ser ocultadas/bloqueadas
  antes de chamar a API, sem prometer uma janela diferente da aplicada pelo servidor.
- **Evidência:** `frontend/src/app/routes.ts:73-76` declara Acessos como `pronto`, mas
  omite `permissaoNecessaria: 'organization.manage'`. Em
  `resolveNavigation.ts:93-99`, permissão ausente vira `nao-publicado`, e
  `resolveNavigation.ts:55-63` considera isso permitido/visível. A página só descobre
  a falta de acesso depois do 403 (`TeamsPage.tsx:55-65`). O texto em
  `TeamsPage.tsx:92-100` diz que a sessão pode levar 15 minutos para perder acesso,
  porém `PapeisPersonalizaveisTest.java:138-153` prova que REST relê o membership e
  revoga na requisição seguinte. A janela residual pertence a inscrição WebSocket já
  ativa, não à sessão REST em geral.
- **Cenário de reprodução ou ataque:** usuário autenticado sem `organization.manage`
  vê/navega para Acessos, dispara consulta e recebe o vazio de 403; após uma revogação,
  a tela apresenta uma latência geral que não corresponde ao REST.
- **Resultado esperado:** rota invisível e guard negado sem a permissão; copy separa
  efeito REST imediato de eventual conexão em tempo real já inscrita.
- **Impacto:** experiência contraditória, chamadas 403 evitáveis e dificuldade para o
  administrador entender quando uma revogação realmente vale.
- **Abrangência:** perfis sem `organization.manage` e administradores da tela Acessos.
- **Correção mínima recomendada:** declarar a permissão na rota, adicionar teste de
  navegação/guard e ajustar a mensagem; tratar a janela STOMP em SEC-011.
- **Teste de regressão:** `resolveNavigation` deve ocultar `teams` sem a permissão e
  `RotaComAcesso` deve redirecionar; teste de copy não pode atribuir 15 minutos ao REST.
- **Dependências ou bloqueios:** fechamento de SEC-011 para prometer revogação também
  em inscrição WebSocket ativa.

## `[P2] ARCH-001 — SQL nativo atravessa fronteiras de módulo sem cobertura do gate arquitetural`

- **Tipo:** desvio arquitetural
- **Certeza:** confirmado
- **Gate afetado:** F
- **Regra ou requisito:** `contexto/01-padroes-tecnicos.md:67-90` define `api` como
  fronteira pública, `internal` privado e comunicação entre módulos por API/evento.
- **Evidência:** `PapelRepository.java:113-143` lê diretamente `app_user`, tabela do
  módulo identity, e o próprio comentário reconhece o desvio para evitar um ciclo
  Java. `DireitosDoTitularService.java:144-225` consulta tabelas de contact,
  conversation, channel, message, deal e task; `AuditQueryService.java:55` também faz
  join com `app_user`. `ApplicationModules.verify()` passa porque ArchUnit enxerga
  dependências Java, não nomes de tabelas dentro de strings SQL.
- **Cenário de reprodução ou ataque:** renomear/alterar a semântica de uma tabela em
  seu módulo proprietário; consumidores SQL de outro módulo compilam normalmente e
  o gate de fronteira continua verde, mas falham em runtime ou exportam dado incorreto.
- **Resultado esperado:** dependências entre módulos aparecem em APIs/eventos ou em
  projeções explicitamente possuídas, testáveis e documentadas.
- **Impacto:** falso senso de isolamento, acoplamento invisível ao schema e maior risco
  de regressão em migrations; não foi observado vazamento porque as consultas filtram
  tenant e continuam sob RLS.
- **Abrangência:** administração de papéis, consulta de auditoria e direitos do titular.
- **Correção mínima recomendada:** introduzir portas públicas de leitura ou projeções
  locais alimentadas por eventos; para privacidade, compor exportadores por módulo.
  Enquanto migra, manter allowlist explícita e teste estático de ownership de tabelas.
- **Teste de regressão:** gate que falha ao referenciar tabela de outro módulo fora da
  allowlist, além dos testes de contrato das novas portas/projeções.
- **Dependências ou bloqueios:** desenho para remover o ciclo identity↔organization e
  decisão de ownership do agregado de exportação LGPD.

## `[P2] DOC-001 — Documentação canônica e de segurança está atrás da implementação V25`

- **Tipo:** documentação
- **Certeza:** confirmado
- **Gate afetado:** A–F
- **Regra ou requisito:** documentos canônicos, banco, threat models e matriz ASVS
  devem representar o baseline usado para decidir gates e orientar novos devs.
- **Evidência:** `contexto/02-estado-atual.md:9-13,123-128` ainda informa 193 testes,
  130 testes frontend e V24; o baseline comprovou 267/140 e V25.
  `contexto/modelo-organizacional.md:54-59` chama TEAM de futuro, embora V25 já o
  persista. `backend/BANCO.md:46-54` termina em V21. A matriz ASVS ainda diz que
  retenção pertence ao Prompt 18 (`asvs-5.0.0-matriz.md:164`) e que exportação não
  existe (`:105`); `threat-models.md:142` também afirma que nenhum endpoint exporta.
  O backlog mantém SEC-016 como CI nunca executado (`backlog.md:307-345`), apesar das
  execuções públicas — atualmente vermelhas.
- **Cenário de reprodução ou ataque:** novo desenvolvedor ou revisor usa os documentos
  indicados como canônicos e conclui que TEAM/retention/export não existem, ou aprova
  gate com contagens/evidências antigas.
- **Resultado esperado:** um único estado atual V25, com links para evidências
  vigentes, riscos residuais e manifests coerentes.
- **Impacto:** decisões e onboarding incorretos, retrabalho e gates não confiáveis.
- **Abrangência:** todas as trilhas de backend, frontend, segurança e operação.
- **Correção mínima recomendada:** atualizar os documentos listados, renomear estados
  parciais do SEC-011/016 e evitar números de teste sem comando/data/baseline.
- **Teste de regressão:** verificação documental no CI compara versão esperada do
  schema com a maior migration e impede referências canônicas inexistentes; checklist
  da revisão exige baseline/data para contagens.
- **Dependências ou bloqueios:** concluir SUPPLY-001 antes de marcar o CI como verde.

## `[P3] QA-001 — Warnings conhecidos reduzem a margem para futuras atualizações`

- **Tipo:** qualidade/manutenção
- **Certeza:** confirmado
- **Gate afetado:** nenhum
- **Regra ou requisito:** builds devem permanecer livres de avisos acionáveis que
  antecipem quebra em versões futuras ou escondam erros de tipagem.
- **Evidência:** lint terminou verde com três warnings `react(only-export-components)`
  em `TenantPresentationContext.tsx:94`, `button.tsx:54` e `AuthContext.tsx:127`.
  A compilação apontou API deprecated em `GlobalExceptionHandler`, operação unchecked
  em `ChannelConnectionLookupImpl` e deprecated em `AlcanceDeEquipeTest`. Mockito
  avisou que o auto-attach do inline mock maker deixará de funcionar em JDK futuro.
- **Cenário de reprodução:** executar lint e `mvnw test` no baseline.
- **Resultado esperado:** zero warning novo; agente Mockito configurado explicitamente.
- **Impacto:** sem falha atual, mas atualizações de Java/React podem transformar aviso
  em quebra ou mascarar comportamento não tipado.
- **Abrangência:** desenvolvimento e CI.
- **Correção mínima recomendada:** separar exports de componentes/contexts, tipar a
  conversão insegura, substituir APIs deprecated e configurar Mockito como agente.
- **Teste de regressão:** lint/compilação sem os avisos catalogados; manter suites verdes.
- **Dependências ou bloqueios:** nenhuma.

## 6. Matriz de regras de negócio

| Invariante | Resultado | Evidência |
| --- | --- | --- |
| Um tenant não lê/escreve dados de outro | comprovado | `AutorizacaoPorAlcanceTest.membershipDeUmTenantNaoAutorizaNoOutro`, RLS forçado e testes com runtime restrito |
| Conta com alcance TENANT vê carteiras de todos e identifica responsável | comprovado | `AutorizacaoPorAlcanceTest.alcanceTenantVeCarteirasComResponsavelIdentificado` |
| Alcance OWN vê somente registros próprios | comprovado | casos negativos de lista, ID e escrita em `AutorizacaoPorAlcanceTest` |
| Gestor TEAM vê a própria carteira e liderados, não terceiros | comprovado | `AlcanceDeEquipeTest` para contato/tarefa, edição e remoção de equipe |
| Revogação REST vale na ação seguinte | comprovado | `PapeisPersonalizaveisTest:138-153` |
| Pelo menos um proprietário de sistema permanece | falhou sob concorrência | AUTHZ-001; cobertura sequencial apenas |
| Ninguém lidera a si mesmo e ciclo direto é recusado | parcial | constraint de auto-gestão + testes sequenciais; ciclo concorrente em AUTHZ-001 |
| Concessão não ultrapassa permissões/alcance do concedente | comprovado no fluxo sequencial | `NaoEscalonamentoTest` (13 casos) e `PapeisPersonalizaveisTest` |
| Webhook persiste/idempotentiza antes de sucesso | comprovado | testes de canal, inbound/outbound e fixtures oficiais na suíte backend |
| Auditoria corporativa é append-only e fail-closed | comprovado em ações catalogadas | V22/V24 e `AuditoriaCorporativaTest`; exceção PRIV-001 |
| Legal hold impede expurgo/anonimização | comprovado | `RetencaoELegalHoldTest`, `LegalHoldImutavelTest`, `DireitosDoTitularTest` |
| Pedido/recusa de titular fica demonstrável | falhou | PRIV-001 |
| Rota sem permissão não é oferecida | falhou na rota Acessos | FE-AUTHZ-001; backend continua negando 403 |
| Contrato OpenAPI e tipos frontend não divergem | comprovado | `OpenApiContractTest` + `npm run api:check` |

## 7. Arquitetura, segurança e ameaças

### 7.1 Mapa atual

| Camada | Estado |
| --- | --- |
| Cliente | React/Vite, rota central, TanStack Query, camada HTTP única, sessão em memória e refresh cookie |
| API | Monólito modular Spring Boot, Problem Details, CSRF, CSP, JWT, autorização relida no banco |
| Domínio | tenant, identity, organization, contacts, deals, tasks, conversations, channels, automation, audit, privacy e reports |
| Dados | PostgreSQL com UUIDv7, FKs compostas, RLS forçado e funções/roles separadas |
| Assíncrono | outbox/filas no banco, Redis para sessão/limites específicos, STOMP simple broker local |
| Integrações | Telegram funcional; Evolution explicitamente experimental; conector HTTP com SSRF/egress controls |
| Operação local | Compose hardened para app, serviços em loopback, readiness e schema health |

### 7.2 Efeitos transversais

- **Tenant:** token define tenant; aplicação usa `TenantContext`; datasource aplica
  `SET LOCAL`; RLS é defesa adicional. As provas negativas permaneceram verdes.
- **Autorização:** permissões e escopo são relidos no banco a cada requisição REST.
  TEAM expande IDs de responsável em um nível. AUTHZ-001 é uma falha de alteração da
  política, não de avaliação de uma política já persistida.
- **Tempo real:** CONNECT/SUBSCRIBE são autenticados e destino de tenant é validado.
  A inscrição já ativa ainda sobrevive a mudança de papel/equipe até reconexão/expiração
  quando não há evento de revogação — risco conhecido SEC-011.
- **Auditoria:** hash, append-only e RLS reduzem adulteração; PRIV-001 quebra a
  completude/semântica especificamente nos direitos do titular.
- **Integrações:** credenciais cifradas, webhook público autenticado e SSRF fail-closed.
  Evolution não deve ser confundida com o canal oficial de produção.
- **Escala:** rate limiting HTTP geral usa memória local e STOMP usa simple broker;
  isso é aceitável no estágio de instância única, mas bloqueia Gate F sem Prompt 28.

## 8. Divergências DB/API/frontend

| Contrato | DB/backend | Frontend/documento | Resultado |
| --- | --- | --- | --- |
| Schema | V25 aplicada e `expected-version: 25` | estado atual/BANCO terminam em V24/V21 | DOC-001 |
| Escopo TEAM | tabela `team_member`, check aceita TEAM, API e testes ativos | modelo organizacional chama TEAM de futuro | DOC-001 |
| Revogação | REST relê membership e nega na próxima chamada | Acessos fala em até 15 minutos para a sessão | FE-AUTHZ-001 |
| Permissão de Acessos | API exige `organization.manage` | rota não declara a permissão e só reage ao 403 | FE-AUTHZ-001 |
| Anonimização | mutação existe e respeita legal hold | audit registra como export/configuração | PRIV-001 |
| OpenAPI | 65 paths / 78 operações no snapshot | tipos gerados sem diff | coerente |

## 9. Testes, CI, dependências e operação

### 9.1 Execuções desta revisão

| Verificação | Resultado |
| --- | --- |
| `backend\\mvnw.cmd test` | 267 testes, 0 falhas, 0 erros, 0 ignorados; 3m04s |
| Flyway em Testcontainers | 25 migrations validadas e aplicadas do zero no PostgreSQL 17.10 |
| `npm run api:check` | aprovado, sem diff do contrato |
| `npm run lint` | aprovado com 3 warnings catalogados em QA-001 |
| `npm test` | 30 arquivos, 140 testes aprovados |
| `npm run build` | aprovado; bundle principal 369,82 kB (116,45 kB gzip) |
| Teste do auditor npm | 3/3 aprovados, incluindo fail-closed para erro/JSON vazio |
| `npm audit` com política | 0 critical; 2 high do mesmo advisory, ambos cobertos pela exceção vigente até 2026-11-30 |
| `docker compose config --quiet` | aprovado |
| Compose vivo | app/PostgreSQL/Redis saudáveis; Evolution e túnel locais em execução |
| API | `/actuator/health/readiness` = `UP` |
| Banco vivo | maior migration = `25`, success = `true` |
| Imagem atual | build aprovado; Trivy reprovou com 4 ocorrências HIGH / 3 CVEs |
| Worktree | limpa; `git diff --check` sem erro |

### 9.2 CI público

O último run público de `main`, em `b0b4c22`, terminou em falha:
<https://github.com/PNPeixoto/CRM/actions/runs/31420612891>. Falharam o teste backend e
o scan Trivy; os passos anteriores do job de segurança, incluindo gitleaks e auditoria
npm, não foram os passos marcados como falha. O baseline local está seis commits à
frente e não possui evidência de CI externo. Portanto, testes locais verdes não
autorizam chamar a `main` atual de promovível.

### 9.3 Acessibilidade e desempenho

Os testes automatizados axe, semântica, foco, contraste e navegação por componente
passam; o relatório de 2026-08-08 comprovou viewport 320×568 sem overflow e alvos de
44 px. Permanecem sem prova leitor de tela e zoom 200%. O build não apresentou alerta
de chunk excessivo, mas não há orçamento, Web Vitals em jornada real ou baseline F11.

## 10. Gates A–F, critério por critério

### Gate A — Fundação: **aprovado**

| Critério | Status | Evidência/bloqueio |
| --- | --- | --- |
| Ambientes documentados/reproduzíveis | comprovado | Compose config válido, app saudável e documentação de perfis |
| Build e testes base | comprovado | 267 backend + 140 frontend; contrato/lint/build verdes |
| Migrations em PostgreSQL real | comprovado | Testcontainers aplica V1–V25; banco vivo em V25 |
| Roles migration/runtime separadas | comprovado | migrations/testes de roles e runtime restrito |
| RLS sem SUPERUSER/BYPASSRLS | comprovado | `BancoSegurancaTest`/cenários de tenant verdes |
| Modelo organizacional e escopos | comprovado para runtime | OWN/TEAM/TENANT ativos; UNIT ainda não decide registro por ADR-0008 |

DOC-001 precisa ser corrigido, mas não invalida as provas executáveis do Gate A.

### Gate B — Segurança do núcleo: **reprovado**

| Critério | Status | Evidência/bloqueio |
| --- | --- | --- |
| Login, recuperação, MFA e sessão | comprovado | testes de autenticação, MFA, refresh/reuso e revogação |
| Ação, escopo e registro | parcial | provas negativas fortes; alteração concorrente falha em AUTHZ-001 |
| IDOR, mass assignment e troca de unidade | comprovado | `AutorizacaoPorAlcanceTest` e suítes correlatas |
| Matriz ASVS aplicável | parcial | fonte 5.0.0 vigente; matriz está desatualizada por DOC-001 |
| Threat models críticos | parcial | artefato existe, mas exportação/retention estão defasados |
| Secret/dependency scan | falhou | SUPPLY-001; último CI público vermelho |
| Frontend F4A | comprovado no escopo entregue | CSP, CSRF/XSS, storage e auditor npm testados |
| Frontend F4 | comprovado | single-flight, logout entre abas e guards testados |

### Gate C — Produto utilizável: **reprovado**

| Critério | Status | Evidência/bloqueio |
| --- | --- | --- |
| Contratos/limites do servidor | comprovado | OpenAPI e limites por teste |
| Shell WCAG 2.2 AA | parcial | axe/contraste/foco/mobile aprovados; leitor de tela e zoom 200% pendentes |
| Slices P0 ponta a ponta | parcial | integração backend/componentes; runner E2E ausente |
| Navegação não vira autorização | falhou em Acessos | FE-AUTHZ-001; servidor continua seguro |
| Jornadas E2E reproduzíveis | aberto por planejamento | F12 `ready` |
| F1/F2 sem casca paralela | comprovado | tokens, primitivas e shell únicos |
| F7 por domínio | aberto por planejamento | F7 é repetível e está `ready`; páginas entregues têm testes, mas não há registro formal para toda slice |

### Gate D — Omnichannel: **aprovado**

| Critério | Status | Evidência/bloqueio |
| --- | --- | --- |
| Webhook autentica/persiste antes do sucesso | comprovado | testes de canal e inbound |
| Idempotência inbound/outbound | comprovado | constraints e testes de duplicidade/retry |
| Contract tests/fixtures oficiais | comprovado | Telegram fixture oficial versionada; adapters testados |
| Mídia isolada/limitada/retida | comprovado com quarentena | download limitado e quarentena; promoção depende de scanner externo |
| Inbox cursor/reconciliação/reautorização | comprovado | testes de paginação, tópicos, reconciliação e F8 |
| F8 adaptativo | comprovado | reconciliação/deduplicação/fallback testados |
| Automação/conector HTTP | comprovado no backend | quotas, retry, SSRF e egress fail-closed testados; UI de automação ainda é placeholder honesto |

O uso de Evolution permanece risco experimental aceito; produção oficial de WhatsApp
requer provedor/canal aprovado.

### Gate E — Operação e conformidade: **reprovado**

| Critério | Status | Evidência/bloqueio |
| --- | --- | --- |
| Auditoria, retenção e direitos | falhou parcialmente | base forte, mas PRIV-001 quebra trilha de pedido/recusa |
| Entitlements/medição/billing/relatórios | aberto por planejamento | backend 19–21 `ready` |
| SLOs/alertas/runbooks | aberto por planejamento | backend 22 `ready` |
| Restore com RPO/RTO | aberto por planejamento | backend 23 `ready` |
| Rollback app/migration | não verificado | ensaio não existe nesta revisão |
| CI/CD rastreável por ambiente | aberto/falhou no CI atual | backend 24 `ready`; run público vermelho |
| F10–F13 | aberto por planejamento | todos `ready`; auditoria parcial de acessibilidade existe |

### Gate F — Integrações privadas e escala: **aberto por planejamento**

| Critério | Status | Evidência/bloqueio |
| --- | --- | --- |
| Agente privado/jobs assinados | aberto por planejamento | backend 25/25B/25C `ready` |
| Arquitetura sem violações | parcial | fronteira Java verde; ARCH-001 |
| Otimização por baseline | aberto por planejamento | backend 27/F11 `ready` |
| Carga representativa | aberto por planejamento | sem teste de carga/SLO |
| Escala horizontal | aberto por planejamento | simple broker e limites locais; backend 28 `ready` |

## 11. Veredito por ambiente

| Ambiente | Veredito | Condições, bloqueadores e risco residual |
| --- | --- | --- |
| Demonstração interna | **pronto com condições explícitas** | usar dados sintéticos, uma instância e um administrador; não anunciar billing, produção geral, WhatsApp oficial ou conformidade final; evitar exposição pública da imagem vulnerável |
| Piloto controlado com dados não sensíveis | **não pronto** | corrigir SUPPLY-001 e AUTHZ-001, obter CI verde no commit piloto e criar smoke/E2E das jornadas usadas |
| Piloto com clientes reais | **não pronto** | além do anterior, corrigir PRIV-001, decidir retenção, executar restore/RPO/RTO, F10/F12/F13 e usar integrações oficialmente aprovadas |
| Produção geral | **não pronto** | fechar Gates B, C e E; concluir SLOs, observabilidade, billing/entitlements, CI/CD, telemetria e evidências operacionais; Gate F quando houver requisito de escala/agente |

## 12. Roadmap ordenado por risco e dependência

| Ordem | Ação | Resolve | Mudança mínima | Responsável por papel | Evidência de saída | Risco de rollout |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | Atualizar base e `httpcore5`; revalidar imagem | SUPPLY-001 | versões corrigidas + digest revisado | plataforma/backend/segurança | Trivy 0 HIGH/CRITICAL, 267 testes, CI verde | compatibilidade transitiva e tamanho da imagem |
| 2 | Serializar invariantes administrativas | AUTHZ-001 | lock por tenant/par antes de check/write | backend | testes concorrentes reais + suíte completa | contenção em alterações administrativas, baixa frequência |
| 3 | Corrigir vocabulário/transação de privacidade | PRIV-001 | eventos próprios e transações que preservem pedido/recusa | backend + jurídico/produto | testes de falha/export e legal hold consultando audit_event | duplicidade de evento se idempotência não for definida |
| 4 | Corrigir rota/copy de Acessos e SEC-011 | FE-AUTHZ-001 | permissão na rota, testes e texto preciso; evento de revogação STOMP | frontend/backend | teste guard/menu + WebSocket revogado | desconexão de sessões administrativas ativas |
| 5 | Atualizar documentação canônica | DOC-001 | V25, TEAM, retenção/export, CI e contagens atuais | arquitetura/segurança | revisão cruzada + check automático de schema/path | baixo |
| 6 | Tornar dependências SQL entre módulos explícitas | ARCH-001 | portas/projeções e allowlist transitória | arquitetura/backend | gate estático + contratos por módulo | refactor transversal; preservar atomicidade LGPD |
| 7 | Fechar evidências de produto | Gate C | F7 aplicável, F10 manual, F11 e F12 | frontend/QA | E2E reproduzível, leitor de tela, zoom e Web Vitals | flakiness de E2E se ambiente não for determinístico |
| 8 | Executar operação/conformidade | Gate E | backend 19–24 e F13 | produto/plataforma/backend/frontend | restore medido, SLO, alertas, artefato promovido | mudanças de ambiente e migration exigem ensaio |
| 9 | Limpar warnings | QA-001 | ajustes localizados e Mockito agent | backend/frontend | lint/compilação sem warnings catalogados | baixo |

Não foram atribuídas datas: o repositório não fornece capacidade/histórico suficiente
para estimativas responsáveis.

## 13. Riscos aceitos, dívidas conhecidas e decisões pendentes

Estes itens não são reclassificados como defeitos novos quando o contrato atual os
declara de forma honesta:

- Evolution API é ponte experimental; WhatsApp oficial é requisito para produção.
- Mídia permanece em quarentena até existir scanner externo aprovado.
- `UNIT` está no vocabulário e no banco, mas não decide ownership de registros até a
  migration prevista no ADR-0008.
- Retenção está implementada, mas desabilitada/dias 0 até decisão jurídica concreta.
- Rate limiting HTTP em memória e STOMP simple broker limitam escala horizontal; Gate
  F/Prompt 28 deve removê-los quando houver baseline e demanda.
- Revogação de inscrição WebSocket já ativa permanece SEC-011; REST é imediato.
- Calendário, reservas, produtos/ativos, unidades, automações, campanhas e configurações
  aparecem como “em produção”, não como concluídos; não são promessa silenciosa.
- Capabilities e entitlements não são inventados pelo frontend; backend 19 ainda deve
  publicar o contrato comercial.
- A exceção npm GHSA-5p4m-2wfm-xmqj é limitada a ferramenta de desenvolvimento/CI,
  tem responsável e expira em 2026-11-30. Deve ser reavaliada antes do prazo.

Decisões pendentes relevantes:

1. política jurídica de retenção por categoria e tratamento dos eventos de
   anonimização;
2. provedor oficial de WhatsApp e scanner de mídia;
3. ambiente alvo, SLOs, RPO/RTO e processo de promoção/rollback;
4. momento em que escala horizontal e agente privado passam a ser requisito real.

## 14. Apêndice de evidências sanitizadas

### 14.1 Comandos e resultados

```text
backend\\mvnw.cmd test
Tests run: 267, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS — 03:04 min

npm run api:check
npm run lint
npm test
npm run build
Contrato aprovado; lint aprovado com 3 warnings; 140/140 testes; build aprovado

node --test .github/security/verificar-dependencias.test.mjs
3/3 aprovados

npm audit --json | node ../.github/security/verificar-dependencias.mjs
0 critical; 2 high cobertos por exceção vigente; 0 high/critical sem exceção

docker compose config --quiet
aprovado

GET http://localhost:8080/actuator/health/readiness
{"status":"UP"}

SELECT última versão Flyway no banco local
25:true

docker build --pull --tag crm-pnp-backend:review ./backend
aprovado

trivy 0.70.0 image --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed
4 ocorrências HIGH em 3 CVEs; 0 CRITICAL; exit code 1

git status --short / git diff --check
worktree limpa; sem erro de whitespace
```

### 14.2 Evidência externa

- CI público consultado por API do GitHub em 2026-08-13T02:00Z:
  <https://github.com/PNPeixoto/CRM/actions/runs/31420612891>.
- Trivy action usada pelo CI está fixada por SHA correspondente à versão 0.36.0;
  a revisão local usou Trivy 0.70.0, versão declarada pela action atual:
  <https://github.com/aquasecurity/trivy-action/releases/tag/v0.36.0>.
- Fonte normativa ASVS: <https://owasp.org/www-project-application-security-verification-standard/>.
- Fonte normativa NIST: <https://csrc.nist.gov/pubs/sp/800/63/4/final>.
- Fonte normativa WCAG: <https://www.w3.org/TR/WCAG22/>.

### 14.3 Proteção de dados

Saídas foram sanitizadas. Este relatório não contém segredos, credenciais, tokens,
cookies, identificadores de cliente, payloads de mensagens ou dados pessoais.
