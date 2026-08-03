# Backlog de segurança

Achados do Prompt 07, priorizados. Cada item aceito tem responsável, prazo e
fundamento — exceção informal não é aceite.

Baseline: commit `a603534`, 2026-08-03. Responsável único do projeto:
**PNPeixoto**.

**Critério de bloqueio:** risco crítico bloqueia o Gate B. Nenhum crítico
permaneceu aberto. Os dois altos foram tratados no Prompt 07, e o médio
`SEC-014` foi encontrado e corrigido no F4.

| Severidade | Abertos | Resolvidos nesta execução | Aceitos com prazo |
|---|---|---|---|
| Crítico | 0 | 0 | 0 |
| Alto | 0 | 1 | 1 |
| Médio | 7 | 1 | 0 |
| Baixo | 5 | 0 | 2 |

---

## Resolvidos

### `SEC-004` — `jackson-databind` com falha de autorização — **RESOLVIDO**

- **Severidade:** alta · CVSS 6.5 · integridade alta
- **Identificador:** `CVE-2026-59889`, mais `CVE-2026-54515` e
  `GHSA-mhm7-754m-9p8w` na linha 2
- **Por que importa aqui:** o Jackson desserializa **todo** corpo de
  requisição. Uma falha nesse ponto está no caminho de entrada de 100% da API,
  antes de qualquer verificação de autorização da aplicação.
- **Correção aplicada:** `jackson-bom.version` 3.1.4 → **3.1.5** e
  `jackson-2-bom.version` 2.21.4 → **2.21.5**, ambas de patch, seguindo o
  precedente já existente no `pom.xml` para a CVE do driver PostgreSQL.
- **Evidência:** `mvnw dependency:tree` confirma 3.1.5 e 2.21.5 resolvidos;
  suíte com 112 testes verde após o bump.

### `SEC-014` — Redirecionamento aberto no retorno pós-login — **RESOLVIDO**

- **Severidade:** média · **Encontrado e corrigido no F4, 2026-08-03**
- **Situação:** o destino de retorno vinha de `location.state` sem validação.
  O estado do histórico é gravável por qualquer código da página, e um valor
  como `//host.externo` é protocolo-relativo: o navegador o resolve como host
  externo. O redirecionamento partiria de uma tela de login legítima, que é
  justamente o que empresta credibilidade a um phishing.
- **Correção:** `destinoInternoSeguro` valida por allowlist de forma — barra
  única inicial, sem caractere de controle, e confirmação de origem depois da
  normalização do parser. `/login` é recusado como destino, para não criar
  laço.
- **Evidência:** 12 casos em `destinoSeguro.test.ts`, incluindo uma
  propriedade que exige que **toda** entrada resulte em destino da mesma
  origem.

---

## Aceitos com prazo

### `SEC-A01` — `GHSA-qwww-vcr4-c8h2` em `react-router`

- **Severidade reportada:** alta · **Severidade real aqui: nenhuma**
- **Responsável:** PNPeixoto · **Prazo:** 2026-11-30
- **Fundamento:** o aviso oficial declara que a falha só afeta quem usa as APIs
  RSC instáveis. Este frontend usa exclusivamente `BrowserRouter`. A correção
  upstream é a 8.3.0 — uma major: aplicá-la é migração de roteador, não
  atualização de segurança. Trocar um risco que não nos alcança por uma
  migração ampla piora o risco real.
- **Mecânica:** registrado em `.github/security/excecoes-de-dependencia.json`.
  O CI **reprova** quando o prazo vence, de modo que o aceite não apodrece em
  silêncio.
- **Gatilho de reavaliação imediata:** o dia em que o projeto adotar RSC.
- **Reavaliado no F4A, 2026-08-03:** mantido. O F4A varreu o código e confirmou
  que nenhum arquivo importa API de RSC ou de framework mode; o roteador segue
  em modo declarativo com `BrowserRouter`. O prazo não foi estendido.

### `SEC-007` — CSP com `style-src 'unsafe-inline'`

- **Severidade:** baixa
- **Responsável:** PNPeixoto · **Prazo:** reavaliado no F4A em 2026-08-03,
  **mantido**; próxima reavaliação no F10
- **Fundamento:** React e Tailwind injetam estilo inline; remover exigiria
  nonce por requisição ou hash por estilo, com custo desproporcional ao ganho.
  `script-src` permanece **sem** `unsafe-inline` e sem `unsafe-eval`, que é
  onde a execução de código realmente acontece.
- **Verificado no F4A:** o navegador reportou `script-src-elem` em modo
  `enforce` ao bloquear script inline injetado na página. Ou seja, a folga de
  `style-src` não contamina `script-src` — o que era a preocupação real.

### `SEC-008` — Pepper não rotacionável

