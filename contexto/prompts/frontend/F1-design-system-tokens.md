---
id: "F1"
canonical_id: "frontend:F1"
title: "Design system e tokens usados"
phase: "frontend_foundation"
risk: "medium"
prerequisites: ["frontend:F0", "frontend:F0A"]
blocking: "before-demo"
produces: ["tokens semânticos", "primitivos da casca", "estados reutilizáveis"]
gate: "C"
---

# F1 — Design system e tokens usados

## Objetivo

Consolide o sistema visual executável sem criar biblioteca, componente ou tema
por antecipação.

## Protocolo

Tokens vêm de F0 e do código. O repositório já contém Tailwind, Manrope,
JetBrains Mono e mapeamentos claro/escuro; preserve-os. Não crie terceiro tema
nem expanda o escuro só para cumprir roteiro. Tema novo exige consumidor real.

## Trabalho

1. Separe primitivas de paleta e tokens semânticos; componentes consomem apenas
   semânticos. Remova literal visual duplicado somente com teste visual/funcional.
2. Mantenha escala tipográfica curta, espaçamento coerente e fonte monoespaçada
   restrita a identificador ou dado técnico.
3. Entregue somente primitivas usadas pela casca: botão, campo, avatar, tooltip,
   toast, skeleton, menu e modal se houver consumidor.
4. Tabela, drawer, tabs, breadcrumbs e seleção avançada nascem com a primeira
   tela que os utiliza, não neste prompt.
5. Cubra foco visível, teclado, disabled, erro, contraste e movimento reduzido.
6. Padronize loading, vazio, erro, sem permissão e offline; vazio inicial é
   diferente de filtro sem resultado.
7. Escreva testes de comportamento e acessibilidade para cada primitiva tocada.

## Aceite

- alteração de token semântico propaga sem editar componentes;
- nenhum literal de cor novo aparece fora da definição de tokens;
- temas existentes renderizam sem exceção por componente;
- nenhuma dependência ou componente sem consumidor foi adicionado;
- lint, testes e build passam.
