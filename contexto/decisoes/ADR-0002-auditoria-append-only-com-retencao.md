# ADR-0002 — Auditoria append-only com retenção definida

- Status: accepted
- Data: 2026-08-01

## Contexto

O padrão anterior afirmava que auditoria nunca expirava. Isso confundia
integridade durante a retenção com guarda eterna e impedia aplicar finalidade,
minimização, obrigação contratual/legal e descarte verificável.

## Decisão

Eventos de auditoria não são editados; correção é novo evento. Cada categoria
tem finalidade, fundamento, acesso autorizado, prazo, legal hold aplicável e
processo verificável de descarte ou anonimização. Auditoria permanece separada
de logs operacionais, que possuem outra retenção.

## Alternativas descartadas

- Retenção eterna por padrão: maximiza exposição e custo sem fundamento geral.
- Permitir edição para atender correção/exclusão: destrói a rastreabilidade.

## Consequências e revisão

Schema e jobs futuros precisam distinguir expurgo autorizado de alteração
cotidiana. Prazo concreto depende da categoria e de decisão jurídica/contratual.
Revisar quando houver requisito legal ou contrato que imponha retenção diferente.
