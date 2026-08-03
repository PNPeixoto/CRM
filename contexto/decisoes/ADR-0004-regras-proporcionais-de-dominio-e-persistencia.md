# ADR-0004 — Regras proporcionais de domínio e persistência

- Status: accepted
- Data: 2026-08-01

## Contexto

Exigir camada de domínio pura e migration em todo slice, ou os mesmos campos em
toda tabela, produz abstrações e colunas artificiais sem melhorar segurança.

## Decisão

Regra relevante fica fora do controller e é testável sem HTTP. Entidade rica,
value object ou serviço de domínio existe quando houver invariantes, cálculos ou
transições. CRUD simples pode ser orquestrado pela aplicação sem camada vazia.

Migration só existe para mudança persistente. Tabelas são classificadas como
entidade de negócio, evento append-only, fila técnica ou referência global, e
recebem campos, índices e retenção coerentes com a categoria.

## Alternativas descartadas

- Camadas/colunas obrigatórias por template: aparência uniforme com semântica
  falsa e custo de manutenção.
- Ausência de padrão: favorece regras em controllers e schemas inconsistentes.

## Consequências e revisão

PR deve justificar a categoria e onde a regra vive. A decisão reduz boilerplate,
mas não relaxa DTOs separados, autorização, idempotência, RLS ou integridade.
