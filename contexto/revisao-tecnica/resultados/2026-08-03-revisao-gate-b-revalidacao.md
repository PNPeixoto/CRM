# Revalidação do Gate B — login administrativo e evidências

> Execução: 2026-08-03, 13:55–14:10 UTC. Baseline `4901499` mais working tree,
> branch `main`. Ambiente: Windows 11, JDK 25.0.4, Node 24.18.0, Docker Desktop
> 29.6.2, PostgreSQL 17 e Redis 7.

## Veredito

**Gate B — APROVADO APÓS CORREÇÕES.**

A revisão anterior havia aprovado o gate, mas uma tentativa real de login
administrativo revelou que a interface não conseguia cumprir a política de MFA
que o backend já exigia. A revalidação partiu do banco em execução, reproduziu
a causa e encontrou outras três divergências de evidência. Todas as que afetam
o gate foram corrigidas antes deste veredito.

Esta continua sendo uma autorrevisão. Uma avaliação independente antes do
piloto externo permanece recomendada.

## Diagnóstico das contas antigas

O banco não perdeu nem redefiniu as contas. Consultas somente leitura provaram:

- `pnp/peixoto`, `acme/peixoto` e `pnp/atendente` continuam ativos;
- os dois hashes de `peixoto` são exatamente os hashes originais do seed;
- os memberships `OWNER` continuam vigentes com alcance `TENANT`;
- nenhuma conta tinha autenticador cadastrado;
- Flyway contém V1–V11, V900 e os dois repeatables de desenvolvimento.

O bloqueio era de produto: `OWNER` exige MFA, o backend respondia
`MFA_CADASTRO_NECESSARIO` depois de validar a senha, e a interface não tinha o
cadastro. O usuário interpretava corretamente isso como “o login não funciona”.

## Achados desta revalidação

### 1. MFA administrativo sem jornada de interface — corrigido (`SEC-018`)

A tela agora inicia o enrollment após a prova da senha, mantém o segredo TOTP
somente em memória, ativa com o código de seis dígitos, estabelece a sessão e
mostra os dez recovery codes uma única vez. Logins posteriores aceitam TOTP ou
código de recuperação. Nenhum token, segredo ou código vai para storage, URL ou
log do navegador.

### 2. Auditoria npm falhava aberta quando o scanner falhava — corrigido (`SEC-019`)

Com o registry indisponível, `npm audit --json` devolveu um JSON de erro. O
verificador antigo leu a ausência de `vulnerabilities` como conjunto vazio e
imprimiu aprovação. Agora ele exige um relatório completo e falha fechado em
erro de transporte, JSON vazio ou formato incompleto. Três testes executam no
CI antes da auditoria real.

### 3. Contrato TypeScript derivado estava desatualizado — corrigido

`npm run api:check` reprovou porque `/api/organizacao/permissoes`, criado no
Prompt 06, existia no snapshot OpenAPI e não no tipo gerado. O arquivo foi
regenerado e a checagem agora passa sem diff.

### 4. Backend local voltou a divergir do repositório — mitigado, risco permanece

O serviço em `localhost:8080` ainda usava `crm-pnp-backend:0.0.1-a603534`.
Somente a aplicação foi reconstruída como `0.0.1-dev`; o volume PostgreSQL foi
preservado. Readiness está `UP`. A recorrência confirma `SEC-006`: enquanto a
aplicação não comparar schema/build esperado com o ambiente, reconstruir a
imagem continua sendo defesa operacional.

## Evidências reexecutadas

| Evidência | Resultado |
|---|---|
| `./mvnw -o test` | 112 testes, 0 falhas, PostgreSQL/Redis reais |
| `npm test` | 93 testes em 20 arquivos, 0 falhas |
| `npm run lint` | 0 erros; 3 avisos conhecidos de Fast Refresh |
| `npm run build` | sucesso; sem source map publicado |
| `npm run api:check` | sucesso após regeneração determinística |
| teste do verificador de dependências | 3/3 casos verdes, incluindo falha do scanner |
| `npm audit` + política | 0 críticas; 2 altas sob `SEC-A01`, vigente até 2026-11-30 |
| gitleaks no histórico | 30 commits, nenhum vazamento |
| gitleaks na árvore | somente 6 achados em logs XML/TXT ignorados de testes em `backend/target`; nenhum em fonte |
| banco local | contas ativas, hashes originais, 14 entradas Flyway |
| ambiente local | frontend com fluxo MFA atual; backend reconstruído e saudável |

O Docker Scout não foi reexecutado porque o ambiente recusou enviar metadados
ou camadas da imagem a um serviço externo sem autorização específica. A
evidência anterior de 0 altas/0 críticas permanece aplicável: nenhuma
dependência ou imagem-base do backend mudou desde aquela varredura.

## Critérios do Gate B

1. Login, recuperação, MFA e sessões: **atende**, agora também pela jornada da SPA.
2. Autorização por ação, escopo e registro: **atende**, 112 testes backend.
3. IDOR, mass assignment e troca de unidade: **atende com `SEC-015` aberto**.
4. Matriz ASVS 5.0.0 nível 2: **atende**.
5. Threat models dos fluxos críticos: **atende**.
6. Secret/dependency scan: **atende após corrigir `SEC-019`**; `SEC-017`
   continua como dívida da varredura Maven no CI privado.
7. F4A — CSP, CSRF/XSS, persistência e supply chain: **atende**.
8. F4 — refresh single-flight, logout entre abas e guards: **atende**.

## Riscos que não bloqueiam este veredito

- `SEC-017`: dependências Maven ainda dependem de Dependabot/varredura local.
- `SEC-006`: deriva entre build e schema ainda não é detectada automaticamente.
- `SEC-015`: mass assignment é provado sob `test`, não por profile de produção.
- `SEC-011`: inscrição WebSocket ativa sobrevive à revogação até reconectar.
- `frame-ancestors` depende do servidor de estáticos do ambiente implantado.

Os itens acima conservam responsável e prazo no backlog. Nenhum crítico ou alto
não excetuado permanece aberto.
