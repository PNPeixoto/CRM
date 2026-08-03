---
id: "F9"
canonical_id: "frontend:F9"
title: "Listas grandes guiadas por evidência"
phase: "frontend_product"
risk: "medium"
prerequisites: ["frontend:F5"]
blocking: "evidence-triggered"
produces: ["baseline de lista", "otimização mensurada", "fallback acessível"]
gate: null
---

# F9 — Listas grandes

## Objetivo

Otimize somente quando volume e medição demonstrarem problema, preservando
navegação, acessibilidade e capacidade de encontrar dados.

## Gatilho

Registre rota, volume representativo, dispositivo/browser, métrica degradada e
meta. Não carregue milhares de registros apenas para justificar esta trilha.

## Trabalho

1. Meça baseline de rede, renderização, memória, interação, scroll e mudança de
   filtro com dados realistas.
2. Prefira paginação/cursor do servidor. Em scroll incremental, preserve âncora,
   posição e retorno ao item.
3. Aplique virtualização somente se a medição exigir. Preserve foco, semântica,
   altura estável e alternativa para busca/localização que o browser não alcança.
4. Busca remota é assíncrona, cancelável, limitada e ignora resposta ultrapassada.
5. Compare depois da mudança e reverta se piorar métrica crítica ou experiência
   assistiva.

## Aceite

- evidência anterior e posterior acompanha a entrega;
- filtro rápido não exibe resposta antiga;
- voltar ao detalhe restaura posição/contexto;
- teclado e tecnologia assistiva alcançam os itens necessários;
- não há virtualização ou abstração genérica sem ganho mensurado.

