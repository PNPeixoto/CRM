# Sessão 2026-08-10 — Fase 4, parte A: papéis personalizáveis

- Branch: `main`
- Ambiente: Windows 11, JDK 25.0.4, Docker Desktop, Testcontainers
- Suíte: **249 testes, 0 falhas** (eram 218)
- Migrations: **nenhuma** — o modelo da V10 bastou

## O que motivou

O item 6 do MVP foi entendido errado na primeira versão do plano: tratei como
recorte por unidade organizacional. É hierarquia de **papéis**, com cada pessoa
personalizando funções dentro do próprio privilégio.

O levantamento contra o código mostrou os dois lados. O modelo de dados está
completo desde a V10 e sob RLS — `app_role`, `role_permission` e
`membership_scope`, com vigência e exclusão lógica. Mas não havia **nenhuma
API**: `OrganizationController` tinha dois `GET`, e fora do serviço de resolução
nenhuma classe Java mencionava `app_role`. Os papéis citados (SDR, Closer,
Gestor, Gerente) não existiam em lugar nenhum; havia só `OWNER` e `ATTENDANT`,
semeados no profile de desenvolvimento.

## O que foi entregue

Seis rotas sob `ORGANIZATION_MANAGE` com alcance de tenant, e a guarda de
não escalonamento por cima de cada mutação.

`GuardaDeConcessao` aplica três regras:

1. **Conceder exige possuir.**
2. **Conceder exige alcance ao menos igual**, verificado por permissão.
3. **Editar exige conter.**

A terceira é a que costuma faltar, e é a que fecha o caminho sem atribuição:
editar um papel **já atribuído a outra pessoa** muda o privilégio dela sem que
nenhuma concessão nova aconteça.

Juntas produzem a propriedade que o teste enuncia:
`oPrivilegioDoTenantNaoCrescePorDelegacao`. Subconjunto de subconjunto continua
subconjunto.

`permissionScopes`, criado no Prompt 06 para o menu esconder o que não se pode
usar, serviu inteiro como base da guarda sem alteração.

## Decisões

**A regra de delegação é dita por extenso, não por ordinal.** `ScopeType` ordena
`NETWORK, TENANT, UNIT, TEAM, OWN`, e comparar `ordinal()` seria mais curto e
errado: os três do meio não decidem sobre registro de domínio (ADR-0008), então
quem só os possui não exerce autoridade e não pode delegá-la.

**A API não oferece `UNIT`.** O banco aceita, e a atribuição não concede nada.
Oferecer produziria papel que parece funcionar e falha em silêncio.

**Recusa não vai para a trilha.** Os endpoints são transacionais e a exceção
marca a transação para rollback: um `INSERT` de auditoria feito antes de lançar
seria descartado junto, deixando a aparência de rastro sem o rastro. Há teste
fixando isso — `aRecusaNaoFingeTerDeixadoRastro`.

Registrado em `ADR-0014`.

## Duas correções durante a execução

**Ciclo entre módulos, duas vezes.** `FronteiraDeModulosTest` acusou quatro
ciclos, e ambos vieram de dependência que parecia limpa:

- `PapelController` injetando `AuditTrail` — mas `audit` já depende de
  `organization.api`, pelo próprio controller de auditoria e pelo listener de
  negação. Corrigido invertendo a direção com o evento `PapelAlterado`, que é
  o caminho que `AcessoOrganizacionalNegado` já usava. Entrega síncrona, então
  a linha da trilha confirma na mesma transação da mudança.
- `PapelRepository` injetando `UsuarioLookup` — mas `identity` depende de
  `organization.api` para decidir MFA. Voltou para consulta nativa, que é o
  mesmo recurso de `DireitosDoTitularService` para atravessar módulo sem
  injetar repositório alheio.

Vale registrar: nos dois casos a versão "limpa" era a que quebrava, e o teste de
fronteira foi quem disse. Sem ele, os dois ciclos entrariam sem ninguém notar.

**Código duplicado devolvia 500.** O índice único parcial já impedia a
duplicata, mas a violação chegava ao handler global como falha não tratada. Um
erro de digitação trivial não deve produzir erro interno.

## Achado para o backlog

`PrivacidadeController.anonimizar` audita a recusa por legal hold **dentro** da
transação que será revertida, exatamente o padrão que este trabalho identificou
e evitou. O registro de `DENIED` é descartado no rollback: o pedido negado por
retenção legal não fica demonstrável, que é justamente o que o Prompt 18 exigia.

Não corrigido aqui para não misturar módulos numa entrega já fechada. Precisa de
transação própria para a trilha, ou de inversão por evento como a que foi feita
em `organization`.

## Parte B — alcance de equipe (V25)

O alcance intermediário virou **equipe**, não unidade. `contact`, `deal` e
`task` já carregam `owner_user_id` com índice desde a V5, então o recorte é
filtro sobre coluna existente: nenhuma coluna nova em tabela de domínio, nenhum
backfill inventado, e nenhum conflito com o ADR-0008 — que trata de unidade e
continua valendo. Registrado em `ADR-0015`.

`team_member` guarda a composição com vigência, e o runtime não tem `DELETE`
nem `UPDATE` livre: encerrar é `valid_until`. O período em que alguém respondeu
a um gestor explica acessos já gravados na trilha.

