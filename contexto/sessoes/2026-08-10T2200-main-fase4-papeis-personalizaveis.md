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

## Pendências da Fase 4

- **Parte B — alcance de equipe.** Migration `team_member`, `Alcance.EQUIPE` e
  recorte nas listagens. É o "gerente vê o que a equipe dele faz", hoje
  inexistente: só há `TENANT` e `PROPRIO`.
- **Interface.** `/equipes` continua `EmProducao`. As rotas existem e estão no
  contrato TypeScript gerado.
- **Preset de papéis.** SDR, Closer, Atendente, Gestor e Gerente como papéis
  comuns editáveis — não `system_role`.
- **Revogação não é instantânea.** Vale na chamada seguinte à API, mas a sessão
  já emitida carrega o token por até 15 minutos. A tela precisa dizer isso.
