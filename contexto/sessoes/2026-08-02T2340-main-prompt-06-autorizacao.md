# Sessão 2026-08-02 — Prompt 06, autorização e multi-tenancy

- Branch: `main`
- Commit base: `793777d`
- Ambiente: Windows 11, JDK Temurin 25.0.4, Docker Desktop ativo, Testcontainers
  (postgres:17-alpine, redis:7-alpine)
- Responsável: PNPeixoto, com Claude Code

## O que foi feito

Autorização por ação, escopo e registro, em ponto único, aplicada a todos os
controllers de domínio e à inscrição em tópico de tempo real.

### Decisão central

`Autorizacao` decide dois alcances, `TENANT` e `PROPRIO`. `UNIT` não decide
sobre registro de domínio porque nenhuma tabela de domínio declara unidade —
registrado em `ADR-0008`, com o caminho de revisão (migration aditiva de
`unit_id` mais regra de backfill).

### Defeito encontrado e corrigido durante a execução

`AuthorizedContext.scopes()` sempre contém o próprio tipo do contexto, porque o
`ContextBuilder` semeia o conjunto com ele. A primeira versão do
`AutorizacaoService` decidia o alcance por `scopes().contains(TENANT)` no
contexto de tenant, o que era **sempre verdadeiro**: `Alcance.PROPRIO` era
inalcançável e um papel de alcance próprio dava acesso a todo o tenant.

A correção não foi mexer no significado de `scopes()`, usado pela troca de
unidade. Foi acrescentar `OrganizationAccess.permissionScopes`, que devolve
permissão → escopo mais amplo que a concede. O conjunto achatado de permissões
responde "o que a pessoa pode fazer" e perde "sob qual alcance" — e é
exatamente essa segunda resposta que a decisão precisa.

### Outros dois defeitos corrigidos no caminho

1. `ContactRepository.buscar` quebrava com `busca` ausente: o parâmetro nulo era
   tipado como `bytea` pelo Postgres e `lower(bytea)` não existe. Era o caminho
   padrão da listagem de contatos. Resolvido com `CAST(:termo AS string)`.
2. Sob alcance próprio, criar registro sem informar responsável o deixava órfão
   e portanto ilegível para o próprio autor — a permissão de escrita não
   concedia nada de útil. `Autorizacao.responsavelPadrao` faz o autor virar
   responsável nesse alcance, e preserva o comportamento atual sob `TENANT`,
   onde fila sem dono é legítima.

### Alcance da aplicação

- Contato, tarefa, oportunidade: permissão na ação e responsável no registro,
  com o recorte de alcance próprio **dentro da consulta**, não sobre a página.
- Atualização verifica antes e depois de aplicar: antes impede editar registro
  alheio, depois impede transferir o próprio para fora do alcance.
- Canal: só permissão; canal não tem responsável.
- Relatório consolidado: exige `reports.read` **e** alcance de tenant.
- Conversa: caixa de entrada compartilhada por desenho, sem recorte por
  responsável — a fila só funciona se quem está livre enxergar o que não tem
  dono. A permissão continua exigida.
- WebSocket: o SUBSCRIBE passou a revalidar permissão, não apenas o tenant do
  destino. Antes, qualquer usuário autenticado do tenant recebia mensagem de
  cliente em tempo real mesmo sem acesso à caixa de entrada. A porta
  `AutorizacaoDeEscuta` mora em `shared.api` e é implementada em
  `organization.internal`, para não criar ciclo entre os módulos.

### Itens do prompt que já estavam satisfeitos

- Mass assignment: todo `@RequestBody` é record com campos explícitos e
  `spring.jackson.deserialization.fail-on-unknown-properties: true`. O único
  corpo cru é o webhook do Telegram, que precisa do texto original para
  conferir assinatura.
- Log de negação registra permissão e usuário e **não** o id do recurso alvo,
  para o log não virar inventário do que existe.

### Novo endpoint

`GET /api/organizacao/permissoes` devolve permissão → alcance, filtrado pelo
catálogo `Permissao`. Serve à separação entre visibilidade de menu e permissão:
a interface esconde o que não se usa sem que esconder seja a proteção. Snapshot
OpenAPI regenerado.

### Seed de desenvolvimento

`R__organizacao_desenvolvimento.sql` ganhou `conversations.read` e
`conversations.write` no papel `OWNER` e um papel `ATTENDANT` de alcance `OWN`
no tenant `pnp`, para existir caso negativo real fora do teste.

## Evidência

| item | valor |
| --- | --- |
| comando | `./mvnw -o test` |
| resultado | Tests run: 96, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| testes novos | `AutorizacaoPorAlcanceTest` (10), `EscutaDeTopicoTest` (4) |
| runtime de banco | `crm_runtime_test`, sem `BYPASSRLS` |

Cinco testes existentes passaram a falhar com 403 ao aplicar a autorização —
todos porque o cenário criava usuário sem membership vigente. Foram corrigidos
concedendo permissão explícita no `setUp`, com comentário dizendo o que cada um
mede. Nenhuma verificação foi afrouxada para acomodar o teste.

## Pendências deixadas em aberto

- Alcance por unidade depende de `unit_id` nas tabelas de domínio (ADR-0008).
- Exportação e job ainda não existem como superfície; a revalidação exigida pelo
  prompt para eles é herdada por `Autorizacao` quando forem criados, e o Prompt
  21 é o lugar de comprová-la.
- A interface ainda não consome `/api/organizacao/permissoes`; o menu segue como
  está até a trilha de frontend tratá-lo.

## Revisão corretiva posterior

Uma revisão em 2026-08-03 encontrou lacunas que tornam algumas afirmações desta
sessão históricas e não mais suficientes como evidência: a apresentação do
tenant não estava protegida; canais, conversas e tópicos aceitavam `OWN` apesar
de serem coletivos; referências relacionadas não revalidavam o alcance; e a
criação preguiçosa do funil permitia escrita com `deals.read`.

Esses pontos foram corrigidos e cobertos por testes adicionais. A evidência de
96 testes desta sessão foi substituída pela suíte integral de 112 testes. O
registro canônico da correção é
`2026-08-03T0025-main-revisao-corretiva-prompt-06.md`.
