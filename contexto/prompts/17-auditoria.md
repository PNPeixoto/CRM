---
id: "17"
title: "Trilha de auditoria"
phase: "operations_compliance"
risk: "high"
prerequisites: ["06", "08"]
produces: ["eventos append-only", "acesso auditável", "integridade da trilha"]
gate: "E"
---

# Prompt 17 — Auditoria

## Objetivo

Implemente trilha de negócio append-only, protegida contra alteração e separada
de logs operacionais. Retenção e direitos do titular ficam no prompt 18.

## Protocolo obrigatório

Append-only não significa eterno. Não fixe prazo nem hipótese legal neste PR.
Registre apenas metadados necessários: ator, ação, escopo, alvo, instante,
resultado e correlação. Não grave token, cookie, segredo, payload, conteúdo de
mensagem, arquivo ou dado sensível desnecessário.

## Trabalho

1. Classifique auditoria como evento append-only: sem `updated_at` e sem edição.
2. Defina eventos canônicos e versionados para ações de alto impacto.
3. Capture ator humano/sistema, tenant, unidade/escopo, alvo e motivo sanitizado.
4. Proteja escrita por API interna mínima e banco; leitura exige permissão
   específica e também gera evento.
5. Garanta ordenação, correlação e integridade verificável sem prometer
   imutabilidade criptográfica inexistente.
6. Mantenha log operacional fora da tabela de auditoria.

## Testes e aceite

- update/delete comum é negado;
- ação negada, exportação, credencial, role e configuração sensível são cobertas;
- tenant A não consulta evento do tenant B;
- falha da auditoria segue política explícita por criticidade da ação;
- interface mascara campos e pagina no servidor;
- schema permite política de retenção/expurgo posterior controlada.
