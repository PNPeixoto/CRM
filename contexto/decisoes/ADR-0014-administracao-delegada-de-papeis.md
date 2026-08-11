# ADR-0014 — Administração delegada de papéis com invariante de não escalonamento

- Status: aceito
- Data: 2026-08-10
- Contexto relacionado: [[ADR-0006]], [[ADR-0008]], [[ADR-0013]]

## Contexto

O modelo de papéis existe desde a V10 e nunca teve API. `app_role`,
`role_permission` e `membership_scope` estão sob RLS, com vigência e exclusão
lógica, mas os únicos papéis existentes eram semeados no profile de
desenvolvimento: `OWNER` e `ATTENDANT`. O cliente não conseguia criar um papel,
renomear, nem conceder permissão — nem pela interface, nem por endpoint.

O MVP comercial pede que cada empresa monte a própria hierarquia (Atendente,
SDR, Closer, Gestor, Gerente) e que cada pessoa personalize funções **dentro do
que o próprio privilégio permite**.

## Decisão

Expor a administração de papéis sob `ORGANIZATION_MANAGE` com alcance de
tenant, e submeter **toda mutação** a uma invariante de não escalonamento.

### A invariante

Três regras, verificadas em `GuardaDeConcessao`:

1. **Conceder exige possuir.** As permissões concedidas precisam estar entre as
   do autor.
2. **Conceder exige alcance ao menos igual**, verificado **por permissão**. Ter
   algo em `TENANT` não autoriza conceder em `TENANT` uma permissão que se
   possui apenas em `OWN`.
3. **Editar exige conter.** Só se mexe em papel cujas permissões já são
   subconjunto das do autor.

A terceira não é redundante: editar um papel **já atribuído a outras pessoas**
altera o privilégio delas sem que nenhuma atribuição nova aconteça, e a guarda
de atribuição, sozinha, não vê esse caminho.

Juntas produzem a propriedade que o teste enuncia: **o conjunto de privilégios
do tenant nunca cresce por delegação**. Subconjunto de subconjunto continua
subconjunto, então quem entra depois nunca excede quem concedeu.

### A delegação é dita por extenso, não por ordinal

`ScopeType` ordena `NETWORK, TENANT, UNIT, TEAM, OWN`. Comparar `ordinal()`
pareceria mais curto e estaria errado: `UNIT`, `TEAM` e `NETWORK` ficam no meio
da ordem mas **não decidem sobre registro de domínio** (ADR-0008). Quem só os
possui não exerce autoridade nenhuma e, portanto, não pode delegar autoridade
real a terceiros. A regra é uma tabela explícita, e falha fechada.

### A API não oferece alcance de unidade

`membership_scope` aceita `UNIT`, e uma atribuição `UNIT` é gravada sem erro e
**não concede nada**. Oferecê-la na tela produziria papel que parece funcionar e
falha em silêncio — pior que a ausência da opção. A API aceita apenas `TENANT`
e `OWN`; `UNIT` volta a ser discutida quando houver alcance intermediário com
persistência.

### Quatro recusas

1. Papel de sistema (`system_role`) é imutável — `OWNER` é a saída de
   emergência do tenant.
2. A última atribuição viva de papel de sistema não é revogada: sem ela o
   tenant fica sem ninguém capaz de administrar, e a saída passa a exigir acesso
   direto ao banco.
3. Papel com atribuição viva não é removido em cascata.
4. Código de papel duplicado é recusado com mensagem, não com 500 do índice
   único.

### Recusa não entra na trilha de auditoria

Os endpoints são transacionais e a exceção de domínio marca a transação para
rollback: um `INSERT` de auditoria feito antes de lançar seria descartado junto,
deixando **a aparência de rastro sem o rastro**. Como recusa também não é
mudança de privilégio, ela fica no log da aplicação. A trilha só recebe o que
sobrevive ao commit.

## Consequências

- O cliente monta a própria hierarquia sem intervenção de suporte.
- `organization.manage` passa a significar "administrar dentro do que eu tenho",
  e deixa de ser um caminho para se tornar qualquer coisa.
- **Revogação não é instantânea.** O JWT vive 15 minutos e carrega o contexto;
  a decisão relê o membership a cada requisição, então a revogação vale na
  chamada seguinte à API — mas uma sessão já emitida continua com o token
  antigo até expirar. A interface precisa dizer isso, e não sugerir efeito
  imediato. Vale também o `SEC-011`, sobre assinatura de WebSocket que
  sobrevive à revogação.
- Nenhuma migration: o modelo da V10 bastou.

## Alternativas descartadas

**Alcance no papel, em vez de na atribuição.** Simplificaria a tela e obrigaria
a duplicar cada papel por alcance — "Closer (tenant)" e "Closer (próprio)". O
modelo da V10 já põe o alcance na atribuição, e mudá-lo seria migration
destrutiva para resolver um problema de formulário.

**Hierarquia numérica entre papéis** (nível 1 a 5, quem tem nível maior manda
em quem tem menor). Parece intuitivo e não corresponde a nada: dois papéis
podem ter poderes incomparáveis, e um número obriga a ordenar o que não é
ordenável. O subconjunto de permissões é a ordem real, e já existe.
