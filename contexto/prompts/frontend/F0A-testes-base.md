---
id: "F0A"
canonical_id: "frontend:F0A"
title: "Infraestrutura mínima de testes frontend"
phase: "frontend_foundation"
risk: "medium"
prerequisites: ["frontend:F0"]
blocking: "before-demo"
produces: ["runner reproduzível", "simulação HTTP", "smoke acessível"]
gate: null
---

# F0A — Infraestrutura mínima de testes

## Objetivo

Garanta que testes acompanhem cada entrega desde a fundação, com os mesmos
comandos localmente e no CI.

## Protocolo

Preserve Vitest, Testing Library e jsdom já instalados. Só adote simulador HTTP
ou auditor automatizado após comprovar lacuna e fixar versão no lockfile.

## Trabalho

1. Padronize runner, ambiente DOM, setup, relógio/rede controláveis e limpeza.
2. Configure simulação HTTP no limite da camada de acesso; fixtures derivam do
   contrato e nunca contêm payload real.
3. Inclua verificação automatizada de acessibilidade para componentes, sem
   tratá-la como substituta do teste manual.
4. Crie um smoke test por papel/rótulo acessível, não por classe CSS.
5. Separe comandos rápidos, integração de componente e E2E; E2E não entra no
   gate rápido sem jornada crítica concreta.
6. Faça falha produzir diagnóstico sanitizado e determinístico.
7. Documente a política de quarentena do pacote principal e automatize expiração
   quando houver infraestrutura de CI; sessão/isolamento não são quarentenados.

## Aceite

- a suíte roda duas vezes seguidas com o mesmo resultado;
- um contrato HTTP inválido e uma violação acessível plantados fazem teste falhar;
- o mesmo comando funciona local e no CI;
- cada prompt F1–F13 passa a entregar seus próprios testes;
- nenhuma meta global de cobertura substitui cobertura por risco.
