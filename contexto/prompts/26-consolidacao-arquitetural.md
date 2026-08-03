---
id: "26"
title: "Consolidação arquitetural"
phase: "private_integrations_scale"
risk: "medium"
prerequisites: ["24", "25C"]
produces: ["mapa de dependências", "dívida priorizada", "contratos consolidados"]
gate: "F"
---

# Prompt 26 — Consolidação arquitetural

## Objetivo

Faça uma revisão integrada depois das capacidades operacionais, removendo
duplicação comprovada e fechando violações sem reescrever o sistema por gosto.

## Protocolo obrigatório

Este prompt diagnostica antes de alterar. Refactor grande, troca de banco/broker,
quebra de API ou mudança de módulo é cara de desfazer e exige decisão. Não crie
microserviços: o alvo continua monólito modular. Preserve comportamento com
testes de caracterização e não mexa em migration aplicada.

## Trabalho

1. Gere mapa de dependências dos módulos e execute verificação do Modulith.
2. Compare APIs públicas, eventos, DTOs, erros, idempotência e autorização.
3. Identifique ciclos, acesso a `internal`, abstrações de um uso e duplicação real.
4. Revise funções privilegiadas, jobs cross-tenant e blast radius.
5. Classifique dívida por risco/custo; proponha slices pequenos com rollback.
6. Atualize ADRs somente para decisões efetivamente tomadas.

## Testes e aceite

- nenhuma violação de módulo ou ciclo não aprovado;
- mudança preserva contratos ou inclui compatibilidade explícita;
- remoção de abstração tem teste e segundo caso inexistente comprovado;
- riscos críticos têm responsável e prazo;
- relatório diferencia fato, inferência e recomendação.
