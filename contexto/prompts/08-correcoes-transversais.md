---
id: "08"
title: "Contratos e correções transversais"
phase: "usable_product"
risk: "medium"
prerequisites: ["07"]
produces: ["contrato HTTP comum", "erros correlacionáveis", "limites de servidor"]
gate: "C"
---

# Prompt 08 — Correções transversais

## Objetivo

Remova inconsistências que fariam cada vertical slice reinventar contrato,
erro, paginação, idempotência, observabilidade ou fronteira modular.

## Protocolo obrigatório

Leia código e preserve contratos já consumidos; quebra exige decisão e plano de
compatibilidade. Regra relevante deve ser testável sem HTTP, mas CRUD simples
não ganha domínio artificial. Migration só para alteração persistente. Segredo,
payload e dado pessoal não entram em log/evidência.

## Trabalho

1. Separe DTOs de entrada/saída das entidades e use allowlist de campos.
2. Padronize validação, erros genéricos e `correlation_id`/`request_id`.
3. Imponha tamanho máximo de payload, página, ordenação e filtros no servidor.
4. Use cursor/keyset para históricos extensos; offset apenas em cadastro pequeno
   com limite e justificativa medida.
5. Defina idempotency key para operações repetíveis e semântica de replay.
6. Versione API apenas em quebra real e preserve compatibilidade durante deploy.
7. Faça módulos dependerem somente de APIs públicas mínimas ou eventos.

## Testes e aceite

- JSON desconhecido/malformado não produz 500 nem vaza detalhe;
- mass assignment, payload excessivo e ordenação inválida são rejeitados;
- erro traz correlação e log correspondente sanitizado;
- replay idempotente devolve resultado consistente;
- `ApplicationModules.verify()` e contratos existentes permanecem verdes.
