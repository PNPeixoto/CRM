---
id: "12"
title: "Fundação omnichannel persistente"
phase: "omnichannel"
risk: "high"
prerequisites: ["03", "06", "10"]
produces: ["modelo normalizado", "inbox persistente", "inbound/outbox duráveis"]
gate: "D"
---

# Prompt 12 — Fundação de canais

## Objetivo

Construa a espinha dorsal independente de provedor: conexão, conversa,
mensagem, entrada durável, saída durável e livro-razão de medição.

## Protocolo obrigatório

Chat ao vivo é o primeiro canal concreto. Nenhum campo de provedor vaza para o
domínio normalizado. Webhook recebe bytes crus limitados, verifica assinatura,
persiste duravelmente e só então responde sucesso; processamento é assíncrono.
Falha de persistência retorna erro transitório. Payload/segredo nunca vai a log.

## Trabalho

1. Modele `ChannelConnection`, `Conversation`, `Message`, `InboundEvent`, outbox
   e eventos de medição append-only com tenant e idempotência.
2. Classifique tabelas e aplique timestamps/retention adequados à categoria.
3. Restrinja payload bruto, criptografe quando necessário, masque na interface,
   retenha por prazo curto e use hash quando o corpo não precisar persistir.
4. Faça reserva de workers segura para concorrência, lease/owner, backoff e erro
   sanitizado. Banco permanece fonte da verdade.
5. Meça por evento idempotente; agregação posterior, nunca contador mutável.
6. Implemente Live Chat como primeiro adapter sem abstração além da porta útil.

## Testes e aceite

- duplicata concorrente converge por constraint/idempotência;
- sucesso do webhook nunca antecede persistência;
- worker reiniciado não perde nem duplica efeito;
- RLS e relações compostas isolam tenants;
- fila morta/reprocessamento e retenção têm procedimento verificável.
