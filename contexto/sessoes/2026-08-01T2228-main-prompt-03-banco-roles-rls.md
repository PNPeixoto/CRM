# Sessão — backend:03 banco, papéis, RLS e integridade

Data: 2026-08-01 22:28 (America/Montevideo)  
Branch: `main`  
Baseline: `793777d + working-tree`

## Resultado

Prompt 03 concluído sem editar V1–V8. V9 restringe funções, transações usam
tenant local e o catálogo passou a ter auditoria executável.

## Mudanças

- V9 revoga `EXECUTE` de `PUBLIC` e entrega ao runtime somente
  `current_tenant_id` e cinco funções privilegiadas auditadas.
- Default privileges não voltam a abrir funções futuras.
- O runtime define tenant localmente em cada transação e zera a sessão ao
  devolver conexão ao pool.
- Teste fecha a classificação de 14 tabelas tenant e duas operacionais,
  conferindo `ENABLE + FORCE RLS`, policies e papel conectado.
- Preflight de V8 diagnostica 16 referências legadas antes das constraints.
- Estratégia de deploy, compatibilidade reversa e rollback documentadas.

## Evidências

- Gate rápido: 30 testes, 0 falhas/erros/ignorados.
- Integração: 35 testes com PostgreSQL/Redis reais, 0 falhas/erros/ignorados.
- Banco limpo: Flyway validou V1–V9.
- Banco atualizado: container separado aplicou V1–V8, depois somente V9, e
  `validate` terminou verde.
- Runtime conectado: sem superuser, bypass RLS, createdb, createrole, create de
  schema/banco ou truncate.

## Próximo

Executar `backend:04` e fechar o Gate A com modelo, migration e testes.
