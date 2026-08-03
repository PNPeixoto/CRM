---
id: "F10"
canonical_id: "frontend:F10"
title: "Auditoria WCAG 2.2 AA"
phase: "frontend_quality"
risk: "high"
prerequisites: ["frontend:F7"]
blocking: "before-production"
produces: ["auditoria WCAG", "correções verificadas", "risco residual"]
gate: "C"
---

# F10 — Auditoria de acessibilidade

## Objetivo

Audite o produto integrado contra WCAG 2.2 AA. Testes de acessibilidade já devem
acompanhar F0A e cada entrega; este prompt fecha lacunas sistêmicas.

## Trabalho

1. Selecione rotas e estados por risco: login, shell, formulários, listas, detalhe,
   diálogo, erro, 403/404, realtime e fluxos críticos.
2. Combine análise automatizada com inspeção manual por teclado, leitor de tela,
   zoom 200%, reflow, contraste, modo de alto contraste e redução de movimento.
3. Verifique ordem/visibilidade de foco, skip links, nomes/descrições, mensagens de
   status, alvos de toque, autenticação acessível e alternativas a arrastar.
4. Teste loading/empty/error/success, conteúdo dinâmico e timeout de sessão. Não
   use anúncio vivo para cada atualização ou texto irrelevante.
5. Corrija problemas no componente raiz quando sistêmicos e acrescente regressão
   automatizada viável.
6. Registre critério, rota, severidade, evidência, correção, responsável e risco
   residual. Exceção precisa de prazo e mitigação.

## Aceite

- nenhuma violação crítica/alta aberta nas jornadas de produção;
- fluxos críticos completam apenas por teclado;
- zoom/reflow não remove conteúdo ou ação;
- anúncios dinâmicos são úteis e não ruidosos;
- relatório diferencia resultado automatizado de verificação manual.

