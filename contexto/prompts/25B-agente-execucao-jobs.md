---
id: "25B"
title: "Agente privado — execução de jobs"
phase: "private_integrations_scale"
risk: "critical"
prerequisites: ["25"]
produces: ["jobs assinados", "allowlist de execução", "resultado sanitizado"]
gate: "F"
---

# Prompt 25B — Agente privado: execução de jobs

## Objetivo

Implemente o ciclo de jobs previamente definidos, sem shell, comando arbitrário
ou linguagem que permita escapar da allowlist.

## Protocolo obrigatório

Cada job é assinado, tem nonce, expiração, tenant/vínculo e tipo permitido. O
agente roda como usuário sem privilégio e resolve segredos localmente. Resultado
é sanitizado e limitado antes de sair do ambiente. Nova capacidade de job é
expansão da superfície de execução e exige threat model/ADR.

## Trabalho

1. Modele estados, lease, expiração, cancelamento e idempotência.
2. Valide assinatura, replay, vínculo e allowlist antes de qualquer efeito.
3. Imponha timeout, tentativas, concorrência e tamanho de entrada/saída.
4. Execute adaptador tipado e configurado; proíba shell, caminho livre e eval.
5. Separe stdout técnico de resultado; ambos são sanitizados e minimizados.
6. Audite emissão, aceite, conclusão, cancelamento e falha sem payload sensível.

## Testes e aceite

- job adulterado, expirado, repetido ou de tipo desconhecido é rejeitado;
- timeout/cancelamento encerra recurso filho sem deixar lease eterno;
- resposta excessiva é truncada/rejeitada com diagnóstico seguro;
- reinício converge sem duplicar efeito idempotente;
- tentativa de comando/caminho arbitrário é bloqueada por construção.
