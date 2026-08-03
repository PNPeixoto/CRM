---
id: "F11"
canonical_id: "frontend:F11"
title: "Auditoria de performance"
phase: "frontend_quality"
risk: "medium"
prerequisites: ["frontend:F7"]
blocking: "before-production"
produces: ["baseline por rota", "budgets calibrados", "plano de melhoria"]
gate: "C"
---

# F11 — Auditoria de performance

## Objetivo

Meça a experiência por rota e jornada, corrija gargalos observados e transforme
uma baseline estável em budgets que não incentivem otimizações cegas.

## Trabalho

1. Meça login, shell e rotas representativas como inbox, dashboard e cadastros,
   em desktop e mobile de referência, com rede/CPU documentadas.
2. Em campo, quando houver volume suficiente, acompanhe p75 de LCP, INP e CLS por
   classe de dispositivo: metas de referência LCP <= 2,5 s, INP <= 200 ms e
   CLS <= 0,1. Trate ausência de amostra como ausência de evidência.
3. Em laboratório, registre TBT, JS/CSS/imagens comprimidos por rota, long tasks,
   memória, renders e transição de rota. Meça também abrir conversa, filtrar lista
   e mover cartão quando essas jornadas existirem.
4. Investigue importações, code splitting, fontes, imagens, cache e renderização
   somente conforme o perfil indicar.
5. Defina budget por rota após baseline repetível: primeiro aviso, depois bloqueio
   quando a variância e a infraestrutura estiverem estabilizadas.
6. Preserve funcionalidade e acessibilidade; não troque métrica por tela vazia ou
   carregamento que apenas oculta trabalho.

## Aceite

- relatório contém ambiente, amostra, mediana/variância e comparação por rota;
- budgets derivam da baseline e têm responsável;
- regressão estatisticamente material impede promoção após fase de aviso;
- principais gargalos têm evidência antes/depois;
- métricas SPA cobrem as interações críticas, não só o primeiro carregamento.

