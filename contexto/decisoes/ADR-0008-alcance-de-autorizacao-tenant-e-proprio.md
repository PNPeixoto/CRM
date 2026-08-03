# ADR-0008 — Alcance de autorização limitado a tenant e próprio registro

- Status: accepted
- Data: 2026-08-02

## Contexto

O ADR-0006 persistiu três alcances em `membership_scope`: `TENANT`, `UNIT` e
`OWN`. A camada de autorização de aplicação está sendo escrita agora, e a
pergunta é qual desses três ela consegue realmente decidir.

Uma decisão de autorização por unidade precisa responder "a qual unidade este
registro pertence". Hoje nenhuma tabela de domínio responde isso. `contact`,
`deal`, `task` e `conversation` carregam `owner_user_id` ou `assigned_user_id`;
nenhuma carrega `unit_id`. A unidade existe apenas do lado da pessoa, em
`membership_scope.unit_id` — ou seja, sabemos em que unidade alguém trabalha e
não sabemos em que unidade o dado nasceu.

Implementar `UNIT` mesmo assim exigiria inferir a unidade do registro pela
unidade de quem o criou, resolvida no momento da leitura. Isso muda a resposta
quando a pessoa é transferida: registros que ela criou na unidade antiga
migrariam sozinhos para a nova, retroativamente, sem nenhum evento que explique
a mudança. Autorização que reescreve o passado não é auditável.

## Decisão

A `Autorizacao` decide apenas dois alcances, expostos no enum `Alcance`:

- `TENANT` — todo registro do tenant corrente;
- `PROPRIO` — somente registros cujo responsável é o usuário corrente.

`UNIT` não é traduzido para um alcance de aplicação. Uma atribuição
`scope_type = 'UNIT'` continua sendo persistida e lida pelo
`OrganizationAccessService` — ela já governa quais unidades a pessoa pode
ativar —, mas não concede leitura ou escrita sobre registros de domínio: o
`AutorizacaoService` a trata como ausência de permissão, e a decisão falha
fechada.

O enum `Alcance` não declara `UNIT`. Uma constante sem implementação seria pior
que a ausência: convidaria a `switch` com ramo vazio e a testes que passam sem
exercer nada.

## Alternativas descartadas

- **Inferir a unidade do registro pela unidade atual do criador**: a resposta
  muda quando a pessoa é transferida, reescrevendo retroativamente quem podia
  ver o quê. Também depende de uma consulta a `membership_scope` por registro
  avaliado.
- **Adicionar `unit_id` às tabelas de domínio agora**: é migration em quatro
  tabelas mais backfill de dados existentes, dentro de uma fase cujo objeto é
  autorização e não modelagem. Backfill improvisado escolheria a unidade errada
  para todo registro anterior à coluna, e migration aplicada é imutável.
- **Tratar `UNIT` como `TENANT` provisoriamente**: amplia silenciosamente o
  alcance de quem foi deliberadamente restrito a uma unidade. Falha aberta, e o
  erro só aparece como vazamento.
- **Tratar `UNIT` como `PROPRIO` provisoriamente**: falha fechada, porém mente
  sobre a regra — um gerente de unidade veria apenas os próprios registros e o
  time trataria isso como bug, não como decisão.

## Consequências e revisão

Enquanto este ADR vigorar, um papel com escopo `UNIT` é inútil para dado de
domínio, e o seed de desenvolvimento não cria nenhum. Quem precisar de recorte
por unidade recebe hoje `TENANT` — alcance maior que o desejado — e isso deve
ser explicitado a quem configurar papéis.

Recursos coletivos que não possuem responsável — como canais, caixa de entrada
compartilhada, relatórios consolidados e os tópicos de tempo real atuais —
exigem `TENANT`. Uma concessão `OWN` não é promovida para viabilizar essas
superfícies. Quando o mesmo papel recebe `OWN` e um alcance ainda não suportado
para a mesma permissão, `OWN` continua sendo o alcance efetivo suportado; apenas
`UNIT` permanece insuficiente e falha fechado.

O seed de desenvolvimento materializa essa separação: `ATTENDANT` usa `OWN`
para registros com responsável e `ATTENDANT_SHARED` usa `TENANT` somente para
a caixa compartilhada. Isso evita transformar conveniência operacional em
ampliação silenciosa de acesso.

O caminho de revisão é uma migration aditiva que dê `unit_id` a `contact`,
`deal`, `task` e `conversation`, com origem definida no momento da criação e
imutável depois, mais a regra de backfill do acervo existente. Feito isso, um
novo ADR referencia este e acrescenta `UNIDADE` ao enum `Alcance`.

## Evidências

`Permissao`, `Autorizacao`, `AutorizacaoService`, V10 (`membership_scope`),
ADR-0006 e o papel `ATTENDANT` de alcance `OWN` em
`db/dev/R__organizacao_desenvolvimento.sql`.
