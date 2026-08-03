---
id: "18"
title: "LGPD, retenção e direitos do titular"
phase: "operations_compliance"
risk: "critical"
prerequisites: ["07", "17"]
produces: ["inventário de tratamento", "políticas de retenção", "fluxos de direito"]
gate: "E"
---

# Prompt 18 — LGPD e retenção

## Objetivo

Modele controles técnicos para tratamento, retenção, exportação, correção,
anonimização e descarte. Decisão jurídica permanece fora do código.

## Protocolo obrigatório

Não assuma universalmente que tenant é controlador e plataforma operadora. O
papel depende da finalidade: a plataforma pode operar dados do cliente e
controlar dados próprios de conta, billing, segurança e fraude. Hipótese legal,
prazo, legal hold, transferência ou papel contratual exige validação competente.

## Trabalho

1. Inventarie atividade, finalidade, hipótese, titulares/dados, papel,
   operadores/suboperadores, transferências, acesso e retenção.
2. Defina política por categoria para banco, auditoria, log, webhook, mídia,
   exportação, backup e fila técnica.
3. Implemente descarte/anonimização verificável e legal hold que impeça expurgo.
4. Verifique identidade e autorização antes de exportar, corrigir ou excluir.
5. Produza exportação rastreável e minimize/mascare o que não pertence ao pedido.
6. Documente incidente, comunicação, restauração e relatório de impacto quando
   necessário.

## Testes e aceite

- relógio controlado prova retenção e expurgo idempotente;
- legal hold preserva somente o escopo autorizado;
- exclusão impossível vira anonimização/recusa fundamentada, não falha silenciosa;
- backup e réplicas têm tratamento documentado;
- toda categoria possui fundamento, prazo, acesso e descarte verificável.
