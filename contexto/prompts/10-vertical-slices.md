---
id: "10"
title: "Vertical slices P0 revisáveis"
phase: "usable_product"
risk: "medium"
prerequisites: ["08", "09"]
companions: ["frontend:F7"]
produces: ["fluxo ponta a ponta", "contrato testado", "PR revisável"]
gate: "C"
---

# Prompt 10 — Vertical slices

## Objetivo

Entregue um fluxo P0 completo e pequeno, da interface ao banco, sem agrupar
módulos independentes nem criar abstração para casos futuros.

O backend é dono de domínio, dados, autorização e contrato. A interface da
mesma jornada é uma execução parametrizada de `frontend:F7:<dominio>`; as duas
evidências formam a fatia vertical sem duplicar responsabilidades.

## Protocolo obrigatório

Antes de agir, nomeie uma jornada, ator, resultado e fronteira do PR. Não inicie
P1/P2 com dependência P0 aberta. Pare apenas pelas condições críticas da v3;
decisões reversíveis preservam comportamento. Migration Flyway só quando o
fluxo altera persistência. Não invente domínio rico para CRUD sem invariantes.

## Trabalho

1. Especifique contrato de entrada/saída, autorização e estados de erro/vazio.
2. Mantenha regra relevante fora do controller e testável sem HTTP.
3. Imponha limites, paginação, ordenação allowlisted e idempotência aplicável.
4. Respeite fronteiras Spring Modulith e publique evento apenas quando houver
   desacoplamento real.
5. Implemente a UI acessível por `frontend:F7:<dominio>`, usando o design system
   existente e o contrato real desta fatia.
6. Não entregue tela fake que não consome o contrato real.

## Testes e aceite

- unidade para regra, integração para banco/RLS e componente para UI;
- E2E somente se a jornada for crítica para Gate C;
- tenant, ação, escopo, registro e mass assignment são cobertos;
- dataset grande usa cursor; offset pequeno tem limite explícito;
- PR tem um objetivo, rollback claro e evidência reproduzível.
