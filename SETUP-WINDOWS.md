# Configuração no Windows

Guia para executar o CRM PNP com Docker Desktop e PowerShell. O ambiente
Docker é autocontido: backend, PostgreSQL 17 e Redis 7.

## Pré-requisitos

- Git;
- Docker Desktop com containers Linux;
- JDK 25 para testes ou execução local do backend;
- Node.js 20+ para o frontend.

Confirme no PowerShell:

```powershell
docker version
docker compose version
java -version
node --version
```

## Ambiente Docker completo

Na raiz do repositório:

```powershell
$env:APP_IMAGE_TAG = '0.0.1-dev'
$env:APP_VERSION = '0.0.1-dev'
$env:VCS_REF = git rev-parse --short HEAD
docker compose up --build -d
docker compose ps
```

Os três serviços devem ficar `healthy`. O backend usa
`http://127.0.0.1:8080`, o PostgreSQL `127.0.0.1:5432` e o Redis
`127.0.0.1:6380`. A 6380 evita depender de Redis/Memurai instalado no Windows.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
```

Para ver logs ou encerrar sem remover dados:

```powershell
docker compose logs -f app
docker compose down
```

Não use `docker compose down -v` se houver dados locais que precisem ser
preservados.

## Backend no host (opcional)

Suba só as dependências:

```powershell
docker compose up -d postgres redis
Set-Location backend
$env:SPRING_PROFILES_ACTIVE = 'dev'
./mvnw.cmd spring-boot:run
```

Se o JDK falhar com `Unable to establish loopback connection`, use um diretório
curto para os sockets AF_UNIX e abra um novo terminal:

```powershell
New-Item -ItemType Directory -Force C:\javatmp | Out-Null
[Environment]::SetEnvironmentVariable(
  'JDK_JAVA_OPTIONS',
  '-Djdk.net.unixdomain.tmpdir=C:\javatmp',
  'User'
)
```

Essa configuração é específica do Windows.

## Testes

Os testes do backend exigem Docker Desktop porque usam Testcontainers:

```powershell
Set-Location backend
./mvnw.cmd test
```

Em outro terminal, valide o frontend:

```powershell
Set-Location frontend
npm install
npm test
npm run lint
npm run build
```

Execute `npm run dev` para abrir `http://localhost:5174`; o Vite encaminha
`/api` para o backend na 8080.

## Produção

O `docker-compose.yml` não é um deploy de produção. Produção usa o profile
`prod`, uma tag imutável, segredos externos e endpoints privados de banco e
cache. Veja `infra/docker/README.md`.
