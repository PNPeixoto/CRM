# Sessão 2026-08-15 — Backend 21: relatórios e exportações

- Branch: `main`
- Baseline: working tree após Backend 19 e decisão do Backend 20
- Objetivo: versionar métricas e entregar exportação assíncrona segura

## Implementação

- V28 cria catálogo estrutural somente leitura com 13 métricas de
  `OVERVIEW_V1`, incluindo fórmula, fonte, granularidade, timezone, unidade e
  moeda quando aplicável.
- Dashboard e exportação usam a mesma fotografia canônica.
- Pedido CSV é idempotente por tenant, solicitante e chave; jobs usam RLS
  `ENABLE + FORCE`, lote limitado, lease, tentativas e concorrência por tenant.
- `reports.read` com alcance `TENANT` é revalidada no pedido, imediatamente
  antes da geração, ao criar a URL e no download.
- CSV neutraliza fórmulas e respeita limites de linhas, colunas e 5 MiB.
- O arquivo é cifrado com AES-256-GCM antes do volume privado. A URL usa outra
  chave HMAC, vale no máximo cinco minutos e não substitui sessão válida.
- Expiração invalida o acesso; o expurgo preserva ciphertext sob legal hold.
- Pedido, conclusão, cancelamento e download entram na auditoria sem conteúdo,
  caminho do arquivo ou chave idempotente.

## Contrato

Foram acrescentados catálogo de métricas, solicitação/consulta/cancelamento de
exportação, emissão de URL temporária e download autenticado. OpenAPI 3.1 e os
tipos TypeScript foram regenerados; o contrato tem 74 caminhos.

## Evidência executada

- suíte backend completa: **301 testes, 0 falhas, 0 erros, 0 ignorados**;
- frontend: build de produção e **155 testes verdes**;
- `api:generate` e `api:check`: verdes;
- instalação limpa V1→V28, atualização V8→V28 e V26→V28: verdes em
  PostgreSQL 17;
- ambiente local preservado e atualizado: V28 aplicada fora de ordem sobre o
  seed V900; backend Docker `healthy`, health `UP`;
- volume privado de exportações criado, modo não privilegiado e gravável pelo
  UID 10001.

## Decisões e limites

`ADR-0018` registra o desenho. O primeiro dataset contém apenas agregados e o
primeiro formato é CSV. Dado pessoal detalhado exige contrato de campos e
permissão própria. O storage atual é local à instância; escala horizontal exige
storage privado compartilhado.

## Próximo passo

Backend 22 — observabilidade e SLOs.
