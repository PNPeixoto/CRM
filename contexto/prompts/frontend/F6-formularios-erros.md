---
id: "F6"
canonical_id: "frontend:F6"
title: "Formulários, validação e erros"
phase: "frontend_foundation"
risk: "high"
prerequisites: ["frontend:F3", "frontend:F5"]
blocking: "before-external-pilot"
produces: ["padrão de formulário", "erros por campo", "modelagem temporal e monetária"]
gate: "C"
---

# F6 — Formulários e erros

## Objetivo

Crie um padrão de entrada acessível que preserve semântica de domínio e trate
datas, horários, dinheiro e segredos sem conversões ambíguas.

## Trabalho

1. Use validação local para resposta rápida e validação do servidor como
   autoridade. Não copie regras complexas que podem divergir.
2. Mapeie erros RFC 9457 por campo, mantenha valores válidos e leve foco ao
   primeiro erro; ofereça resumo quando o formulário for longo.
3. Evite envio duplo e use idempotência onde a operação admitir repetição.
   Avise sobre saída com alterações somente quando houver perda real.
4. Distinga data civil, horário local, instante e intervalo. Converta instante na
   fronteira usando timezone explícito; não transforme data civil em UTC.
5. Modele dinheiro como `amountMinor` inteiro seguro (ou string quando exceder o
   limite) e `currency`. Nunca calcule valor monetário com ponto flutuante.
6. Campos secretos chegam vazios na edição e só alteram o servidor quando o
   usuário fornecer novo valor. Nunca reexiba segredo mascarado como dado real.
7. Centralize formatação/parsing com `Intl` e locale conhecido; transporte não
   depende da máscara exibida.
8. Teste teclado, leitores de tela, erros globais/de campo, datas em fusos
   distintos, arredondamento, moedas, submissão concorrente e segredo em branco.

## Aceite

- erro de campo é associado e anunciado no controle correto;
- data civil não muda de dia por conversão de fuso;
- valor monetário atravessa a fronteira sem `number` fracionário;
- envio repetido não duplica efeito;
- edição não revela nem apaga segredo sem intenção explícita.

