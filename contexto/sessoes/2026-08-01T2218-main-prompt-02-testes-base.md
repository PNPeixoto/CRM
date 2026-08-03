# Sessão — backend:02 testes base

Data: 2026-08-01 22:18 (America/Montevideo)  
Branch: `main`  
Baseline: `793777d + working-tree`

## Resultado

Prompt 02 concluído. A suíte separa retorno rápido, integração real,
quarentena e contrato dos perfis de carga sem trocar PostgreSQL/Redis por H2 ou
mock.

## Mudanças

- `TesteDeIntegracao` marca o corte que sobe Testcontainers.
- Profiles Maven `gate-rapido`, `integracao`, `quarentena` e `carga`.
- `@Quarentena` exige issue, responsável, justificativa, categoria e expiração;
  categorias de segurança, tenant, migration e cobrança são bloqueadas.
- Teste de política reprova `@Disabled`, tag de quarentena fora do contrato e
  expiração vencida.
- Quatro perfis declarativos: login, inbox, fila de saída e relatórios.
- Wrapper Maven do Windows corrigido para diretório `.m2` que não é link.
- `backend/TESTES.md` documenta comandos, tempos, fixtures e diagnóstico seguro.

## Evidências

- Gate rápido: 29 testes, 0 falhas/erros/ignorados, 12,4 s.
- Contrato de carga: 4 perfis, 0 falhas/erros/ignorados, 3,9 s.
- Gate completo, execução 1: 60 testes, 0 falhas/erros/ignorados, 45,2 s.
- Gate completo, execução 2: 60 testes, 0 falhas/erros/ignorados, 35,0 s.
- Infra real: PostgreSQL `17-alpine`, Redis `7-alpine`, runtime
  `crm_runtime_test` separado de `crm_migrator_test`.

## Próximo

Executar `backend:03` sem alterar migrations já publicadas V1–V8.
