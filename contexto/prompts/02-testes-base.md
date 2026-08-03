---
id: "02"
title: "Base de testes orientada a risco"
phase: "foundation"
risk: "medium"
prerequisites: ["01"]
produces: ["pirâmide de testes", "fixtures isoladas", "política de flaky tests"]
gate: "A"
---

# Prompt 02 — Testes base

## Objetivo

Estabeleça uma suíte reproduzível que prove riscos reais, sem meta global de
cobertura e sem mocks que escondam RLS, transações ou constraints.

## Protocolo obrigatório

Leia o contexto e preserve testes existentes. Falha não pode ser ignorada.
Quarentena exige issue, responsável, justificativa, expiração, execução
contínua e exclusão explícita do gate; segurança, tenant, migration e cobrança
nunca são quarentenados. Não registre dados sensíveis em fixture ou relatório.

## Trabalho

1. Use unitários para regras/transformações, integração para banco/RLS/filas,
   contract tests para provedores, componente para UI e E2E só para jornadas
   críticas.
2. Configure Testcontainers com PostgreSQL e cache reais e papéis separados.
3. Garanta limpeza determinística sem `TRUNCATE` concedido ao runtime.
4. Crie builders com dados fictícios e tenant explícito; nenhuma fixture global
   pode vazar estado entre testes.
5. Verifique fronteiras Spring Modulith e comportamento transacional.
6. Prepare perfis para carga em login, inbox, filas e relatórios, sem executá-los
   indiscriminadamente no gate rápido.

## Aceite

- suíte roda duas vezes consecutivas com o mesmo resultado;
- falha produz diagnóstico sanitizado;
- testes críticos usam infraestrutura equivalente, não H2;
- tempo de gate e divisão das suítes estão documentados;
- política de quarentena está automatizada e tem expiração verificável.

Anexe evidência com commit, ambiente, data, comando, resultado e artefato.
