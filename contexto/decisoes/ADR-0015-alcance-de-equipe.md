# ADR-0015 — Alcance de equipe em vez de alcance de unidade

- Status: aceito
- Data: 2026-08-10
- Substitui parcialmente: [[ADR-0008]]
- Contexto relacionado: [[ADR-0014]], [[ADR-0006]]

## Contexto

Havia dois alcances úteis: `TENANT` (vê tudo) e `OWN` (vê o próprio). Faltava o
meio — *"o gestor enxerga o que a equipe dele faz"* —, que é o que uma
hierarquia com Gestor e Gerente pede.

`membership_scope` já aceitava `UNIT`, e o ADR-0008 registrou que ele **falha
fechado**: nenhuma tabela de domínio declara unidade, então uma atribuição
`UNIT` é gravada sem erro e não concede nada.

## Decisão

Implementar o alcance intermediário como **equipe** (subordinação), não como
unidade organizacional.

### Por quê

`contact`, `deal` e `task` já carregam `owner_user_id` com FK composta e índice
por responsável desde a V5. O recorte por equipe é, portanto, filtro sobre
coluna existente:

| | Unidade (`unit_id`) | Equipe (escolhida) |
|---|---|---|
| Colunas novas em tabela de domínio | 4, com backfill sem resposta | **nenhuma** |
| Recorte | `unit_id = :unidade` | `owner_user_id IN :responsaveis` |
| Casa com | franquia, filial, geografia | **cargo, gestão de pessoas** |

O backfill era o problema real da alternativa: não há como saber a unidade de um
registro criado antes de a coluna existir, e inventá-la a partir do dono atual
seria inventar história.

**Isto não revoga o ADR-0008**, que trata de unidade. `UNIT` continua falhando
fechado e continua fora da API.

### Modelo

`team_member` (V25) guarda a composição com vigência. `valid_until` encerra; o
runtime **não tem `DELETE` nem `UPDATE` livre** na tabela, só sobre
`valid_until`. O período em que alguém respondeu a um gestor explica acessos já
registrados na trilha, e apagar a linha apagaria a justificativa de um evento
antigo.

`ScopeType.TEAM` já existia no enum desde a V10; a V25 apenas ampliou o `CHECK`
de `membership_scope` para aceitá-lo.

### Um nível, sem recursão

`equipeDe` resolve os liderados diretos mais o próprio usuário. Gerente de
gestores exigiria `WITH RECURSIVE` e guarda de ciclo, e o desenho atual cobre
Gerente sobre SDR e Closer, que é o caso real. A tabela suporta a extensão sem
migration; a mudança seria só na resolução.

Como a resolução é de um nível, ciclos não travam a consulta. Ainda assim o
ciclo de dois é recusado: ele produz duas pessoas se enxergando sem que nenhuma
seja gestora de fato, o que ninguém explica olhando o organograma.

### O próprio usuário sempre entra na equipe

Um gestor que enxergasse o time e não a própria carteira seria um recorte que
ninguém pediu, e a primeira listagem denunciaria.

### O recorte é resolvido num lugar só

`Autorizacao.recorteDe` devolve `Recorte(todoOTenant, responsaveis)`, e cada
listagem o aplica **na própria consulta**. As três listagens de domínio passaram
de `UUID responsavelId` para `boolean irrestrito, Collection<UUID> responsaveis`.

Devolver só o alcance e deixar cada controller traduzir faria a mesma regra ser
reescrita em três módulos — que é como duas delas passam a divergir.

### Delegação: o alcance é relativo

`GuardaDeConcessao` passou a aceitar `TENANT → {TENANT, TEAM, OWN}` e
`TEAM → {TEAM, OWN}`. Conceder `TEAM` a alguém não entrega a *minha* equipe:
entrega a dele. A invariante do ADR-0014 é sobre o **tipo** de recorte — nunca
conceder um mais amplo que o próprio —, e não sobre um conjunto absoluto de ids,
que mudaria a cada contratação.

## Consequências

- A hierarquia comercial passa a ser expressável sem dar o tenant inteiro a
  ninguém.
- Papel com alcance de equipe **sem composição montada** enxerga apenas o
  próprio responsável. É falha fechada e é correto, mas ninguém descobre
  sozinho: a tela avisa.
- `unit_id` continua sem existir nas tabelas de domínio. Quem precisar de
  recorte por filial precisará de um ADR novo e da migration correspondente.
- V25 é aditiva. Os dois `CHECK` de `membership_scope` foram recriados para
  aceitar `TEAM`; nenhuma linha existente muda de significado.

## Alternativas descartadas

**Hierarquia recursiva desde já.** Custa `WITH RECURSIVE`, guarda de ciclo e
uma consulta bem mais cara, para um caso que a operação ainda não tem.

**Campo `gestor_id` em `app_user`.** Mais simples e errado: uma pessoa passa a
ter no máximo um gestor, e mudar de gestor apagaria o histórico do vínculo
anterior — que é justamente o que explica acessos passados.
