---
id: "03"
title: "Banco, papéis, RLS e integridade"
phase: "foundation"
risk: "high"
prerequisites: ["01", "02"]
produces: ["migrations seguras", "roles separados", "isolamento no banco"]
gate: "A"
---

# Prompt 03 — Banco, roles, RLS e integridade

## Objetivo

Faça do PostgreSQL uma barreira verificável de isolamento e integridade, com
Flyway separado da conexão cotidiana da aplicação.

## Protocolo obrigatório

Inspecione migrations já aplicadas; nunca edite uma migration publicada.
Crie nova migration somente para schema, índice, constraint, trigger, policy ou
seed estrutural. Tenant nunca vem do cliente. Alteração irreversível ou que
possa rejeitar dados existentes exige diagnóstico e decisão antes da execução.

## Trabalho

1. Separe `migrator` e `runtime`; runtime não recebe superusuário, `BYPASSRLS`,
   `CREATE` ou `TRUNCATE`.
2. Classifique tabelas: entidade de negócio, evento append-only, fila técnica ou
   referência global. Aplique campos e retenção conforme a categoria.
3. Ative `ENABLE` e `FORCE ROW LEVEL SECURITY` nas tabelas por tenant e use
   `SET LOCAL app.tenant_id` dentro da transação.
4. Use FKs/uniques compostos com `tenant_id` onde a relação for multi-tenant.
5. Restrinja funções `SECURITY DEFINER`: `search_path` fixo, menor privilégio,
   retorno mínimo e teste específico. Reavalie a soma da superfície privilegiada.
6. Garanta compatibilidade backward durante deploy e rollback planejado.

## Testes e aceite

- migrations V1..N aplicam em PostgreSQL vazio e validam banco atualizado;
- runtime restrito não cruza tenant por leitura, escrita ou referência;
- papel conectado é inspecionado explicitamente nos testes;
- dados inválidos antigos falham com diagnóstico antes de adicionar constraint;
- índices críticos têm plano medido quando o volume justificar.

Evidência deve identificar commit, banco/versão, data, comandos e resultados.
