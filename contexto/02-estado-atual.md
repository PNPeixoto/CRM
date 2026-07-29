# Estado atual

> Reescrito ao fim de cada sessão. Máximo 150 linhas.
> Última atualização: 2026-07-28

## Onde parei

**Fase 1 (autenticação) escrita por inteiro, backend e frontend, compilando
nos dois lados.** Falta *executar* os testes de integração: eles exigem
Docker, que está instalado mas ainda não subiu nesta máquina.

## Pronto e verificado

- **Fase 0 completa.** `backend/compose.yaml` apagado e
  `spring-boot-docker-compose` removido do `pom.xml`; BouncyCastle 1.85 e
  `spring-boot-starter-oauth2-resource-server` adicionados. Nome do pacote
  `br.com.pnp.crm` confirmado como definitivo.
- **Ambiente destravado.** JDK 25 instalado, backend compila
  (`release 25`). O `Selector.open()` que impedia Tomcat/Testcontainers foi
  corrigido — ver "Armadilhas".
- **Migration V1** com `tenant`, `app_user`, `refresh_token`, RLS `FORCE` em
  todas, índices compostos começando por `tenant_id`, login único por tenant
  entre não-excluídos, e duas funções `SECURITY DEFINER` de resolução.
- **Seed de dev** em `db/dev/V900`, carregado só pelo profile `dev`.
- **Módulo `identity`**: Argon2id com pepper, access token de 15 min, refresh
  rotativo com família, bloqueio progressivo em Redis, resposta de login
  uniforme com hash dummy, `SecurityConfig` com CSP liberando WebSocket, sem
  `X-XSS-Protection`, cookie `SameSite=Strict`.
- **Módulo `tenant`**: entidade e `TenantLookup` como porta pública.
- **`ApplicationModules.verify()` passa** — executado de fato.
- **Frontend**: tokens semânticos, `lib/api.ts` com refresh único no 401 e
  serialização de refreshes concorrentes, `AuthContext`, `RotaProtegida`,
  `LoginPage` real. Build passa; tela validada no navegador, incluindo o
  caminho de erro e a troca de tema.

## Pendente de execução — não é bloqueio do projeto

**Testes de integração nunca executados.** `IsolamentoEntreTenantsTest`,
`LoginRespostaUniformeTest`, `RefreshTokenRotacaoTest` e `CrmApplicationTests`
estão escritos e **compilam**, mas exigem Testcontainers.

Isto é limitação de **máquina**, não de código, e onde ela existe importa:

| Ambiente | Roda os testes? |
|---|---|
| Notebook Linux | **Sim.** Docker nativo, sem WSL, sem a armadilha do AF_UNIX |
| Windows 11 IoT LTSC (a máquina de 2026-07-28) | Não. Falta aceitar a licença do Docker Desktop e o WSL2, que não existe nessa edição |
| Claude Code web | Provavelmente não — o sandbox costuma não ter Docker |

**Então a Fase 1 fecha no notebook Linux.** Não há nada a corrigir no código
antes disso; o que falta é rodar.

Consequência direta: a migration V1 **nunca foi aplicada em banco nenhum** e
ainda é editável sem violar a regra de imutabilidade. Isso deixa de valer no
instante em que `./mvnw test` rodar pela primeira vez.

## Próximo passo

1. **No Linux:** `docker compose up -d` na raiz e `./mvnw test`. Só então a
   Fase 1 está verde.
2. Validar o login ponta a ponta com o backend no ar (empresa `pnp`, usuário
   `peixoto`).
3. Fase 2 — módulo `conversation`, porta `ChannelAdapter` e `LiveChatAdapter`.

O trabalho de **escrita** da Fase 2 pode ser adiantado em qualquer ambiente,
inclusive no web. A **validação** exige a máquina com Docker.

## Trabalhando entre CLI e web

O repositório é a única fonte de sincronia — não há estado fora dele. Antes de
trocar de ambiente, commitar e dar push; ao chegar no outro, dar pull. Este
arquivo é o que diz onde o trabalho parou, então ele precisa estar correto no
momento do push, e não só no fim da sessão.

## Pendências conhecidas

- **`react-router` com alerta de segurança alto** (GHSA-qwww-vcr4-c8h2, bypass
  de CSRF em modo RSC). Não usamos RSC — usamos `BrowserRouter` no cliente —
  então a exposição é improvável. `npm audit fix --force` propõe **descer**
  para 7.11.0. Avaliar antes de aceitar.
- Variante clara do `#4B2ED4` para superfície escura: resolvida como
  `#9b85f5` no token de tema escuro, ainda sem conferência formal de
  contraste.
- Broker STOMP em memória não funciona com mais de uma instância. Decidir o
  broker externo **antes** de subir a segunda instância.

## Armadilhas conhecidas

- **`Selector.open()` falhava nesta máquina — resolvido.** A causa era
  AF_UNIX: o `Pipe` interno do JDK usa socket de domínio Unix, e o `connect`
  falha quando o socket é criado em `AppData\Local\Temp`. Sem isso, Tomcat,
  Netty e Testcontainers não sobem. Corrigido pela variável de usuário
  `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Administrator\javatmp`.
  Se o backend voltar a não subir, **conferir essa variável primeiro**.
- **O build exige JDK 25.** `JAVA_HOME` de usuário aponta para o Temurin 25;
  o JDK 21 da Oracle continua no `PATH`. `mvnw` sem `JAVA_HOME` correto falha
  com "release version 25 not supported".
- **`FORCE ROW LEVEL SECURITY` é obrigatório.** O usuário `crm` é dono das
  tabelas, e sem `FORCE` o Postgres ignora toda política para o dono — o
  isolamento pareceria configurado e não existiria.
- **Trocar `APP_PEPPER` invalida todas as senhas**, inclusive as do seed. Não
  é rotação transparente; exige redefinição por todos os usuários.
- **`@NamedInterface` é o que realmente expõe um pacote `api`.** Comentário
  em `package-info.java` dizendo "só `api` é visível" não faz nada sozinho —
  `ApplicationModules.verify()` reprovou exatamente por isso.
- Postgres 17 não tem `uuidv7()` (só a partir do 18). UUID v7 é gerado na
  aplicação, em `shared.api.UuidV7`. Testcontainers está fixado em
  `postgres:17-alpine` para não testar contra versão diferente da real.
- `ddl-auto` deve permanecer em `validate`.
- Nenhum hex literal em componente: cor só entra via token semântico.