`ScopeType.TEAM` existia no enum desde a V10; a V25 só ampliou o `CHECK` de
`membership_scope`.

**O recorte é resolvido num lugar só.** `Autorizacao.recorteDe` devolve
`Recorte(todoOTenant, responsaveis)`, e as três listagens passaram de
`UUID responsavelId` para `boolean irrestrito, Collection<UUID> responsaveis`,
sempre aplicado **na consulta**. Devolver só o alcance e deixar cada controller
traduzir faria a mesma regra existir em três módulos.

Um nível, sem recursão — cobre Gerente sobre SDR e Closer. Ciclos não travam a
consulta por isso, mas o ciclo de dois é recusado assim mesmo: produz duas
pessoas se enxergando sem que nenhuma seja gestora de fato.

## Tela

`/acessos` (antes `/equipes`, `EmProducao`) com três abas: Papéis, Pessoas e
Equipes. Nove testes de componente.

A tela desabilita o que o backend recusaria — permissão não delegável, papel de
sistema, papel acima do privilégio, papel em uso — e **nada disso é a
proteção**: cada botão bate num endpoint que decide sozinho, e há teste no
backend provando a recusa.

Três avisos que a tela dá porque ninguém descobriria sozinho:

- revogação vale na próxima ação, mas a sessão aberta leva até 15 minutos;
- papel de equipe sem composição enxerga só o próprio responsável;
- papel que concede além do seu privilégio aparece, sem botão, com o motivo.

## Revisão — dois defeitos, mesma família

Ambos eram a tela prometendo o que o servidor recusa.

**Contradição interna.** A listagem marcava `gerenciavel` por possuir a
permissão, e `criar`/`definirPermissoes` exigiam alcance de tenant. Um
administrador com `organization.manage` no tenant e `contacts.read` só no
próprio via "Editar" habilitado e levava 422 ao salvar sem ter mudado nada.

A fronteira foi para onde está de fato: **definir papel exige possuir; atribuir
exige o alcance**. O papel não concede nada até ser atribuído, e é na atribuição
que o privilégio é conferido. Não abre escalonamento — quem só tem a permissão
sob alcance próprio segue só conseguindo atribuir sob alcance próprio.

**Seletor de alcance oferecia o impossível.** Faltava `delegavelNaEquipe` no
catálogo. A tela passou a calcular por papel quais alcances pode oferecer.

## Aplicação da V25 — 2026-08-10

| Verificação | Resultado |
|---|---|
| Comandos destrutivos | `REVOKE` de privilégio e 2 `DROP CONSTRAINT` **seguidos de recriação mais ampla**; nenhum `DELETE`/`DROP TABLE` |
| Backup | 16 MB antes de tudo |
| Prova em contêiner descartável | V25 subiu limpa sobre cópia restaurada; dados intactos |
| RLS `team_member` | `true/true` |
| Privilégios do runtime | `INSERT, SELECT`; `DELETE` **recusado de fato** |
| `UPDATE` por coluna | `valid_until, updated_at, updated_by` |
| Banco após deploy | flyway em 25, contadores **idênticos** à linha de base |
| App | healthy, readiness UP, zero erro |
| Rota nova sem token | 401 |

## Verificação na interface

Fluxo completo exercitado no navegador, contra o backend real:

1. Papel `GESTOR` criado com três permissões — `OWNER` apareceu com "Editar" e
   "Remover" desabilitados e o motivo no `title`.
2. Atribuído à atendente com alcance **A equipe dele**; a dica apareceu sozinha:
   *"Monte a equipe na aba Equipes"*.
3. Equipe montada — o seletor de liderado **já excluía a própria gestora**.

No banco: papel com 3 permissões, atribuição `TEAM`, composição vigente. Na
trilha, os três eventos com o motivo certo — `ROLE_CREATED`,
`ROLE_ASSIGNMENT_CHANGED`, `TEAM_MEMBERSHIP_CHANGED`. Todas as chamadas da tela
em 200/204.

**Um 422 no console não era defeito**: `MFA_NECESSARIO`, do login do operador,
com MFA obrigatório para `OWNER`. Conferido no log em vez de presumido.

O recorte em si — gestor enxergando o time e não enxergando quem está fora —
continua provado pelos 16 casos de `AlcanceDeEquipeTest`, não pelo navegador:
não há sessão da atendente, e eu não digito senha.

## Erro cometido nesta sessão

Um `git add -A` levou o dump de 16 MB para dentro de um commit. Dump contém
conteúdo de mensagem, dado de contato e hash de senha — exatamente o que a
seção de segurança documental do `CLAUDE.md` proíbe versionar.

Corrigido no mesmo minuto: removido do commit, `var/backups/` acrescentado ao
`.gitignore`, reflog expirado e `gc` executado. Zero objetos com o dump
restaram na história. O commit nunca saiu da máquina.

## Pendências da Fase 4

- **Preset de papéis.** SDR, Closer, Atendente, Gestor e Gerente como papéis
  comuns editáveis — não `system_role`. Hoje o cliente cria os dele do zero.
- **Hierarquia de mais de um nível**, se a operação passar a exigir.
- **Dados de demonstração no banco local**: o papel `GESTOR`, a atribuição e a
  equipe criados na verificação continuam lá.
