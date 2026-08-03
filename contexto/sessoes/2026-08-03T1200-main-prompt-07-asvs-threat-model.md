# Sessão 2026-08-03 — Prompt 07, baseline ASVS e threat models

- Branch: `main`
- Commit base: `a603534`
- Ambiente: Windows 11, JDK Temurin 25.0.4, Docker Desktop 29.6.2,
  Testcontainers com PostgreSQL 17 e Redis 7
- Responsável: PNPeixoto, com Claude Code

## Antes do prompt

Duas pendências operacionais foram fechadas nesta sessão, na ordem pedida:

1. **Versionamento (GOV-001).** O acervo saiu do working tree para a `main` em
   cinco commits por área, sobre `793777d`, com as duas suítes verdes. 476
   arquivos rastreados, nenhum segredo entre eles.
2. **Ambiente (ENV-001).** A pilha rodava imagem de `793777d-p01-r3` com banco
   em V8+V900 enquanto o código estava em V11. Reconstruída como
   `0.0.1-a603534`; Flyway alcançou V11. A prova de correspondência é o
   contrato: o `/v3/api-docs` do contêiner é idêntico ao snapshot versionado,
   exceto `servers[0].url`, que reflete o host da requisição.

## Padrão consultado

ASVS **5.0.0**, publicado em **30/05/2025**, lido da fonte oficial da OWASP em
2026-08-03. Estrutura confirmada no CSV oficial: 16 capítulos, identificador
`<capítulo>.<seção>.<requisito>`, nível na coluna `L`. Alvo nível 2; nível 3
aplicado apenas onde declarado na matriz.

Nada veio de documentação memorizada, conforme o protocolo do prompt.

## Entregáveis

`contexto/seguranca/` com quatro documentos: índice, matriz dos 16 capítulos,
threat models por fluxo e backlog priorizado. Cada linha da matriz distingue
**verificado por execução** de **verificado por inspeção** — sem essa
distinção, um documento de segurança soa igualmente confiante sobre o que foi
testado e sobre o que foi apenas lido.

Fluxos que o produto não tem — mídia, automação, conector, exportação, job,
billing, agente privado — aparecem **declarados como inexistentes**, com a
fronteira que precisará ser tratada e o prompt responsável. Modelar componente
imaginário produz cobertura de mentira.

## Achado corrigido durante a execução

`jackson-databind` 3.1.4 e 2.21.4 são afetados por **CVE-2026-59889**
(*Incorrect Authorization*, CVSS 6.5, integridade alta); a linha 2 acumula
ainda CVE-2026-54515 e GHSA-mhm7-754m-9p8w. O Jackson desserializa todo corpo
de requisição, então isso está no caminho de entrada de 100% da API, antes de
qualquer verificação da aplicação.

Corrigido por `jackson-bom.version` → 3.1.5 e `jackson-2-bom.version` → 2.21.5,
ambas de patch, seguindo o precedente que o `pom.xml` já tinha para a CVE do
driver PostgreSQL. Suíte verde depois do bump.

## Achado analisado e recusado como aplicável

`npm audit` reporta duas vulnerabilidades altas em `react-router`. O aviso
oficial (`GHSA-qwww-vcr4-c8h2`) declara que a falha **só afeta quem usa as APIs
RSC instáveis**; este frontend usa exclusivamente `BrowserRouter`. A correção
upstream é a 8.3.0, uma major — aplicá-la seria migração de roteador, não
atualização de segurança.

Registrado como exceção nomeada com responsável, prazo e fundamento, não como
silenciamento.

## Varredura de segredo e dependência integrada ao gate

O critério exigia que a varredura **integre o gate**, não que exista à parte.

- Job `seguranca` novo no CI, separado dos testes para que um achado leia como
  "reprovou segurança" em vez de sumir no log da suíte.
- gitleaks sobre o histórico completo (`fetch-depth: 0`): segredo removido no
  commit seguinte continua no histórico e continua vazado.
- Verificador próprio de dependências, sem dependência nova — um verificador de
  supply chain que arrasta pacotes amplia a superfície que deveria vigiar.
- `.gitleaks.toml` com exceção casando **valor literal e caminho**, não o
  diretório de testes inteiro: arquivo de teste é justamente onde credencial
  real costuma ser colada e esquecida.
- Dependabot para maven, npm, github-actions e docker. Sem fonte de
  atualização, a única saída restante seria escrever exceções, e o gate viraria
  um gerador de desculpas.

O verificador reprova também quando uma **exceção vence** — exceção sem prazo
vira permissão permanente e ninguém percebe.

## Evidências

| verificação | comando | resultado |
|---|---|---|
| suíte backend após o bump | `mvnw test` | 112 testes, 0 falhas, 0 erros |
| suíte frontend | `npm test -- --run` | 56 testes em 14 arquivos |
| segredo no histórico | gitleaks, 25 commits, 1,90 MB | nenhum vazamento com a configuração |
| dependências do frontend | `npm audit` + verificador | 2 altas, ambas cobertas por exceção vigente |
| imagem | `docker scout` | 0 críticas, 0 altas, 9 médias, 12 sem severidade |
| contrato servido | diff contra snapshot | idêntico exceto `servers[0].url` |

Os dois caminhos negativos do gate foram exercitados de fato: exceção vencida
reprova, e vulnerabilidade sem exceção reprova. Um segredo falso injetado no
arquivo de teste permitido continuou sendo detectado, o que prova que a
allowlist é por valor e não por caminho.

## Veredito do Gate B

**Backend aprovado.** Nenhum crítico aberto; os dois altos tratados. Cinco
médios e quatro baixos no backlog, todos com responsável e prazo.

O gate **permanece aberto no conjunto** até as provas de frontend F4A e F4.
Autorização, sessão e supply chain de backend estão cobertas; segurança de
navegador é a metade que falta.

## Limites desta execução

Não foram executados teste de intrusão, fuzzing, análise dinâmica, auditoria de
acessibilidade nem teste de carga. A matriz marca cada linha como execução ou
inspeção; inspeção é afirmação sobre o código lido, não sobre comportamento
observado.

O Docker Desktop caiu **duas vezes** durante a sessão, produzindo 47 e depois
67 erros de teste que eram uma única causa repetida. Está no backlog como
`SEC-012`, herdando o `TEST-001` da revisão integrada.
