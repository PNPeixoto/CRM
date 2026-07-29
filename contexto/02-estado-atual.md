# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-07-29

## Onde parei

**Fases 1 e 2 escritas por inteiro, compilando dos dois lados.** Falta
*executar* os testes de integração, que exigem Docker. Fase 3 (Telegram) não
começou.

## Verificado de fato (executado)

- Backend compila com JDK 25; frontend builda.
- **21 testes rodando e passando** nesta máquina, sem Docker:
  `FronteiraDeModulosTest` (fronteira de módulos), `TopicosTempoRealTest`
  (extração de tenant do destino STOMP) e `InboundMessageTest` (contrato
  normalizado de entrada).
- **Tela de login** renderizada no navegador: rótulos associados,
  `role="alert"` no erro, tema claro como padrão e troca para escuro.
- **Inbox renderizado** com `fetch` interceptado no navegador (stub temporário,
  já removido): lista, thread, composer, fuso `America/Sao_Paulo` correto,
  cores dos balões conforme os tokens, sem rolagem horizontal.
- Fontes Manrope e JetBrains Mono carregando auto-hospedadas, sob o CSP.

## Escrito, compilando, NUNCA executado contra banco

Nada abaixo jamais tocou um Postgres. É a maior dívida aberta do projeto.

- **Migrations V1, V2 e V3** — 5 tabelas, 7 políticas de RLS `FORCE`,
  4 funções `SECURITY DEFINER`, triggers de `updated_at`.
- **Fase 1 — identity**: Argon2id com pepper, access token de 15 min, refresh
  rotativo com família, bloqueio progressivo em Redis, resposta de login
  uniforme com hash dummy.
- **Fase 2 — conversation/channel**: ingestão idempotente, porta
  `ChannelAdapter`, `LiveChatAdapter`, STOMP com autorização por inscrição,
  REST de conversa, worker da fila de saída com backoff e teto de tentativas.
- **Testes de integração**: `CrmApplicationTests`, `IsolamentoEntreTenantsTest`,
  `LoginRespostaUniformeTest`, `RefreshTokenRotacaoTest`, `FilaDeSaidaTest`.
  Todos compilam; nenhum rodou.

## Onde os testes rodam

Limitação de **máquina**, não de código:

| Ambiente | Roda os testes de integração? |
|---|---|
| Notebook Linux | **Sim.** Docker nativo, sem WSL, sem AF_UNIX |
| Windows 11 IoT LTSC (máquina de 2026-07-28) | Não. Falta licença do Docker Desktop e WSL2, ausente nessa edição |
| Claude Code web | Provavelmente não — o sandbox costuma não ter Docker |

## Próximo passo

1. **No Linux:** `docker compose up -d` e `./mvnw test`. É o passo mais
   valioso do projeto agora — cinco tabelas e quatro funções privilegiadas
   dependem de uma abordagem de RLS que nunca foi exercida.
2. Validar o login e o inbox ponta a ponta com backend no ar.
3. Fase 3 — Telegram (reordenado; ver `03-decisoes.md` de 2026-07-29).

Enquanto as migrations não forem aplicadas, elas continuam editáveis. Isso
deixa de valer no primeiro `flyway migrate`.

## Fase 2 — o que ainda falta

- Widget do visitante e emissão de sessão própria para ele (o visitante não é
  usuário do CRM). `LiveChatAdapter.enviar()` hoje entrega só o lado do
  atendente.
- Fila, atribuição e SLA.
- Tela de reenvio de mensagem morta (reprocessar é zerar `attempt_count`).

## Pendências conhecidas

- **`react-router` com alerta alto** (GHSA-qwww-vcr4-c8h2, CSRF em modo RSC).
  Não usamos RSC. `npm audit fix --force` propõe **descer** para 7.11.0.
- **Modelo de cobrança do WhatsApp não decidido**: cliente traz a própria WABA
  (medição serve para limite) ou nós revendemos (medição é faturamento).
  Muda modelagem de tabela. Fechar antes da Fase 4.
- Broker STOMP em memória não funciona com mais de uma instância. Decidir o
  broker externo **antes** de subir a segunda.

## Armadilhas conhecidas

- **`Selector.open()` no Windows — resolvido.** Causa: o `Pipe` interno do JDK
  usa AF_UNIX, e o `connect` falha quando o socket nasce em
  `AppData\Local\Temp`. Sem isso, Tomcat, Netty e Testcontainers não sobem.
  Corrigido por
  `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Administrator\javatmp`.
  **Não se aplica a Linux nem macOS.**
- **O build exige JDK 25.** `JAVA_HOME` aponta para o Temurin 25; o JDK 21
  continua no `PATH`.
- **`FORCE ROW LEVEL SECURITY` é obrigatório.** O usuário `crm` é dono das
  tabelas, e sem `FORCE` o Postgres ignora toda política para o dono.
- **Trocar `APP_PEPPER` invalida todas as senhas.** Não é rotação
  transparente.
- **`@Transactional` não funciona em auto-invocação.** Já causou um bug real
  em `FilaDeSaidaWorker`, corrigido separando `ReservaDeMensagens` em bean
  próprio. Método anotado chamado de dentro da mesma classe é ignorado em
  silêncio.
- **`@NamedInterface` é o que expõe um pacote `api`.** Comentário dizendo que
  só `api` é visível não faz nada — `ApplicationModules.verify()` já reprovou
  por isso.
- **CSS do dev server pode ficar obsoleto.** Classe nova do Tailwind pode não
  existir até um rescan. Se algo parecer sem estilo, conferir o build de
  produção antes de "consertar".
- Postgres 17 não tem `uuidv7()`. UUID v7 é gerado em `shared.api.UuidV7`.
  Testcontainers fixado em `postgres:17-alpine`.
- `ddl-auto` permanece em `validate`. Nenhum hex literal em componente.
