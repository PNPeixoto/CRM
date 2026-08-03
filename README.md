# CRM PNP

Plataforma SaaS de CRM omnichannel para redes de franquias. Centraliza
WhatsApp, Instagram, Telegram e chat ao vivo em uma única caixa de entrada,
com múltiplos números e contas dentro da mesma licença.

O contexto completo do projeto — produto, padrões técnicos, decisões e estado
atual — está em [`contexto/`](contexto/). **Comece por
[`CLAUDE.md`](CLAUDE.md)**, que define a ordem de leitura.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Modulith 2.1 |
| Banco | PostgreSQL 17 + Flyway, Row Level Security |
| Cache/filas | Redis 7 |
| Frontend | React 19, TypeScript, Vite, Tailwind 4 |

## Pré-requisitos

- **JDK 25** — o `pom.xml` fixa `java.version=25`; JDK menor falha com
  `release version 25 not supported`
- **Docker** — imagem da aplicação, Postgres e Redis de desenvolvimento, além
  dos testes de integração (Testcontainers)
- **Node 20+**

> **No Linux (Nobara/Fedora), siga o [`SETUP-LINUX.md`](SETUP-LINUX.md)** —
> guia completo do zero, com as pegadinhas do dnf5, SELinux e Testcontainers
> já resolvidas.
>
> **No Windows, siga o [`SETUP-WINDOWS.md`](SETUP-WINDOWS.md)** — inclui Docker
> Desktop, PowerShell e a correção de AF_UNIX do JDK.

## Subir o ambiente

O Compose é exclusivamente de desenvolvimento e sobe backend, PostgreSQL 17 e
Redis 7. A aplicação usa uma imagem versionável; `latest` não faz parte do
fluxo.

```bash
APP_IMAGE_TAG=0.0.1-dev APP_VERSION=0.0.1-dev VCS_REF=working-tree \
  docker compose up --build -d
docker compose ps
```

No PowerShell, defina as três variáveis com `$env:NOME='valor'` antes do
`docker compose up --build -d`.

Backend em `127.0.0.1:8080`, Postgres em `127.0.0.1:5432` e Redis em
`127.0.0.1:6380`. As portas são publicadas só em loopback de propósito. O
serviço `app` só inicia depois dos health checks do banco e do cache, roda sem
root e com filesystem somente leitura. Confira:

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

O Postgres de desenvolvimento usa dois papéis:

- `crm_migrator`: administrativo, usado somente pelo Flyway;
- `crm_runtime`: usado pela aplicação, sem `SUPERUSER` e sem `BYPASSRLS`.

O script em `infra/postgres/init-runtime-user.sql` cria o runtime apenas na
inicialização de um volume novo. Um volume criado pela configuração antiga
precisa ser migrado manualmente ou recriado depois de preservar qualquer dado
necessário; mudar `POSTGRES_USER` não altera um cluster já inicializado.

## Backend local, sem conteinerizar a aplicação

Suba apenas as dependências e execute o Maven no host:

```bash
docker compose up -d postgres redis
```

O profile `dev` é obrigatório em desenvolvimento — é ele que carrega o seed de
`db/dev`, aponta para Redis em `127.0.0.1:6380` e fornece credenciais
descartáveis. Os valores reais de produção não possuem fallback.

```bash
cd backend && ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

> **As aspas não são enfeite.** No PowerShell, `-Dspring-boot.run.profiles=dev`
> sem aspas é partido em dois argumentos no hífen de `spring-boot`, e o Maven
> recebe `.run.profiles=dev` solto — o erro que aparece é
> `Unknown lifecycle phase`, que não diz nada sobre a causa. Em bash as aspas
> são inofensivas, então o comando acima funciona nos dois.

Alternativa que evita a tokenização de vez:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Testes (exigem Docker):

```bash
cd backend && ./mvnw test
```

Os testes de isolamento confirmam `current_user`, `rolsuper=false` e
`rolbypassrls=false`; um teste conectado como superusuário não é evidência de
que RLS funciona.

## Frontend

```bash
cd frontend && npm install && npm run dev
```

Sobe em `http://localhost:5174`, com proxy de `/api` para
`http://localhost:8080`. O proxy não é conveniência: ele faz o navegador ver
tudo na mesma origem, o que permite o cookie de refresh usar
`SameSite=Strict` em vez de afrouxar CORS só para desenvolver.

Validação do frontend:

```bash
npm test
npm run lint
npm run build
```

No primeiro acesso autenticado, `/api/empresa/apresentacao` informa se o
segmento já foi escolhido. A tela `/primeiro-acesso` persiste apenas o segmento
em `/api/empresa/perfil-inicial`; menu, ordem das rotas e funil inicial passam a
usar o mesmo preset sem exigir novo login. Isso é apresentação, nunca
autorização — o backend continua protegendo cada endpoint.

## Usuário de desenvolvimento

O seed cria dois tenants para tornar visível qualquer vazamento de isolamento.

| Empresa | Login |
|---|---|
| `pnp` (`GENERAL_SERVICES`) | `peixoto` |
| `acme` (`CONFECTIONERY`) | `peixoto` |

Empresa e login são **insensíveis a maiúsculas** — "PNP" e "Peixoto" também
entram. Espaços nas pontas são descartados.

A senha não está escrita em nenhum arquivo do repositório, por regra do
projeto. Os hashes são Argon2id com o pepper de desenvolvimento definido em
`application-dev.yml` — **trocar o pepper invalida esses hashes**, que é o
efeito pretendido dele.

## Variáveis de ambiente

`.env.example` lista apenas os **nomes**. Valor real nunca entra no
repositório. Em produção, conexões, credenciais, `APP_PEPPER`,
`JWT_SIGNING_KEY` e a lista de proxies confiáveis não têm padrão; o profile
`prod` recusa subir se faltar configuração obrigatória.

> `APP_PEPPER` **não é rotacionável**: trocá-lo invalida todas as senhas de
> uma vez e exige redefinição por todos os usuários. Perder o valor de
> produção equivale a perder todas as senhas.

## Armadilha específica de Windows

Se o backend não subir com `IOException: Unable to establish loopback
connection`, a causa é o `Pipe` interno do JDK sobre AF_UNIX falhando quando
o socket é criado em `AppData\Local\Temp`. Sem isso, Tomcat, Netty e
Testcontainers não funcionam.

```
JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\<usuario>\javatmp
```

**Não é necessário em Linux nem macOS** — inclusive no Claude Code web.

## Imagem e produção

A imagem multi-stage usa somente o JRE e as camadas da aplicação no estágio
final, executa com UID/GID `10001` e inclui readiness como health check. O
Compose deste repositório não é um manifesto de produção: banco e cache devem
ficar em rede privada e somente o proxy conhecido pode enviar
`X-Forwarded-*`. Build, promoção e rollback por tag imutável estão descritos em
[`infra/docker/README.md`](infra/docker/README.md).

## Estado

Autenticação, conversa/canais, CRM básico, apresentação por segmento e o
ambiente Docker de desenvolvimento estão implementados. A suíte PostgreSQL
continua dependente de Docker/Testcontainers. Ver
[`contexto/02-estado-atual.md`](contexto/02-estado-atual.md) para resultados e
riscos atuais.
