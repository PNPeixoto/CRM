---
id: "25"
title: "Agente privado — protocolo e enrollment"
phase: "private_integrations_scale"
risk: "critical"
prerequisites: ["07", "16", "24"]
produces: ["protocolo versionado", "enrollment único", "identidade revogável"]
gate: "F"
---

# Prompt 25 — Agente privado: protocolo e enrollment

## Objetivo

Entregue somente protocolo, identidade e registro seguro do agente. Não execute
jobs nem implemente atualização neste PR.

## Protocolo obrigatório

O agente conecta sempre de saída e roda sem privilégio. Credencial local fica no
cofre do sistema operacional. Escolha de mTLS, assinatura, distribuição ou SOs
suportados é decisão de segurança/operação: pare se não houver ADR. O CRM central
nunca recebe o valor de segredo do ambiente do cliente.

## Trabalho

1. Versione protocolo e negociação de compatibilidade/capacidades.
2. Enrollment usa token único, curto, armazenado por hash e queimado atomically.
3. Emita identidade própria, rotacionável e revogável para cada instalação.
4. Vincule agente a tenant e integrações permitidas sem aceitar tenant do cliente.
5. Use nonce, timestamp/expiração e assinatura para impedir replay.
6. Registre enrollment/revogação em auditoria sanitizada e heartbeat mínimo.

## Testes e aceite

- replay ou corrida do token permite no máximo um registro;
- agente revogado não autentica nem renova credencial;
- tenant A não registra/vincula agente no tenant B;
- downgrade/incompatibilidade de versão falha fechado;
- logs/evidências não mostram token, certificado privado ou segredo local.
