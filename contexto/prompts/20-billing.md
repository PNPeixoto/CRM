---
id: "20"
title: "Cobrança, fatura e reconciliação"
phase: "operations_compliance"
risk: "critical"
prerequisites: ["17", "19"]
produces: ["cálculo versionado", "fatura rastreável", "reconciliação de provedor"]
gate: "E"
---

# Prompt 20 — Billing

## Objetivo

Implemente cálculo e reconciliação a partir de entitlements/medição aprovados,
sem embutir política comercial não decidida.

## Protocolo obrigatório

Moeda, timezone de fechamento, arredondamento, tributo, grace period, estorno e
provedor são decisões comerciais/contratuais: pare se ausentes. Dinheiro usa
inteiro na menor unidade; nunca `float`/`double`. Webhook financeiro valida,
persiste e é idempotente antes de responder sucesso.

## Trabalho

1. Versione preço/regra e grave snapshot aplicado à linha da fatura.
2. Feche período por timezone/configuração explícitos e estado transacional.
3. Faça cálculo determinístico da origem ao total, com impostos/ajustes separados.
4. Integre provedor com idempotência, assinatura, reconciliação e estado local.
5. Modele estorno/crédito como novo evento, não edição destrutiva.
6. Revalide entitlement sem usar status de pagamento como permissão de usuário.

## Testes e aceite

- golden tests cobrem arredondamento, moeda, virada de período e proration
  somente se aprovados;
- webhook duplicado/fora de ordem converge;
- centavos da origem reconciliam com linha, subtotal e total;
- concorrência não gera duas faturas/cobranças;
- auditoria explica preço, versão, medição e ajuste sem expor dado de pagamento.
