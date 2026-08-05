# Testes do backend

A suíte usa PostgreSQL 17 e Redis 7 reais via Testcontainers. O usuário da
aplicação é `crm_runtime_test`; somente o suporte de teste usa o migrador para
aplicar migrations e limpar dados. O runtime não recebe `TRUNCATE`,
`BYPASSRLS`, `CREATE` ou privilégios administrativos.

## Cortes reproduzíveis

No Windows, troque `./mvnw` por `.\mvnw.cmd`.

- `./mvnw test`: gate completo; unitários, fronteiras e integração. Carga fica
  fora. Quarentenas, se existirem, continuam rodando.
- `./mvnw -Pgate-rapido test`: unitários e contratos estáticos, sem subir
  containers. É o retorno rápido para desenvolvimento.
- `./mvnw -Pintegracao test`: somente testes com PostgreSQL/Redis reais.
- `./mvnw -Pquarentena test`: execução dedicada e contínua das exceções
  temporárias.
- `./mvnw -Pcarga test`: valida os quatro perfis de carga; não dispara carga
  contra um ambiente por acidente.

O gate completo desta máquina leva cerca de 70 segundos. O corte rápido deve
ficar em segundos; a duração real deve ser registrada a cada mudança de CI.

Antes de descobrir testes, o gate completo confirma o runtime de containers
uma única vez. Se o Docker estiver parado, a execução termina com uma mensagem
de infraestrutura e indica `-Pgate-rapido`, sem repetir a mesma causa em cada
classe de integração.

## Quarentena

Não use `@Disabled` nem `@Tag("quarentena")`. A única entrada permitida é
`@Quarentena`, com issue, responsável, justificativa, categoria e data de
expiração ISO-8601. O gate reprova metadado vazio ou expirado. Testes de
segurança, tenant, migration e cobrança não podem entrar em quarentena.

Uma quarentena é uma exceção de gate, não uma forma de transformar falha em
sucesso. Ela continua na execução dedicada e deve ser removida assim que a
causa for corrigida.

## Fixtures e diagnóstico

Cada cenário cria tenants e usuários fictícios explicitamente e limpa antes e
depois. Não reutilize seed `dev`, dados pessoais, tokens ou segredos reais.
Relatórios de teste podem conter somente identificadores sintéticos. Falhas de
RLS, constraint e transação devem permanecer visíveis; não substitua banco por
mock ou H2.

## Perfis de carga preparados

`src/test/resources/carga/perfis.properties` define login, inbox, fila de saída
e relatório com rota, concorrência, duração, orçamento p95 e taxa máxima de
erro. O contrato automatizado impede rota sensível ou valor fora dos limites.
A geração de tráfego fica deliberadamente separada e exige ambiente descartável,
seed sintético e autorização operacional; o baseline representativo será
medido no Prompt 27.

O benchmark isolado de senha usa
`./mvnw -Pcarga -Dtest=Argon2BenchmarkTest test`. Em 2026-08-01, Java 25.0.4
no Windows, Argon2id `m=32768,t=3,p=1` mediu média de 103 ms em cinco amostras
após aquecimento.
