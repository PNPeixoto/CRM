---
id: "F4"
canonical_id: "frontend:F4"
title: "Sessão, refresh e autorização de rotas"
phase: "frontend_security"
risk: "critical"
prerequisites: ["backend:05", "frontend:F3", "frontend:F4A"]
blocking: "before-external-pilot"
produces: ["sessão resiliente", "refresh single-flight", "guards de rota"]
gate: "B"
---

# F4 — Sessão e refresh

## Objetivo

Implemente sessão previsível entre recargas, múltiplas abas e expiração, sem
armazenar credenciais acessíveis ao JavaScript nem criar loops de autenticação.

## Trabalho

1. Mantenha access token somente em memória. Refresh usa cookie `HttpOnly` e
   `Secure`, com `SameSite`, `Domain` e `Path` definidos para a topologia real.
2. Ao recarregar, recupere a sessão pelo endpoint de refresh; não persista token.
3. Faça refresh `single-flight`: requisições concorrentes aguardam a mesma
   tentativa. Cada cadeia pode repetir a requisição original no máximo uma vez.
4. Falha definitiva encerra a sessão, limpa caches/contexto e preserva apenas um
   destino interno validado; bloqueie redirecionamento aberto.
5. Logout chama o servidor, invalida a sessão e comunica às abas. Use
   `BroadcastChannel` apenas para eventos de sessão, nunca para tokens ou PII.
6. Guards aguardam resolução inicial, distinguem 401/403/404 e não usam menu
   oculto como autorização.
7. Aplique os controles CSRF e CORS definidos em F4A a refresh, logout e escritas.
8. Teste recarga, dez requisições simultâneas expiradas, falha de refresh, logout
   em outra aba, destino inválido e ausência de ciclo infinito.

## Aceite

- uma rajada de expiração dispara exatamente um refresh;
- token não sobrevive em armazenamento acessível ao JavaScript;
- logout em uma aba encerra as demais sem compartilhar segredo;
- falhas convergem para um estado estável e compreensível;
- retorno pós-login aceita somente destino interno permitido.

