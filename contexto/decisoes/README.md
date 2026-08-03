# ADRs do CRM PNP

Cada decisão que fecha uma porta recebe um arquivo próprio. O histórico anterior
a 2026-08-01 permanece em `contexto/03-decisoes.md` e não deve receber novas
entradas.

## Convenção

Nome: `ADR-NNNN-slug-curto.md`.

Campos mínimos:

- título, status e data;
- contexto e forças relevantes;
- decisão;
- alternativas descartadas;
- consequências e gatilho de revisão;
- referências/evidências sem material sensível.

Status possíveis: `proposed`, `accepted`, `deprecated`, `superseded`. Uma
decisão aceita não é reescrita; mudança recebe novo ADR e marca a relação de
substituição.