- **Severidade:** baixa, com impacto alto se materializar
- **Responsável:** PNPeixoto · **Prazo:** antes do primeiro cliente pagante
- **Fundamento:** o pepper participa do hash de toda senha; trocá-lo invalida
  todos os hashes de uma vez. Rotacioná-lo exige rehash no próximo login bem
  sucedido, com convivência de duas gerações — desenho que ainda não existe.
  Aceito hoje porque a base de usuários é de teste; **deixa de ser aceitável**
  no momento em que houver senha real armazenada.

---


## Abertos — médio

### `SEC-001` — Contrato OpenAPI público em produção

- **Capítulo:** V4 · **Severidade:** média
- **Situação:** `/v3/api-docs/**` está em `ENDPOINTS_PUBLICOS`, em todos os
  profiles.
- **Abuso:** qualquer pessoa obtém o mapa completo de 30 rotas, DTOs e campos,
  incluindo os que exigem permissão. Não é vulnerabilidade por si; é o
  reconhecimento que antecede uma.
- **Correção mínima:** expor o contrato apenas fora de produção, ou exigir
  autenticação. O snapshot versionado continua alimentando o frontend, então
  fechar em produção não quebra a geração de tipos.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 08

### `SEC-003` — Sem limite de taxa e de tamanho fora do login

- **Capítulo:** V2, V4 · **Severidade:** média
- **Situação:** o bloqueio progressivo cobre login. Nenhum outro endpoint tem
  limite de taxa, e não há teto explícito de tamanho de corpo — inclusive no
  webhook, que é a única rota exposta à internet sem sessão.
- **Abuso:** exaustão de recurso por repetição ou por corpo grande; enumeração
  por volume em endpoints de leitura.
- **Correção mínima:** teto de tamanho de corpo no servidor e limite por
  origem nas rotas públicas e no webhook.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 08

### `SEC-006` — Nada detecta deriva entre schema esperado e aplicado

- **Capítulo:** V13 · **Severidade:** média
- **Situação:** foi exatamente o ENV-001: a pilha rodou 37 horas com imagem de
  V8 contra código em V11, e nada avisou.
- **Abuso:** não é ataque; é o ambiente mentindo. Um teste manual que passa
  contra build velha produz confiança falsa, que é pior que falha visível.
- **Correção mínima:** verificação de readiness comparando a versão de schema
  esperada pela aplicação com a aplicada no banco, falhando quando divergirem.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 22, ou Prompt 08 se antes
- **Observação:** hoje a defesa é operacional — subir com `--build` ou com tag
  do commit. Defesa operacional é a que falha primeiro.

### `SEC-011` — Inscrição WebSocket já ativa sobrevive à revogação

- **Capítulo:** V7, V8 · **Severidade:** média
- **Situação:** a permissão é revalidada a cada SUBSCRIBE, o que fecha a janela
  de **novas** inscrições. Uma inscrição já ativa continua recebendo até o
  cliente reconectar, e a conexão dura horas.
- **Abuso:** usuário desligado no meio do expediente continua recebendo
  mensagem de cliente em tempo real enquanto não fechar a aba.
- **Correção mínima:** revalidação periódica das inscrições ativas, ou
  encerramento das sessões WebSocket do usuário quando o membership muda.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 14, que trata a caixa em
  tempo real
- **Nota:** o caminho HTTP **não** tem esse problema — ali a negação vale na
  requisição seguinte.

### `SEC-012` — Falha de infraestrutura se apresenta como falha de teste

- **Capítulo:** V13 · **Severidade:** média para a operação, nula para o produto
- **Situação:** com o Docker parado, a suíte reporta dezenas de erros de teste
  que são uma única causa repetida. Aconteceu **duas vezes** nesta sessão: 47
  erros em uma, 67 na outra, sempre um só motivo.
- **Abuso:** não é ataque; é ruído que atrasa diagnóstico no pior momento e
  ensina a equipe a ignorar suíte vermelha.
- **Correção mínima:** verificação prévia do runtime de contêiner que falhe com
  uma mensagem única e clara antes de a suíte começar.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 08
- **Origem:** já era `TEST-001` da revisão integrada; segue aberto.

---

## Abertos — baixo

### `SEC-002` — JWT HS256 com chave simétrica

- **Capítulo:** V9 · **Severidade:** baixa hoje
- **Situação:** a mesma chave assina e verifica. Com um único serviço isso é
  aceitável e simples.
- **Abuso futuro:** no dia em que um segundo serviço precisar **verificar**
  token, ele receberá também a capacidade de **assiná-lo** — e um serviço
  periférico comprometido passa a emitir sessão de qualquer usuário.
- **Correção:** migrar para assinatura assimétrica (RS256/ES256) **antes** do
  segundo verificador, não depois.
