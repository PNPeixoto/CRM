# Prompt — testes, CI e dependências

## Início do prompt

Revise a capacidade do CRM PNP de detectar regressões e produzir artefatos
confiáveis. Trabalhe em modo somente leitura e não atualize dependências. Leia
`00-CONTEXTO-CANONICO.md`, `backend/TESTES.md`, manifests, configurações de build,
workflow de CI, suites e lockfiles. Use o modelo oficial de achado.

### Baseline reproduzível

- Registre commit/working tree, SO, Java, Maven, Node, npm, Docker e serviços.
- Execute os gates disponíveis: backend completo, OpenAPI check, lint, frontend
  tests, frontend build e `git diff --check`.
- Diferencie falha do produto, falha do teste e limitação de ambiente.
- Contagem histórica de 82/56 testes não comprova o baseline atual.

### Estratégia de testes

Mapeie risco → nível → suite → gate para:

- regra de domínio sem HTTP;
- controller/contrato e validação;
- módulos Spring Modulith;
- PostgreSQL/Flyway/RLS com banco real e papel runtime;
- Redis e concorrência quando relevantes;
- autenticação, MFA, reset, sessão, IDOR e mass assignment;
- webhooks, idempotência, adapters e falha de provedor;
- frontend por componente, integração e jornada;
- OpenAPI gerado e compatibilidade frontend;
- container, health/readiness e caminho de upgrade.

Procure testes que passam apenas com H2, mocks excessivos, asserts fracos,
fixtures compartilhadas entre tenants, ordem implícita, relógio/rede reais,
sleep, aleatoriedade não registrada ou exceção capturada sem falha.

### Casos negativos e concorrência

- Todo controle cross-tenant precisa de caso positivo e negativo.
- IDOR deve variar identificador principal e relacionamentos recebidos.
- Retry/idempotência deve executar ao menos duas vezes e simular falha após
  persistência/antes do ACK.
- Sessões devem cobrir refresh simultâneo, reuse e expirações.
- Onboarding, funil padrão, inbound e movimentação devem ter teste concorrente
  proporcional ao risco.
- Não considere cobertura de linha substituto de cobertura de invariantes.

### Governança de falhas

- Teste falho não é ignorado silenciosamente.
- Quarentena exige issue, responsável, justificativa, expiração, execução
  contínua e exclusão explícita do gate.
- Segurança, tenant, migration e cobrança não podem entrar em quarentena.
- Procure `skip`, `disabled`, `todo`, retries globais, permissões de continuação e
  scripts que devolvem sucesso apesar de falha.

### CI/CD e artefatos

- Triggers, permissões mínimas, pinning de actions/plugins e cache seguro.
- Ordem: compile/teste, banco real, contrato, frontend, scans e artefato.
- Snapshot OpenAPI e tipos gerados falham se houver diff.
- Mesmo artefato deve ser promovido por ambiente; versão e origem rastreáveis.
- Logs/artefatos não contêm segredos ou dados pessoais.
- Jobs obrigatórios não podem ser facilmente omitidos por mudança de path.

### Supply chain

- Lockfiles presentes e coerentes; versões de plugins e imagens controladas.
- Valide `npm audit` e scanners existentes, alcance em runtime/dev e mitigação.
- Verifique secret scan, dependency scan, SBOM e image scan conforme gates; se
  ainda planejados, registre como lacuna do gate e não como implementação.
- Achado aceito precisa de responsável, justificativa, prazo/condição de revisão
  e controle compensatório.

### Saída

Entregue:

1. tabela de comandos com resultado e duração;
2. matriz risco → teste → ambiente → gate;
3. lista de testes ausentes por prioridade, com nome/cenário sugerido;
4. achados no formato oficial;
5. análise dos achados de dependência sem atualização automática;
6. veredito da força das evidências dos Gates A–F.

Não classifique ausência de teste como defeito funcional confirmado; classifique
como evidência ausente, salvo quando um teste novo reproduzir a falha.

## Fim do prompt

