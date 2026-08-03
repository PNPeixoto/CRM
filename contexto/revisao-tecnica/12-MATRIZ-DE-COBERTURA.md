# Matriz de cobertura da revisão

Esta matriz evita que a revisão se limite a estilo de código ou a uma única
camada. “Coberto” exige evidência; apenas ler o arquivo não basta.

| Risco ou dimensão | Prompt principal | Evidência mínima esperada |
| --- | --- | --- |
| Jornadas P0 e invariantes | 02 | cenários, código da regra e testes positivos/negativos |
| Contato, funil, oportunidade e tarefa | 02 | validações de tenant, transições e persistência |
| Conversa, canal e roteamento | 02 e 06 | deduplicação, ordem transacional e contratos |
| Onboarding, segmento e navegação | 02 e 07 | idempotência e separação entre preset e permissão |
| Fronteiras do monólito modular | 03 | dependências, APIs públicas e testes Modulith |
| Transações, eventos e concorrência | 03 | limites transacionais, locks/idempotência e falhas |
| Login, sessão, reset e MFA | 04 | testes de abuso, expiração, rotação e revogação |
| Autorização e IDOR | 04 | ação + escopo + registro e tentativas cross-tenant |
| RLS e papéis PostgreSQL | 05 | sessão runtime restrita e testes positivos/negativos |
| Migrations e upgrade | 05 | banco vazio, caminho de atualização e checksum |
| Contrato HTTP/OpenAPI | 06 | snapshot, geração sem diff e respostas de erro |
| Webhooks e provedores | 06 | autenticação, persist-before-ack, retry e deduplicação |
| WebSocket | 04 e 06 | autenticação CONNECT/SUBSCRIBE, origem e reconciliação |
| Frontend e contrato gerado | 07 | fronteira de adapters, estados e integração real |
| UX e WCAG | 07 | teclado, foco, semântica, contraste e responsividade |
| Testes e CI | 08 | execução reproduzível e falhas não mascaradas |
| Dependências e segredos | 08 | scans, lockfiles e tratamento formal dos achados |
| Containers e ambientes | 09 | imagem, usuário, secrets, health e configuração prod |
| Backup, rollback e observabilidade | 09 | exercício mensurado, runbook, SLO e alertas |
| LGPD, auditoria e retenção | 09 | inventário, finalidade, acesso, prazo e descarte |
| Gates e prontidão | 10 | ligação de cada decisão à evidência reproduzível |

## Cobertura transversal obrigatória

Em todos os prompts, verificar também:

- isolamento entre tenants;
- dados sensíveis e segredos;
- concorrência, retry e idempotência;
- estados de erro e recuperação;
- compatibilidade entre documentação, contrato, código e testes;
- impacto em deploy, rollback e dados existentes;
- distinção entre implementado, parcialmente implementado e planejado.

