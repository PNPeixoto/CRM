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
- **Docker** — Postgres e Redis de desenvolvimento, e os testes de integração
  (Testcontainers)
- **Node 20+**

> **No Linux (Nobara/Fedora), siga o [`SETUP-LINUX.md`](SETUP-LINUX.md)** —
> guia completo do zero, com as pegadinhas do dnf5, SELinux e Testcontainers
> já resolvidas.

## Subir o ambiente

```bash
docker compose up -d
```

Postgres em `127.0.0.1:5432` e Redis em `127.0.0.1:6379`. As portas são
publicadas só em loopback de propósito: o Docker publica em `0.0.0.0` por
padrão e ignora o firewall do sistema.

## Backend

O profile `dev` é obrigatório em desenvolvimento — é ele que carrega o seed de
`db/dev` e fornece os valores de `APP_PEPPER` e `JWT_SIGNING_KEY`. Sem profile,
a aplicação **não sobe**, e isso é intencional: não existe valor padrão de
segredo em `application.yml`.

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Testes (exigem Docker):

```bash
cd backend && ./mvnw test
```

## Frontend

```bash
cd frontend && npm install && npm run dev
```

Sobe em `http://localhost:5174`, com proxy de `/api` para
`http://localhost:8080`. O proxy não é conveniência: ele faz o navegador ver
tudo na mesma origem, o que permite o cookie de refresh usar
`SameSite=Strict` em vez de afrouxar CORS só para desenvolver.

## Usuário de desenvolvimento

O seed cria dois tenants para tornar visível qualquer vazamento de isolamento.

| Empresa | Login |
|---|---|
| `pnp` | `peixoto` |
| `acme` | `peixoto` |

A senha não está escrita em nenhum arquivo do repositório, por regra do
projeto. Os hashes são Argon2id com o pepper de desenvolvimento definido em
`application-dev.yml` — **trocar o pepper invalida esses hashes**, que é o
efeito pretendido dele.

## Variáveis de ambiente

`.env.example` lista apenas os **nomes**. Valor real nunca entra no
repositório. Em produção, `APP_PEPPER` e `JWT_SIGNING_KEY` não têm padrão e a
aplicação recusa subir sem eles.

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

## Estado

Fase 1 (autenticação) escrita e compilando; testes de integração ainda não
executados. Ver [`contexto/02-estado-atual.md`](contexto/02-estado-atual.md)
para o detalhe e o próximo passo.
