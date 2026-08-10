# Guia de onboarding para desenvolvimento

Este guia leva uma pessoa nova do clone até uma alteração validada sem
substituir os guias específicos de sistema operacional. O código e a
configuração executável vencem este texto quando houver divergência.

## 1. Pré-requisitos

- Git;
- Docker com Docker Compose e suporte a containers Linux;
- JDK 25 para executar ou testar o backend no host;
- Node.js 24.x e npm 11 para o frontend.

Não é necessário instalar Maven: o wrapper está em `backend/mvnw` e
`backend/mvnw.cmd`.

No Windows, siga também o [guia de configuração](../SETUP-WINDOWS.md). No
Linux, consulte o [guia para Fedora/Nobara](../SETUP-LINUX.md).

## 2. Clonar e ler o contexto mínimo

```bash
git clone https://github.com/PNPeixoto/CRM.git crm-pnp
cd crm-pnp
```

Leia nesta ordem:

1. [`CLAUDE.md`](../CLAUDE.md), ponto de entrada e protocolo de decisão;
2. [`00-projeto.md`](00-projeto.md), produto e prioridades;
3. [`01-padroes-tecnicos.md`](01-padroes-tecnicos.md), regras técnicas;
4. [`02-estado-atual.md`](02-estado-atual.md), implementação e próximo passo;
5. [diagramas de arquitetura](diagramas/README.md), visão visual do sistema.

Antes de começar uma entrega, confira ainda os manifestos
[`backend`](prompts/manifest.yaml) e
[`frontend`](prompts/frontend/manifest.yaml). Eles definem ordem,
pré-requisitos e gates; um prompt novo não deve ser inventado fora dessas
trilhas para duplicar trabalho existente.

## 3. Subir o ambiente

### Opção A — backend e dependências em containers

No PowerShell, na raiz do repositório:

```powershell
$env:APP_IMAGE_TAG = '0.0.1-dev'
$env:APP_VERSION = '0.0.1-dev'
$env:VCS_REF = git rev-parse --short HEAD
docker compose up --build -d
docker compose ps
```

Em bash:

```bash
export APP_IMAGE_TAG=0.0.1-dev
export APP_VERSION=0.0.1-dev
export VCS_REF="$(git rev-parse --short HEAD)"
docker compose up --build -d
docker compose ps
```

O Compose de desenvolvimento sobe somente PostgreSQL 17, Redis 7 e o backend.
Não existe RabbitMQ no ambiente atual. As migrations Flyway são aplicadas pela
aplicação; não execute uma migration manual separada.

Valide o backend:

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

### Opção B — backend no host

Suba apenas as dependências:

```bash
docker compose up -d postgres redis
```

No PowerShell:

```powershell
Set-Location backend
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

Em bash:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

O profile `dev` é obrigatório: ele seleciona o seed de demonstração, as
credenciais descartáveis e o Redis publicado na porta 6380 do host.

### Frontend

Em outro terminal:

```bash
cd frontend
npm ci --legacy-peer-deps
npm run dev
```

A SPA abre em `http://localhost:5174` e encaminha `/api` e `/ws` para o
backend. Em produção, o servidor de arquivos estáticos e o proxy não fazem
parte deste repositório.

## 4. Contas de desenvolvimento

O seed contém os tenants `pnp` e `acme`, ambos com o login `peixoto`, além do
login `atendente` em `pnp`. `peixoto` representa a conta máxima do tenant e
exige MFA; `atendente` exercita alcances mais restritos.

A senha não é documentada nem versionada. Obtenha-a pelo canal seguro adotado
pela equipe. No primeiro acesso de uma conta com MFA obrigatório, a SPA conduz
o cadastro no autenticador e mostra códigos de recuperação uma única vez.

## 5. Tour pelo código

```text
crm-pnp/
├── backend/
│   ├── src/main/java/br/com/pnp/crm/  # módulos Spring Modulith
│   ├── src/main/resources/db/         # migrations e seed dev separado
│   ├── src/test/                       # unitários, integração e contratos
│   └── openapi/openapi.json            # contrato HTTP versionado
├── frontend/
│   ├── src/app/                        # rotas e composição da aplicação
│   ├── src/adapters/http/              # fronteira OpenAPI/HTTP
│   ├── src/shared/                     # sessão, tenant e componentes comuns
│   └── src/pages/                      # telas por área funcional
├── infra/                              # imagem, PostgreSQL e operação
├── contexto/                           # fonte documental canônica
└── .github/                            # CI, dependências e segurança
```

No backend, outro módulo só pode importar a interface nomeada `api`; o pacote
`internal` é privado. O teste `FronteiraDeModulosTest` aplica essa regra. O
tenant nunca vem do corpo ou de parâmetro do cliente: é derivado da credencial
e propagado até o PostgreSQL, onde RLS é forçado também para o papel runtime.

## 6. Validar antes de entregar

Backend completo, com PostgreSQL e Redis reais via Testcontainers:

```bash
cd backend
./mvnw test
```

No Windows, use `.\mvnw.cmd test`. Para ciclos locais menores existem os
profiles `gate-rapido` e `integracao`:

```bash
./mvnw test -Pgate-rapido
./mvnw test -Pintegracao
```

Frontend e contrato gerado:

```bash
cd frontend
npm run api:check
npm run lint
npm test
npm run build
```

Não existe hoje um script E2E no `package.json`; não registre esse gate como
executado sem que ele tenha sido implementado.

## 7. Primeira contribuição

1. Escolha um prompt `ready` cujos pré-requisitos estejam concluídos.
2. Leia o prompt inteiro e os ADRs relacionados antes de editar.
3. Faça uma mudança pequena, com teste proporcional ao risco.
4. Não altere migration já aplicada; mudança de schema recebe nova versão.
5. Não grave senha, token, cookie, payload real ou dado pessoal em evidência.
6. Execute os gates relevantes e registre o resultado em
   `contexto/sessoes/` quando a tarefa exigir consolidação.

## 8. Problemas comuns

- `release version 25 not supported`: o JDK ativo não é 25.
- backend local sem seed ou sem Redis: o profile `dev` não foi ativado.
- API 404/502 na SPA: o backend não está saudável na porta 8080.
- erro de WebSocket em `localhost:5174/ws`: confirme o proxy do Vite e o
  backend antes de investigar STOMP.
- testes marcam falha de infraestrutura: verifique o Docker; o preflight da
  suíte separa indisponibilidade do ambiente de regressão de código.
- Flyway acusa checksum: não edite o histórico nem use `repair` como atalho;
  restaure a migration aplicada e crie uma migration corretiva aditiva.

