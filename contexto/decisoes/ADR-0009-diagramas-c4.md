# ADR-0009 — Visões C4 em Mermaid para a arquitetura executável

- Status: accepted
- Data: 2026-08-05

## Contexto

O repositório descreve arquitetura, segurança e operação em texto, mas não
possuía uma entrada visual que mostrasse o sistema, seus containers e os
componentes críticos. A ausência é verificável pela própria árvore de
`contexto/`; já as métricas de tempo e os relatos atribuídos a pessoas no
material que propôs este ADR não possuem evidência e não fundamentam a decisão.

Um diagrama desatualizado pode induzir uma mudança mais perigosa que a leitura
direta do código. A proposta original ilustrava RabbitMQ, Grafana e um servidor
de estáticos como se fossem containers existentes. No estado atual, o broker
STOMP é interno ao processo, observabilidade externa ainda pertence ao Prompt
22 e o deploy da SPA não está definido neste repositório.

## Decisão

Adotamos as abstrações do modelo C4 para manter quatro visões pequenas:

1. contexto do sistema;
2. containers executáveis e dependências externas;
3. componentes do núcleo de identidade;
4. sequência de login e cadastro de MFA.

Os diagramas ficam em Markdown sob `contexto/diagramas/` e usam os blocos
estáveis `flowchart` e `sequenceDiagram` do Mermaid. Não dependemos da sintaxe
experimental `C4Context`/`C4Container`; o nível C4 é a semântica da visão, não
uma extensão específica do renderizador.

Cada diagrama declara a data de verificação e aponta para fontes executáveis.
Ele não antecipa prompts futuros como se estivessem implementados. Elemento
planejado pode aparecer somente com estilo e legenda explícitos de “planejado”.

## Regras de atualização

Atualize a visão afetada no mesmo change set quando ocorrer:

- criação ou remoção de módulo implementado;
- nova dependência de runtime, provedor ou datastore;
- mudança no transporte REST/WebSocket ou no broker;
- mudança estrutural em autenticação, autorização ou isolamento de tenant;
- definição do servidor/proxy que publicará a SPA.

Uma alteração CRUD interna, sem nova relação arquitetural, não exige mudar os
diagramas. O revisor compara a visão com `docker-compose.yml`, `pom.xml`,
`package.json`, configurações Spring e fronteiras observadas no código. Um gate
automático de sintaxe só será adicionado se houver ferramenta executável no CI;
`markdownlint` sozinho não valida semântica Mermaid.

## Alternativas descartadas

- **Copiar os exemplos da proposta:** descreviam componentes inexistentes e
  relações inválidas; a documentação nasceria desatualizada.
- **Arquivos gráficos editáveis apenas por ferramenta externa:** o diff não
  permite revisar facilmente a mudança sem o editor proprietário.
- **Gerar todas as visões no build:** adicionaria cadeia de ferramentas e
  manutenção antes de existir necessidade operacional comprovada.
- **Não criar diagramas:** preservaria a precisão textual, mas manteria alto o
  custo de formar a visão de conjunto a partir de vários arquivos.

## Consequências e gatilho de revisão

Os diagramas passam a ser mapas de navegação, não substitutos dos ADRs nem do
código. A atualização é manual e revisável. Quando o Prompt 28 trocar o broker
em memória por infraestrutura compartilhada, ou quando o Prompt 22 definir a
plataforma de observabilidade, esta decisão e a visão de containers devem ser
revisadas no mesmo trabalho.

## Evidências

- `contexto/diagramas/README.md` e as quatro visões vinculadas;
- `docker-compose.yml`;
- `backend/src/main/java/br/com/pnp/crm/CrmApplication.java`;
- `backend/src/main/java/br/com/pnp/crm/shared/internal/WebSocketConfig.java`;
- `backend/src/test/java/br/com/pnp/crm/FronteiraDeModulosTest.java`;
- `frontend/package.json` e `frontend/vite.config.ts`.

