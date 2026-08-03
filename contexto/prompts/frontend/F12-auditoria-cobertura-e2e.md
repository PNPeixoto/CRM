---
id: "F12"
canonical_id: "frontend:F12"
title: "Auditoria de cobertura e E2E"
phase: "frontend_quality"
risk: "high"
prerequisites: ["frontend:F0A", "frontend:F7"]
blocking: "before-production"
produces: ["matriz risco-teste", "E2E crítico", "política de quarentena"]
gate: "C"
---

# F12 — Auditoria de cobertura e E2E

## Objetivo

Audite os testes entregues continuamente desde F0A e preencha lacunas de maior
risco. Este prompt não é o início tardio da estratégia de testes.

## Trabalho

1. Mapeie jornadas/riscos para teste unitário, integração, contrato ou E2E. Não
   use percentual de linhas como único indicador.
2. Confirme cobertura de login, recarga, refresh concorrente/expirado, logout em
   abas, guards, troca de contexto/cache, erros por campo e quatro estados de tela.
3. Cubra CSRF/CSP/persistência segura, idempotência, permissões e, quando ativo,
   realtime com gap, duplicata, reconexão e ordem.
4. Mantenha E2E apenas para jornadas críticas que atravessam fronteiras. Use dados
   determinísticos, relógio controlado quando necessário e seletores semânticos.
5. Remova esperas arbitrárias e dependência de ordem. Falha intermitente não vira
   retry infinito.
6. Quarentena exige evidência, responsável, prazo e cobertura compensatória; não
   pode mascarar teste de segurança ou jornada bloqueante.
7. Publique matriz, tempo de execução, flakiness e lacunas aceitas.

## Aceite

- cada risco crítico tem teste adequado ou exceção com prazo;
- E2E crítico roda de forma determinística no pipeline;
- refresh concorrente e isolamento de contexto têm regressão automatizada;
- testes verificam comportamento e acessibilidade, não detalhes frágeis;
- quarentena é visível, temporária e não libera falha bloqueante.

