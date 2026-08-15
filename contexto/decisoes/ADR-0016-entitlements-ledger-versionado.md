# ADR-0016 — Entitlements separados de autorização com ledger versionado

- Status: aceito
- Data: 2026-08-15
- Contexto relacionado: [[ADR-0008]], [[ADR-0013]]

## Contexto

O backend já media mensagens e bytes em `usage_event` desde V15/V17. A tabela
era append-only e idempotente, mas ainda não respondia:

- qual capacidade técnica produziu a métrica;
- qual versão do contrato valia quando o uso ocorreu;
- qual origem compõe um agregado;
- qual janela e timezone governam um limite;
- como impedir duas chamadas concorrentes de ultrapassar um limite hard.

Também havia um risco conceitual: usar contratação como permissão, ou esconder
um item de menu e tratar isso como proteção. Os quatro sinais têm autoridades e
ciclos de vida diferentes.

## Decisão

### Quatro eixos permanecem separados

| Eixo | Autoridade | Efeito |
|---|---|---|
| Capacidade técnica | catálogo do release/backend | diz se o módulo existe e está disponível |
| Entitlement | concessão contratual do tenant | diz se a capacidade foi contratada |
| Permissão | `organization`/autorização | diz se o usuário pode executar a ação |
| Visibilidade | apresentação/navegação | decide somente o que a interface mostra |

`EntitlementService` publica somente os dois primeiros. Um módulo consumidor
continua obrigado a verificar permissão e escopo; a UI continua sem autoridade
de segurança.

### Catálogo técnico não contém política comercial

`technical_capability` e `usage_metric` são catálogo do build. Mudam por
migration/release e o runtime só consulta. As três métricas semeadas são as que
o código já produzia antes da V27; o seed não as torna faturáveis e não concede
nada a tenant algum.

Nova semântica de métrica recebe novo código. Reatribuir um código existente a
outra capacidade faria o histórico mudar de significado e não é permitido.

### Concessão é snapshot imutável e temporal

`entitlement_grant` guarda tenant, capacidade, versão, referência contratual,
vigência, janela, timezone IANA, limites soft/hard e término da carência. Duas
versões da mesma capacidade não podem se sobrepor.

Mudança de plano insere outra versão. `UPDATE` e `DELETE` são revogados do
runtime e um gatilho também recusa reescrita. Evento atrasado procura a versão
vigente em `occurred_at`, não a versão corrente no instante da ingestão.

Nenhuma concessão, limite, quantidade, preço ou prazo comercial nasce por
default. Configuração ausente falha como `NOT_CONTRACTED`.

### O ledger continua sendo a verdade

V27 amplia `usage_event` com `source_type`, `source_id` e
`entitlement_grant_id`. O agregado soma eventos da concessão, métrica e janela;
nenhum contador mutável substitui a evidência.

Produtores de mensagem e mídia continuam medindo mesmo sem concessão. Quando
existe contrato no instante da ocorrência, gravam o snapshot da versão. Isso
preserva medição operacional sem inventar política comercial.

Retenção continua sendo exceção controlada ao append-only: o runtime não tem
`INSERT`, `UPDATE` ou `DELETE` direto, e o descarte só acontece pela função de
retenção, com corte explícito e legal hold.

### Limite hard usa a soma do ledger sob trava transacional

`registrar_evento_de_uso` primeiro serializa a chave idempotente e depois a
combinação tenant + concessão + métrica + janela. Dentro da mesma seção crítica
ela soma o ledger, aplica o limite e, se permitido, insere o evento.

Não existe reserva em contador paralelo para reconciliar depois. A
serialização pode reduzir concorrência numa única cota, mas mantém uma fonte de
verdade e evita oversell por corrida.

O resultado diferencia:

- `MODULE_UNAVAILABLE` e `NOT_CONTRACTED`, sem inserir uso;
- `RECORDED` e `REPLAY`;
- `SOFT_LIMIT_EXCEEDED`, que mede e sinaliza;
- `HARD_LIMIT_GRACE`, que mede durante a carência explícita;
- `HARD_LIMIT_EXCEEDED`, que recusa atomicamente.

Produtor que mede **depois** de executar uma ação não pode usar hard limit como
preflight. Quem precisar bloquear deve chamar a porta antes do efeito externo;
medição posterior não apaga fato ocorrido.

### Janela e timezone são dados do contrato

A infraestrutura suporta dia civil, mês civil e termo contratual. O timezone é
IANA e obrigatório; nenhum fuso implícito do servidor ou navegador participa.
Janela nova ou regra de fechamento diferente exige decisão própria, não um
`if` escondido no cálculo.

## Consequências

- Billing e relatórios podem reconciliar agregado → eventos → fonte sem copiar
  política de medição.
- Plano novo não reclassifica consumo antigo.
- Contratação não libera endpoint e permissão não cria contratação.
- O caminho de hard limit é seguro sob concorrência, mas deve ser integrado
  explicitamente antes do efeito que se deseja limitar.
- O catálogo inicial cobre apenas as métricas já existentes. Novas capacidades
  entram quando um produtor real e seu contrato técnico forem implementados.
- Prompt 20 continua bloqueado até decisões de moeda, fechamento,
  arredondamento, tributo, estorno e provedor; V27 não antecipa nenhuma delas.

## Alternativas descartadas

**Contador de consumo atualizado a cada chamada.** Mais barato para consultar,
mas vira uma segunda verdade que precisa ser reconciliada com eventos e perde a
origem do total.

**Guardar somente o plano atual no tenant.** Evento atrasado seria calculado
pela política errada e troca de plano reescreveria o significado do histórico.

**Usar pagamento ou entitlement como papel.** Mistura contrato com identidade,
impede delegação correta e torna status financeiro uma permissão de usuário.

**Aplicar hard limit nos gatilhos posteriores de mensagem/mídia.** Nesse ponto
o efeito já ocorreu; recusar o evento esconderia consumo real em vez de
impedir a ação.

## Gatilhos de revisão

- necessidade comprovada de projeção materializada por volume;
- nova janela além de dia, mês ou termo;
- criação de um plano de controle separado para administrar contratos;
- definição comercial necessária ao Prompt 20.
