---
id: "14"
title: "Inbox em tempo real e recuperação"
phase: "omnichannel"
risk: "high"
prerequisites: ["06", "12", "13"]
companions: ["frontend:F8"]
produces: ["inbox paginada", "WebSocket autorizado", "reconexão sem lacunas"]
gate: "D"
---

# Prompt 14 — Inbox em tempo real

## Objetivo

Entregue inbox rápida sobre dados persistentes. Tempo real é otimização: REST e
cursor/event sequence recuperam o estado completo após desconexão.

Este prompt é dono do contrato do servidor: REST, cursor/sequence, autorização,
limites e recuperação. `frontend:F8` é dono do ciclo de conexão, reconciliação,
fallback adaptativo e comportamento acessível no navegador.

## Protocolo obrigatório

Não carregue milhares de mensagens. WebSocket exige allowlist de `Origin`,
autenticação no `CONNECT` e autorização no `SUBSCRIBE` e em toda ação recebida.
Conteúdo de conversa não vai para log. Sessão/tenant/unidade são revalidados,
nunca confiados no tópico enviado pelo cliente.

## Trabalho

1. Abra primeira página rapidamente e carregue histórico por cursor/keyset.
2. Exponha cursor, IDs, versões e busca paginada suficientes para que o cliente
   preserve scroll e deduplique; virtualização só ocorre em `frontend:F9` após
   medição que a justifique.
3. Use sequence/cursor para reconectar sem lacuna ou duplicação.
4. Limite conexões por usuário/tenant, tamanho/frequência de mensagem; configure
   heartbeat, timeout ocioso e backpressure.
5. Trate expiração/revogação de sessão durante conexão.
6. Persista `due_at` e eventos de SLA; verificadores são idempotentes e usam
   timezone configurado por tenant/unidade.

## Testes e aceite

- dataset representativo não é carregado integralmente;
- reconexão entre eventos converge ao estado REST;
- inscrição/ação cruzada entre tenants/unidades é negada;
- carga mede latência, erros, conexões e saturação;
- a evidência integrada de `frontend:F8` cobre teclado, leitor de tela, mobile e
  fallback enquanto desconectado.