- **Responsável:** PNPeixoto · **Prazo:** gatilho, não data — o primeiro
  serviço adicional que precise verificar token

### `SEC-009` — Log pode carregar corpo de resposta do provedor

- **Capítulo:** V14, V16 · **Severidade:** baixa
- **Situação:** os 17 pontos de log foram auditados e registram identificador,
  nunca conteúdo. A exceção são pontos que passam o objeto de exceção do
  provedor ao logger, e essa exceção pode carregar corpo de resposta.
- **Correção mínima:** registrar tipo e código do erro do provedor, não a
  exceção inteira.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 17, junto com auditoria

### `SEC-010` — Capítulo V5 inteiro em aberto quando mídia chegar

- **Capítulo:** V5 · **Severidade:** baixa hoje, alta no dia da entrega
- **Situação:** nenhum endpoint recebe ou serve arquivo, então V5 não se aplica
  — hoje.
- **Correção:** tratar V5 por completo no Prompt 13, com tipo declarado versus
  conteúdo real, tamanho, nome, caminho fora da raiz web, varredura e URL
  assinada com expiração.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 13

### `SEC-013` — Ações de CI referenciadas por tag móvel

- **Capítulo:** V13 · **Severidade:** baixa
- **Situação:** o workflow usa `@v4` e `@v2`, que são tags móveis. Quem
  controla a tag controla o que roda no pipeline.
- **Correção mínima:** fixar por SHA, com Dependabot mantendo a atualização.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 24, que trata CI/CD

---

## Herdados, fora do escopo deste prompt

| Id | Descrição | Bloqueia | Prompt |
|---|---|---|---|
| `AUDIT-001` | Trilha de auditoria não existe; é P0 do produto | Gate E | 17 |
| `AUTZ-002` (resíduo) | Frontend não consome `/organizacao/contextos` nem `/organizacao/permissoes`; seletor de unidade alimentado por lista fabricada | Gate C | trilha frontend |
| ADR-0008 | Escopo por unidade depende de `unit_id` nas tabelas de domínio | — | migration futura |

---

## Acrescentados pela revisão do Gate B, 2026-08-03

### `SEC-015` — Mass assignment garantido por configuração verificada em um só profile

- **Capítulo:** V2 · **Severidade:** baixa
- **Situação:** a proteção é `fail-on-unknown-properties: true`, global, mais
  um DTO por caso de uso. Existe **um** teste que a exercita, sob o profile
  `test`. Desligar a propriedade em outro profile não seria detectado.
- **Correção mínima:** teste que leia a propriedade efetiva do profile de
  produção, ou um caso de rejeição em endpoint de domínio além do atual.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 08

### `SEC-016` — O gate de segurança do CI nunca executou

- **Capítulo:** V13 · **Severidade:** média para a operação
- **Situação:** o job `seguranca` foi verificado passo a passo na máquina, e o
  workflow nunca rodou no GitHub Actions — os commits são locais.
- **Risco concreto:** `gitleaks/gitleaks-action@v2` exige licença em
  repositório de organização. Este é pessoal, onde é gratuito, mas isso é
  inferência e não observação.
- **Correção mínima:** enviar os commits e conferir a execução.
- **Responsável:** PNPeixoto · **Prazo:** antes do próximo prompt

### `SEC-017` — Dependências do backend não são varridas pelo CI

- **Capítulo:** V13 · **Severidade:** média
- **Descoberto em:** 2026-08-03, ao conferir o CI depois do push.
- **Situação:** o job `seguranca` cobre o frontend por `npm audit` mais o
  verificador de exceções. A cobertura do backend dependia do
  `dependency-review-action`, que **exige GitHub Advanced Security em
  repositório privado** — e este repositório é privado. O passo ficou
  condicionado a repositório público para não falhar em toda execução; um
  passo que sempre falha é desligado por alguém em duas semanas, junto com o
  resto do job.
- **Consequência concreta:** a CVE-2026-59889 do `jackson-databind`, corrigida
  no Prompt 07, foi encontrada por `docker scout` **local**. Nada no CI a teria
  encontrado. A próxima passa despercebida.
- **Controles compensatórios hoje:** alertas do Dependabot, gratuitos em
  repositório privado, mas que **precisam estar habilitados nas configurações
  do repositório** — é ajuste de interface, não de código, e não dá para
  verificar daqui; e varredura local de imagem, que depende de alguém lembrar.
- **Correção mínima:** varredura de dependência Maven que funcione em
  repositório privado, executada no CI. Alternativas: `docker scout cves` com
  credencial no pipeline, OWASP dependency-check com chave da NVD, ou tornar o
  repositório público quando isso for aceitável.
- **Responsável:** PNPeixoto · **Prazo:** Prompt 08
