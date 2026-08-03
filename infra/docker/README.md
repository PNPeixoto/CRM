# Imagem e ambientes do CRM PNP

## Contrato dos ambientes

| Ambiente | Configuração | Dados | Exposição |
|---|---|---|---|
| Desenvolvimento | `docker-compose.yml` + profile `dev` | volume local nomeado e seed de dev | app, banco e cache apenas em loopback |
| Teste | profile `test` + Testcontainers | PostgreSQL/Redis efêmeros | portas aleatórias do host |
| Produção | imagem promovida + profile `prod` | serviços externos/persistentes | somente a aplicação atrás do proxy |

O repositório mantém uma única composição de desenvolvimento. Não há um
Compose de produção disfarçado nem MailHog sem consumidor: o sistema ainda não
possui fluxo de e-mail.

## Imagem

O `backend/Dockerfile` tem dois estágios:

1. JDK Temurin 25 compila com Maven Wrapper e separa as camadas do Spring Boot;
2. JRE Temurin 25 recebe somente as camadas executáveis.

O estágio final não contém código-fonte, Maven, JDK ou caches de build. Ele
executa com UID/GID `10001`, readiness como health check e metadados OCI de
versão e revisão.

Crie uma tag imutável a partir da versão e do commit:

```bash
VERSION=0.0.1
REVISION="$(git rev-parse --short HEAD)"
TAG="${VERSION}-${REVISION}"
docker build --pull \
  --build-arg APP_VERSION="$TAG" \
  --build-arg VCS_REF="$REVISION" \
  --tag "crm-pnp-backend:$TAG" backend
```

Não publique nem implante `latest`. Em um registry, prefira também registrar o
digest produzido pelo push e promover exatamente esse digest entre ambientes.

## Desenvolvimento

```bash
export APP_IMAGE_TAG=0.0.1-dev
export APP_VERSION=0.0.1-dev
export VCS_REF="$(git rev-parse --short HEAD)"
docker compose up --build -d
docker compose ps
```

O Compose espera PostgreSQL e Redis passarem seus health checks antes de
iniciar a aplicação. O backend usa raiz somente leitura, `/tmp` efêmero,
`no-new-privileges`, nenhuma capability Linux, limite de 768 MiB e uma CPU.

PostgreSQL persiste em volume nomeado. Redis é cache e não possui volume. O
script `infra/postgres/init-runtime-user.sql` só roda na criação de um volume
novo e separa `crm_migrator` de `crm_runtime`.

## Saúde

- `/actuator/health/liveness` verifica apenas o estado interno da aplicação;
- `/actuator/health/readiness` inclui PostgreSQL e Redis;
- detalhes de saúde não são expostos.

Uma dependência indisponível retira a instância do tráfego sem criar um ciclo
de reinicializações de todas as réplicas.

## Produção fail-closed

Ative `SPRING_PROFILES_ACTIVE=prod` e injete os valores por secret manager ou
mecanismo equivalente. Os nomes estão em `.env.example`; nenhum valor real é
versionado. São obrigatórios, entre outros:

- conexão e credenciais separadas de runtime e migration;
- `REDIS_URL` privado;
- `JWT_SIGNING_KEY` e `APP_PEPPER`;
- `TRUSTED_PROXY_IP_REGEX`, restrito ao proxy reverso conhecido.

Banco e cache não recebem portas públicas em produção. A aplicação ignora
`X-Forwarded-*` por padrão; o profile `prod` habilita o suporte nativo do Tomcat
somente para endereços que correspondam à expressão de proxies confiáveis.

## Promoção e rollback

O mesmo artefato deve atravessar teste, homologação e produção. Mude apenas
configuração externa.

```bash
# exemplo: promover a versão aprovada
docker pull registry.example/crm-pnp-backend:0.0.1-793777d
docker image inspect registry.example/crm-pnp-backend:0.0.1-793777d

# rollback: reapontar o deploy para a última tag aprovada
docker pull registry.example/crm-pnp-backend:0.0.0-abc1234
```

O mecanismo efetivo de deploy será definido no Prompt 24. Até lá, o contrato é
não sobrescrever tags e conservar a última tag aprovada para rollback.

## Verificação mínima antes de promover

```bash
docker image inspect crm-pnp-backend:<tag>
docker run --rm --entrypoint id crm-pnp-backend:<tag>
docker scout cves crm-pnp-backend:<tag>
```

Além do scan de vulnerabilidades, verifique a ausência de segredos, execute a
suíte do backend e valide liveness/readiness contra uma inicialização limpa.
Vulnerabilidade crítica sem correção ou tratamento bloqueia a promoção.
