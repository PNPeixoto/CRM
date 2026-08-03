---
id: "F0"
canonical_id: "frontend:F0"
title: "Diagnóstico do frontend e extração de tokens"
phase: "frontend_foundation"
risk: "low"
prerequisites: ["backend:00"]
blocking: "before-demo"
produces: ["inventário frontend", "mapa página-API", "baseline de navegadores"]
gate: null
---

# F0 — Diagnóstico do frontend

## Objetivo

Descreva o cliente executável e suas divergências sem alterar código de
produção nem decidir silenciosamente questões de produto.

## Protocolo

Aplique `PREAMBULO.md`. Leia versões reais do `package.json`, lockfile e código;
não presuma biblioteca, protótipo, fonte ou tema. O código vence a trilha v3.

## Trabalho

1. Inventarie páginas, rotas, componentes, dependências, scripts e versões.
2. Classifique cada página como pronta, parcial ou placeholder com evidência.
3. Extraia tokens existentes: cores, fontes, espaçamento, raios, sombras e
   mecanismo de tema. Registre que Tailwind, Manrope, JetBrains Mono e os mapas
   claro/escuro existem hoje; verifique uso real, não intenção histórica.
4. Liste divergências entre protótipo, briefing e repositório como perguntas.
5. Mapeie `página → endpoint → contrato → estado backend`; destaque API manual,
   `fetch` disperso, paginação ausente e endpoint sem OpenAPI.
6. Inventarie armazenamento do navegador, cookies, CSP, uso de HTML/URL externa,
   scripts de instalação, source maps e telemetria.
7. Registre matriz de navegadores desktop/mobile, APIs necessárias e política
   de polyfill. Sem público/contrato/telemetria, registre a decisão pendente.
8. Rode lint, testes e build existentes sem corrigir falhas neste prompt.

## Entrega e aceite

Crie apenas `contexto/frontend-inventario.md` e o log de sessão. O documento
identifica commit, ambiente, comandos e resultados; não contém dado sensível.
Nenhuma dependência ou código de produção é alterado e toda divergência aponta
evidência e próximo teste verificável.
