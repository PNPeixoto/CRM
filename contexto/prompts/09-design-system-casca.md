---
id: "09"
title: "Design system e casca acessível"
phase: "usable_product"
risk: "medium"
prerequisites: ["08"]
companions: ["frontend:F1", "frontend:F2"]
produces: ["tokens visuais", "shell responsivo", "baseline WCAG 2.2 AA"]
gate: "C"
---

# Prompt 09 — Design system e casca

## Objetivo

Consolide o sistema visual já aprovado e a casca do produto sem importar uma
segunda biblioteca ou confundir seletor de tenant/unidade com autorização.

Este prompt consolida o Gate C entre as trilhas. A implementação detalhada de
tokens/componentes e da casca pertence a `frontend:F1` e `frontend:F2`; aqui se
validam também capabilities, entitlements e permissões fornecidos pelo backend.

## Protocolo obrigatório

Preserve dependências, fontes e tokens existentes. Só adote biblioteca, fonte
ou framework novo se houver ADR aprovado ou lacuna comprovada e escolha
justificada. Preferência visual reversível usa o default menos invasivo; não
pare por tema. Segurança e acessibilidade vencem estética.

## Trabalho

1. Centralize tokens semânticos; nenhum componente usa hex literal ou escala de
   cor como contrato de domínio.
2. Reuse componentes existentes e mantenha textos em português do Brasil.
3. Mire WCAG 2.2 AA: foco não oculto, alvo mínimo, zoom/reflow, contraste,
   redução de movimento, autenticação acessível e erros ligados ao campo.
4. Drag-and-drop tem alternativa por teclado e status nunca depende só de cor.
5. Mostre seletor de tenant/unidade somente com mais de uma opção autorizada.
6. Menu reflete navegação/entitlement/permissão resolvidos, mas o backend
   continua sendo a barreira.
7. Execute ou referencie a evidência de `frontend:F1` e `frontend:F2`, evitando
   uma segunda implementação concorrente da mesma casca.

## Testes e aceite

- navegação completa por teclado e leitor de tela nos fluxos críticos;
- 200% de zoom e viewport móvel sem perda de conteúdo/ação;
- teste de componente cobre estados vazio, erro, carregando e sem permissão;
- lint, testes e build de produção passam;
- auditoria de acessibilidade lista evidências e riscos residuais.
