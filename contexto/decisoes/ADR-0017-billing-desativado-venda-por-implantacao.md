# ADR-0017 — Billing desativado enquanto a venda é por implantação

- Status: aceito
- Data: 2026-08-15
- Contexto relacionado: [[ADR-0016]]

## Contexto

O Prompt 20 exige moeda, fechamento, arredondamento, tributo, carência, estorno
e provedor antes de implementar preço, fatura ou cobrança. Esses dados não são
detalhes técnicos: formam o contrato comercial e financeiro.

O modelo inicial de venda foi decidido como **implantação do CRM por projeto**.
Não haverá preço recorrente, cobrança por consumo nem integração financeira
automática nesta fase.

## Decisão

O billing operacional fica desativado e o Prompt 20 fica deliberadamente
adiado enquanto esse modelo comercial vigorar.

Não serão criados:

- preço ou regra de preço fictícios;
- moeda e calendário de fechamento implícitos;
- fatura de valor zero usada como placeholder;
- webhook ou adaptador para provedor ainda não escolhido;
- vínculo entre pagamento e permissão de usuário.

A fundação futura é a entregue pelo Backend 19: catálogo técnico, concessões
temporais e ledger reconciliável. Ela registra capacidade e uso sem afirmar que
uma métrica é faturável. A V28 de relatórios também não cria consumo comercial.

Ativar billing exigirá uma nova decisão que informe, no mínimo:

1. o que é vendido e qual métrica, se houver, é faturável;
2. moeda, timezone e regra de fechamento;
3. arredondamento, proporcionalidade, tributos, ajustes e estornos;
4. estados da fatura, carência e efeito de inadimplência;
5. provedor, assinatura de webhook, reconciliação e propriedade dos dados.

Só depois disso o Prompt 20 volta a `ready` e recebe schema executável. A nova
implementação deverá referenciar versões de `entitlement_grant` e
`usage_event`, sem reclassificar o histórico.

## Consequências

- A venda por implantação não fica acoplada a um subsistema de SaaS recorrente.
- O banco não carrega números ou regras que ninguém aprovou.
- Entitlements continuam separados de autorização e de navegação.
- Gate E trata billing como fora do escopo comercial atual, não como capacidade
  pronta para cobrança.
- Relatórios e exportações podem avançar porque dependem da medição e da
  autorização, não de preço ou pagamento.

## Alternativas descartadas

**Criar fatura de valor zero.** Pareceria progresso, mas fixaria estados,
fechamento e reconciliação sem contrato real.

**Escolher BRL e fechamento mensal por convenção.** BRL é adequado aos valores
do CRM atual, mas não autoriza usá-lo como política de cobrança.

**Usar status do pagamento como permissão.** Mistura contrato e identidade e
contraria a separação aceita na ADR-0016.

## Gatilhos de revisão

- início de cobrança recorrente, por licença ou por uso;
- escolha de um provedor financeiro;
- necessidade de emitir ou reconciliar faturas;
- contrato que imponha limite ou carência com efeito financeiro.
