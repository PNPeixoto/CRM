---
id: "28"
title: "Escalabilidade horizontal"
phase: "private_integrations_scale"
risk: "high"
prerequisites: ["23", "24", "27"]
produces: ["aplicação multi-instância", "tempo real distribuído", "workers escaláveis"]
gate: "F"
---

# Prompt 28 — Escalabilidade horizontal

## Objetivo

Prove execução com múltiplas instâncias sem sessão local, duplicação de jobs,
lacuna no tempo real ou dependência oculta de sticky session.

## Protocolo obrigatório

Trocar broker/consistência é decisão arquitetural cara e exige ADR com teste e
rollback. Não decomponha em microserviços. Escale somente após baseline do
prompt 27. Tenant permanece isolado em cache, tópicos, filas e armazenamento.

## Trabalho

1. Externalize estado de sessão/presença necessário e trate cache como derivado.
2. Substitua broker STOMP em memória ou implemente ponte distribuída aprovada;
   REST/cursor continua fonte de recuperação.
3. Garanta workers com lease/lock, idempotência e rebalanceamento seguro.
4. Configure proxy, WebSocket, readiness, drain e shutdown gracioso.
5. Teste perda de instância, partição do broker/cache e deploy rolling.
6. Dimensione conexões de banco, filas e budgets por tenant sob carga.

## Testes e aceite

- duas ou mais instâncias preservam login, inbox, jobs e eventos;
- queda durante processamento não perde nem duplica efeito;
- reconexão recupera lacunas por cursor/sequence;
- load test registra latência, erro, throughput e saturação antes/depois;
- restore, rollback e runbook multi-instância aprovam Gate F.
