---
id: "06"
title: "Autorização e multi-tenancy"
phase: "core_security"
risk: "high"
prerequisites: ["04", "05"]
produces: ["RBAC com escopo", "políticas por registro", "testes anti-IDOR"]
gate: "B"
---

# Prompt 06 — Autorização e multi-tenancy

## Objetivo

Implemente autorização no backend nos eixos ação, escopo e registro. Navegação
oculta é apenas UX e entitlement não é permissão.

## Protocolo obrigatório

Tenant e unidade efetiva vêm de identidade/membership verificados, nunca de
body, query ou header livre. Mudança na semântica de papel ou escopo exige ADR.
Preserve o comportamento quando a escolha for reversível. Não exponha dado
proibido para depois filtrá-lo no frontend.

## Trabalho

1. Defina permissões atômicas por ação e composição por role.
2. Resolva escopo de rede/tenant/unidade/equipe/próprio registro de modo central.
3. Revalide autorização em jobs, exportações e assinaturas WebSocket, não apenas
   na requisição que os criou.
4. Evite mass assignment com DTOs específicos e allowlist de campos.
5. Separe visibilidade de menu, disponibilidade técnica e entitlement.
6. Produza auditoria sanitizada de negações relevantes sem transformar log em
   inventário de recursos existentes.

## Testes e aceite

- matriz role × ação × escopo aprovada;
- IDOR em leitura/escrita, troca de unidade, exportação, arquivo, relatório,
  cache, WebSocket, job e reprocessamento é negado;
- tenant A não referencia registro do tenant B;
- usuário sem membership vigente perde acesso imediatamente conforme política;
- testes usam runtime PostgreSQL restrito e integram o Gate B.
