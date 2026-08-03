---
id: "F2"
canonical_id: "frontend:F2"
title: "Casca da aplicação e navegação"
phase: "frontend_foundation"
risk: "medium"
prerequisites: ["frontend:F1"]
blocking: "before-demo"
produces: ["shell responsivo", "navegação derivada", "troca segura de contexto"]
gate: "C"
---

# F2 — Casca e navegação

## Objetivo

Entregue uma casca acessível cuja navegação represente capacidades autorizadas,
sem transformar visibilidade de menu em segurança.

## Trabalho

1. Derive menu do registro central de rotas conhecido pelo build.
2. Resolva separadamente a interseção: rota local conhecida, capability do
   backend, entitlement, permissão e apresentação do segmento. Backend é a
   autoridade; componente compilado é apenas capacidade de renderização.
3. Mostre seletor de tenant/unidade apenas com mais de uma opção autorizada.
   Troca de contexto limpa cache antes de apresentar o novo rótulo.
4. Implemente casca responsiva com foco ordenado, skip link e navegação mobile.
   Busca/criação rápida podem ser affordances desabilitadas se o caso não existe.
5. Persista menu recolhido somente como preferência não sensível, com chave
   versionada por usuário. Nunca misture com sessão ou autorização.
6. Rota desconhecida é 404. Módulo conhecido sem permissão é 403; registro fora
   de escopo segue a política anti-enumeração do backend, normalmente 404.
7. Teste teclado, foco, uma/múltiplas unidades, troca de contexto e estados 403/404.

## Aceite

- nova página exige uma entrada no registro, sem menu paralelo;
- seletor não aparece com uma única opção;
- troca de contexto não mostra dados anteriores;
- shell funciona por teclado, em mobile e com rede lenta;
- preferências persistidas não contêm dado sensível.
