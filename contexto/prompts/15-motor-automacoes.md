---
id: "15"
title: "Motor de automações"
phase: "omnichannel"
risk: "high"
prerequisites: ["10", "12"]
produces: ["definições versionadas", "execuções controladas", "dry-run"]
gate: "D"
---

# Prompt 15 — Motor de automações

## Objetivo

Implemente apenas o motor interno de automações. Não inclua o conector HTTP,
agente privado ou cobrança neste PR.

## Protocolo obrigatório

Efeito externo não é executado no dry-run. Cada execução aponta para a versão
imutável da definição usada. Tenant vem do contexto/evento validado. Mudança na
semântica de compensação ou entrega é decisão arquitetural. Logs são sanitizados
e não contêm payload, mensagem ou segredo.

## Trabalho

1. Modele estados explícitos, definição/versionamento e execução/passos.
2. Imponha limites de duração, passos, ramificações, concorrência e quotas por
   tenant.
3. Deduplicate gatilhos/execuções e bloqueie recursão de automação.
4. Permita pausa e cancelamento com transições idempotentes.
5. Registre compensação quando possível; marque claramente efeito irreversível.
6. Faça retry apenas onde seguro e preserve causalidade/versão.

## Testes e aceite

- replay do mesmo gatilho não duplica execução;
- definição editada não altera execução antiga;
- dry-run não produz efeito externo;
- loop, fan-out excessivo, timeout, pausa e cancelamento são cobertos;
- workers concorrentes respeitam lease e quotas;
- trilha permite explicar cada transição sem expor dados sensíveis.
