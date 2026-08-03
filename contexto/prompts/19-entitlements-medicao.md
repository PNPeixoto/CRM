---
id: "19"
title: "Entitlements e medição"
phase: "operations_compliance"
risk: "high"
prerequisites: ["04", "08", "12"]
produces: ["entitlements configuráveis", "ledger de uso", "limites auditáveis"]
gate: "E"
---

# Prompt 19 — Entitlements e medição

## Objetivo

Separe capacidade contratada de autorização do usuário e registre consumo em
livro-razão append-only idempotente. Não implemente cobrança/fatura neste PR.

## Protocolo obrigatório

Não fixe política comercial de usuário, unidade, número ou mensagem no código.
Produto/contrato decide métrica, limite soft/hard, grace period e vigência.
Entitlement não concede ação; permissão não cria entitlement; menu não aplica
nenhum dos dois. Mudança de métrica faturável exige decisão de cobrança.

## Trabalho

1. Modele catálogo técnico, concessão contratual versionada e vigência.
2. Registre evento de medição append-only com tenant, métrica, quantidade,
   ocorrência, fonte e chave idempotente.
3. Agregue posteriormente por janela/timezone; não use contador mutável como
   única evidência.
4. Aplique limite soft/hard e grace period configuráveis com comportamento claro.
5. Diferencie módulo indisponível, não contratado, sem permissão e oculto.
6. Permita reconciliação da origem até o agregado.

## Testes e aceite

- replay não duplica consumo;
- evento atrasado cai no período correto segundo política versionada;
- alteração de plano não reescreve histórico;
- tenant não consulta nem consome cota de outro;
- limite concorrente é atômico onde hard limit for exigido;
- agregado reconcilia com eventos de origem.
