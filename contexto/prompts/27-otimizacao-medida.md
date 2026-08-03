---
id: "27"
title: "Otimização baseada em medição"
phase: "private_integrations_scale"
risk: "medium"
prerequisites: ["22", "26"]
produces: ["baseline de carga", "mudança mensurada", "orçamentos de desempenho"]
gate: "F"
---

# Prompt 27 — Otimização medida

## Objetivo

Otimize apenas gargalo observado em dataset e carga representativos. Não faça
cache, paralelismo, índice ou complexidade por intuição.

## Protocolo obrigatório

Banco segue fonte da verdade; cache é descartável e isolado por tenant. Mudança
que altera consistência, ordenação, entrega ou custo operacional exige decisão.
Colete telemetria sanitizada. Preserve comportamento e mantenha rollback simples.

## Trabalho

1. Defina cenário, dataset, hardware, concorrência e orçamento de latência/erro.
2. Registre baseline, plano SQL, CPU, memória, I/O, filas e saturação.
3. Formule uma hipótese e aplique a menor mudança capaz de testá-la.
4. Meça antes/depois e efeitos em cauda, não apenas média.
5. Teste invalidação/falha do cache, contenção e degradação sob dependência lenta.
6. Remova otimização que não produz ganho material ou aumenta risco sem retorno.

## Testes e aceite

- relatório reproduzível identifica commit/ambiente/dataset;
- orçamento é atendido sem aumentar taxa de erro ou violar consistência;
- P95/P99 e saturação aparecem junto do throughput;
- isolamento de cache/consulta entre tenants é testado;
- rollback e custo operacional da otimização estão documentados.
