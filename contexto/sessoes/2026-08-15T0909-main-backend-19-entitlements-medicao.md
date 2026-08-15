# Sessão 2026-08-15 — Backend 19, entitlements e medição

- Branch: `main`
- Ambiente: Windows 11, JDK 25.0.4, Docker Desktop, PostgreSQL 17 via Testcontainers
- Decisão: `ADR-0016`

## Resultado

O ledger existente foi evoluído sem criar plano, preço, concessão automática ou
regra faturável. V27 adiciona catálogo técnico, concessões contratuais
versionadas, fonte reconciliável e uma operação atômica para hard limit.

`EntitlementService` é a porta pública do módulo `billing`. Ela avalia somente
capacidade técnica e contratação; permissão/escopo continuam no módulo de
autorização e navegação continua sendo apresentação.

## Contratos implementados

- `technical_capability` e `usage_metric`: catálogo técnico somente leitura no
  runtime;
- `entitlement_grant`: tenant-scoped, RLS forçado, vigência sem sobreposição e
  versões imutáveis;
- `usage_event`: fonte, concessão aplicada e métrica catalogada, sem contador
  mutável paralelo;
- janela explícita `CALENDAR_DAY`, `CALENDAR_MONTH` ou `CONTRACT_TERM`, sempre
  com timezone IANA informado pelo contrato;
- resultados distintos para indisponibilidade técnica, não contratação, soft,
  hard, carência e replay;
- registro com travas transacionais por chave idempotente e por cota/janela;
- agregação e listagem das fontes para reconciliação.

Os produtores existentes de mensagem e mídia continuam medindo sem contrato e
passam a anexar a versão vigente quando ela existe. Hard limit só é aplicado na
porta de preflight; não se descarta medição de um efeito que já aconteceu.

## Correção transversal

O runtime perdeu `INSERT`, `UPDATE` e `DELETE` direto sobre `usage_event`.
Triggers e porta validada escrevem com funções estreitas. A exceção de retenção
da V23 agora usa um modo controlado que respeita legal hold; fora dele o gatilho
append-only continua recusando exclusão.

## Evidências

- compilação Java 25: passou;
- `EntitlementsMedicaoTest`: 6/6;
- banco/segurança/retenção/arquitetura/configuração: 20/20;
- instalação limpa V1→V27: passou em PostgreSQL 17;
- atualização V8→V27: passou, 19 migrations aplicadas;
- atualização de V26 com ledger já populado: passou sem perder eventos;
- seeds de desenvolvimento: passaram sem conceder entitlement comercial.
- suíte backend completa: **296 testes, 0 falhas, 0 erros, 0 ignorados**;
- ambiente local atualizado: V27 aplicada e backend Docker `healthy`.

## Pendências deliberadas

- Nenhum tenant recebeu concessão por default.
- Métrica faturável, preço, moeda, fechamento, arredondamento, tributo, estorno
  e provedor não foram decididos nem implementados.
- O Prompt 20 só pode avançar depois dessas decisões comerciais/contratuais.
