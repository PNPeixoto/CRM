---
id: "16"
title: "Conector HTTP seguro"
phase: "omnichannel"
risk: "critical"
prerequisites: ["07", "15"]
produces: ["cliente isolado", "política anti-SSRF", "ações externas idempotentes"]
gate: "D"
---

# Prompt 16 — Conector HTTP seguro

## Objetivo

Implemente o conector HTTP como executor limitado de integrações aprovadas, não
como proxy genérico nem linguagem de execução remota.

## Protocolo obrigatório

Política de rede/egress, redirects ou linguagem de expressão é decisão de
segurança e exige aprovação quando ausente. Não use `eval`, JavaScript, shell,
SpEL irrestrito ou cliente compartilhado com cookies. Segredo é resolvido só no
momento do envio, nunca retornado, persistido em claro ou interpolado em log.

## Trabalho

1. Bloqueie IPv4/IPv6 privados, reservados, loopback, link-local e metadata.
2. Resolva A e AAAA, fixe destino durante a conexão e proteja DNS rebinding.
3. Desative redirects; se aprovados, valide cada hop com a mesma política.
4. Use cliente dedicado sem cookies, com TLS, timeout, limite de resposta,
   concorrência e orçamento por tenant; produção usa egress policy/proxy.
5. Restrinja métodos, headers, tipos e templates a uma expressão segura.
6. Gere idempotency key para ação externa; sanitize request/response antes de
   persistir diagnóstico.

## Testes e aceite

- suíte anti-SSRF cobre IPv4/IPv6, DNS rebinding, redirect e metadata;
- resposta grande/lenta é interrompida sem esgotar recursos;
- segredo não aparece em preview, erro, auditoria ou evidência;
- retry não duplica efeito externo quando o destino suporta idempotência;
- fuzzing da expressão não alcança classe, arquivo, rede ou execução arbitrária.
