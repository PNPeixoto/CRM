# Sessão 2026-08-15 — Backend 20: billing adiado

- Branch: `main`
- Baseline: working tree após Backend 19
- Objetivo: avaliar o Prompt 20 sem inventar um modelo comercial

## Decisão

O produto começará vendendo a implantação do CRM, não assinatura recorrente,
licença ou consumo. Portanto, billing operacional fica fora do escopo atual.

O Prompt 20 foi marcado como `deferred`, não como concluído. Nenhum preço,
moeda de cobrança, calendário de fechamento, imposto, fatura de valor zero,
provedor financeiro ou webhook fictício foi criado.

A fundação futura permanece na V27: catálogo técnico, concessões temporais e
ledger reconciliável, sem afirmar que qualquer métrica é faturável. Billing só
volta a `ready` depois de uma decisão comercial que defina preço, moeda,
fechamento, arredondamento, tributo, carência, estorno e provedor.

## Evidência

- decisão registrada em `ADR-0017`;
- manifesto canônico registra Backend 20 como `deferred`;
- V28 declara explicitamente que relatório e métrica não ativam cobrança;
- nenhuma tabela de preço, fatura, pagamento ou webhook financeiro foi criada.

## Próximo passo relacionado

Reabrir o Prompt 20 somente quando o modelo recorrente ou por consumo existir.
Até lá, venda, contrato e recebimento da implantação ficam fora do runtime do
CRM.
