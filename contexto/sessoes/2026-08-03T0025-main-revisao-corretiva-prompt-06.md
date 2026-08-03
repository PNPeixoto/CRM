# Sessão 2026-08-03 — revisão corretiva do Prompt 06

- Branch: `main`
- Commit base: `793777d + working-tree`
- Ambiente: Windows 11, JDK Temurin 25.0.4, Docker Desktop ativo,
  Testcontainers com PostgreSQL 17 e Redis 7
- Escopo: revisão e correção da implementação de autorização e multi-tenancy
  do Prompt 06

## Resultado

A implementação anterior tinha uma base correta para `TENANT` e `PROPRIO`, mas
deixava superfícies sem proteção e promovia na prática concessões `OWN` a
recursos coletivos. A revisão fechou essas lacunas sem criar migration de
produção e preservou a decisão de falha fechada para `UNIT` do ADR-0008.

## Lacunas encontradas

1. A apresentação e o onboarding do tenant não chamavam a autorização central.
2. Canais e conversas compartilhadas aceitavam qualquer alcance da permissão;
   `OWN` acabava vendo o tenant inteiro.
3. A assinatura STOMP aceitava `OWN` e qualquer caminho sob o prefixo do tenant.
4. A combinação `UNIT + OWN` escolhia `UNIT` pela ordem do enum e anulava a
   concessão `OWN` válida.
5. Oportunidades e tarefas podiam referenciar contato ou oportunidade de outro
   responsável sob alcance próprio.
6. Alguns controllers buscavam o id antes de verificar a permissão, expondo um
   oráculo de existência por diferença entre 403 e 404.
7. `GET /api/funis` podia criar o funil padrão exigindo apenas `deals.read`.
8. O seed dava permissões de caixa compartilhada ao papel `ATTENDANT` com
   alcance `OWN`, tornando ambíguo o modelo que deveria demonstrar.

## Correções aplicadas

- `Autorizacao` ganhou operações explícitas para membership vigente e para
  recursos que obrigatoriamente exigem alcance `TENANT`.
- Apresentação exige membership ativo; mutação do onboarding exige
  `organization.manage` com alcance `TENANT`.
- Canais, conversas, relatórios e tópicos compartilhados exigem `TENANT`.
- Destinos STOMP usam uma lista exata: inbox do tenant ou conversa identificada
  por UUID; caminhos desconhecidos e segmentos extras são recusados.
- Na consolidação de alcances, `TENANT` prevalece e `OWN` permanece válido
  diante de um alcance ainda não suportado. `UNIT` isolado continua negado.
- Contato, oportunidade e tarefa revalidam o registro relacionado pelas portas
  públicas de lookup e checam a permissão antes de consultar o id alvo.
- A criação preguiçosa do funil padrão exige `deals.write` dentro do ramo que
  efetivamente escreve, evitando uma janela entre verificação e gravação.
- O seed separa `ATTENDANT` (`OWN` para contatos, oportunidades e tarefas) de
  `ATTENDANT_SHARED` (`TENANT` somente para conversas compartilhadas).

## Evidências

| verificação | resultado |
| --- | --- |
| suíte backend integral | 112 testes, 0 falhas, 0 erros, 0 ignorados |
| testes focados de autorização | 47 testes verdes |
| migrations de atualização | 2 cenários verdes |
| conjunto `dev` | 14 artefatos Flyway aplicados e validados, incluindo os seeds repetíveis |
| infraestrutura de teste | PostgreSQL 17 e Redis 7 reais; runtime sem `BYPASSRLS` |

Os novos casos cobrem matriz de alcance, recursos coletivos, IDOR de leitura e
escrita, referências cruzadas entre responsáveis, revogação de membership na
requisição seguinte, destinos STOMP inválidos e escrita preguiçosa do funil.

## Limites preservados

- `UNIT` ainda depende de `unit_id` nas tabelas de domínio e falha fechado.
- Os tópicos atuais representam a caixa compartilhada; não existe ainda tópico
  individual que possa receber alcance `OWN`.
- Exportação e jobs ainda não existem como superfícies. Quando forem criados,
  deverão revalidar identidade e autorização na execução, não só na criação.
- Nenhum arquivo do frontend foi alterado nesta correção.
- Nenhum commit foi criado; o repositório continua com o trabalho acumulado no
  working tree.
