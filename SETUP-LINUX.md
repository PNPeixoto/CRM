# Configuração no Linux (Nobara / Fedora)

Guia para deixar o projeto rodando do zero. Escrito para **Nobara**, que é
baseado em Fedora — os comandos valem para Fedora e derivados.

O Nobara 43 usa **dnf5**, e isso importa: a sintaxe de adicionar repositório
mudou, e é exatamente onde a instalação do Docker costuma quebrar. Está
resolvido abaixo.

> **Bom saber:** a armadilha de `JDK_JAVA_OPTIONS` / AF_UNIX descrita no
> `README.md` é **exclusiva do Windows**. Não configure nada disso aqui.

---

## 0. Confirmar a base

```bash
cat /etc/os-release && dnf --version | head -1
```

Se `dnf --version` mostrar `5.x`, use os comandos marcados **dnf5**. Se
mostrar `4.x`, use os marcados **dnf4**.

---

## 1. Ferramentas básicas

```bash
sudo dnf install -y git curl unzip zip
```

Configure o git, se ainda não estiver:

```bash
git config --global user.name "Seu Nome"
git config --global user.email "seu@email"
```

---

## 2. Clonar o repositório

```bash
git clone https://github.com/PNPeixoto/CRM.git ~/crm-pnp
cd ~/crm-pnp
```

O repositório é privado. Se pedir credencial, use um Personal Access Token do
GitHub como senha — senha de conta não funciona mais para HTTPS.

---

## 3. Java 25

O projeto fixa `java.version=25` no `pom.xml`. JDK menor falha com
`release version 25 not supported`.

### Recomendado: SDKMAN

Instala no diretório do usuário, sem root, e permite fixar a versão exata por
projeto — que é o que evita "funciona na minha máquina" quando o Fedora
atualizar o pacote do sistema.

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem
```

`25-tem` é o Temurin 25, o mesmo usado no Windows. Conferir:

```bash
java -version   # precisa mostrar 25.x
```

### Alternativa: pacote do sistema

```bash
sudo dnf install -y java-latest-openjdk-devel
```

Confira a versão depois — se vier menor que 25, use o SDKMAN. Com vários JDKs
instalados, alterne com:

```bash
sudo alternatives --config java
```

---

## 4. Node 20+

```bash
sudo dnf install -y nodejs npm
node -v   # precisa ser 20 ou maior
```

Se a versão do repositório for antiga, use o nvm:

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.bashrc
nvm install 22
```

---

## 5. Docker

O Fedora não distribui o `docker-ce` nos repositórios oficiais — ele vem do
repositório da Docker.

### 5.1 Remover conflitos

O Fedora traz `podman` e `podman-docker`, que registram um `/usr/bin/docker`
concorrente:

```bash
sudo dnf remove -y docker docker-client docker-common docker-latest \
  docker-logrotate docker-engine podman-docker runc 2>/dev/null || true
```

> Se você prefere manter o Podman, veja a seção "Podman em vez de Docker" no
> final — mas leia antes, porque há uma pegadinha com os testes.

### 5.2 Adicionar o repositório

**dnf5 (Nobara 43+ / Fedora 41+):**

```bash
sudo dnf install -y dnf5-plugins
sudo dnf config-manager addrepo --from-repofile=https://download.docker.com/linux/fedora/docker-ce.repo
```

**dnf4 (versões anteriores):**

```bash
sudo dnf install -y dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
```

> Esta é a linha que mais quebra. O dnf5 trocou `--add-repo <url>` por
> `addrepo --from-repofile=<url>`, e a documentação da Docker ainda mostra a
> forma antiga. Se der erro de sintaxe, você está usando o comando da outra
> versão.

### 5.3 Instalar e habilitar

```bash
sudo dnf install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

sudo systemctl enable --now docker
```

### 5.4 Usar sem sudo

```bash
sudo usermod -aG docker $USER
newgrp docker      # ou faça logout/login
docker run --rm hello-world
```

> **Consciência de segurança:** o grupo `docker` é equivalente a root — quem
> está nele pode montar `/` dentro de um container e escrever em qualquer
> lugar. Em máquina de desenvolvimento pessoal é o custo aceito; em servidor
> compartilhado, prefira rootless ou `sudo`.

---

## 6. Subir o ambiente Docker

```bash
cd ~/crm-pnp
export APP_IMAGE_TAG=0.0.1-dev
export APP_VERSION=0.0.1-dev
export VCS_REF="$(git rev-parse --short HEAD)"
docker compose up --build -d
docker compose ps      # app, postgres e redis precisam aparecer healthy
```

Sobe a imagem versionada do backend na 8080, `postgres:17-alpine` na 5432 e
`redis:7-alpine` na 6380 do host. Tudo é publicado **apenas em `127.0.0.1`**.
A aplicação espera banco e cache ficarem saudáveis, roda como usuário 10001 e
usa filesystem somente leitura.

### SELinux e firewalld

O Fedora vem com os dois ativos, e **nenhum precisa ser desligado** aqui:

- O compose usa volume nomeado, não bind mount, então não há rótulo SELinux a
  ajustar (`:z`/`:Z` seria necessário só com bind mount).
- As portas ficam em loopback, então o firewalld não interfere.

Se algum dia alguém sugerir `sudo setenforce 0` para resolver um problema
deste projeto, a sugestão está errada — investigue a causa.

Conferir que o banco responde:

```bash
docker compose exec postgres psql -U crm_migrator -d crm -c '\l'
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

---

## 7. Variáveis de ambiente

Para **desenvolvimento**, não é preciso configurar nada: o profile `dev`
fornece os valores em `application-dev.yml`, todos marcados
`TESTE — TROCAR ANTES DE PRODUÇÃO`.

No profile `prod`, a aplicação **recusa subir** sem conexões, credenciais,
`APP_PEPPER`, `JWT_SIGNING_KEY` e `TRUSTED_PROXY_IP_REGEX` — não há valor
padrão, de propósito. O Compose deste repositório é somente de desenvolvimento;
consulte `infra/docker/README.md` para promoção e rollback por tag.

Para gerar segredos reais quando chegar a hora:

```bash
cp .env.example .env          # .env está no .gitignore
openssl rand -base64 48       # JWT_SIGNING_KEY (mínimo 32 bytes)
openssl rand -base64 48       # APP_PEPPER
```

> **`APP_PEPPER` não é rotacionável.** Trocá-lo invalida todas as senhas de
> uma vez e exige redefinição por todos os usuários. Guarde o valor de
> produção como dado de sobrevivência do sistema.

---

## 8. Rodar os testes — o passo mais importante

Os testes usam PostgreSQL 17 e Redis reais em containers efêmeros e validam as
migrations, o isolamento e o comportamento da aplicação.

```bash
cd ~/crm-pnp/backend
./mvnw test
```

Os testes usam Testcontainers: eles sobem containers próprios, separados do
`docker compose`. Não é preciso parar o compose.

O que deve passar:

| Teste | O que prova |
|---|---|
| `CrmApplicationTests` | as migrations aplicam e o schema bate com as entidades |
| `IsolamentoEntreTenantsTest` | tenant A não lê, edita nem apaga dado de B |
| `LoginRespostaUniformeTest` | login inválido responde igual, inclusive no tempo |
| `RefreshTokenRotacaoTest` | reuso de refresh token revoga a família |
| `FilaDeSaidaTest` | reserva com `SKIP LOCKED`, backoff e teto de tentativas |
| `FronteiraDeModulosTest` | nenhum módulo alcança o `internal` de outro |

**Se algum falhar, pare e conserte antes de escrever qualquer coisa nova.** As
migrations ainda não foram aplicadas em lugar nenhum, então continuam
editáveis — isso deixa de valer no primeiro `flyway migrate` real.

---

## 9. Rodar fora do container (opcional)

### Backend

```bash
cd ~/crm-pnp
docker compose up -d postgres redis
cd ~/crm-pnp/backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

A variável de ambiente é preferida à propriedade de sistema
(`-Dspring-boot.run.profiles=dev`): é a mesma forma usada em produção e não
depende de como o shell divide argumentos — no PowerShell, aquela propriedade
sem aspas é partida em dois no hífen de `spring-boot`.

O profile `dev` é obrigatório: é ele que carrega o seed de `db/dev` e fornece
os segredos de desenvolvimento.

Conferir:

```bash
curl -s localhost:8080/actuator/health
```

### Frontend

Em outro terminal:

```bash
cd ~/crm-pnp/frontend
npm install
npm run dev
```

Abre em `http://localhost:5174`, com proxy de `/api` para o backend. O proxy
não é conveniência: ele faz o navegador ver tudo na mesma origem, o que
permite o cookie de refresh usar `SameSite=Strict`.

### Entrar

| Campo | Valor |
|---|---|
| Empresa | `pnp` |
| Login | `peixoto` |

Empresa e login são insensíveis a maiúsculas; espaços nas pontas são
descartados.

A senha não está escrita em nenhum arquivo do repositório, por regra do
projeto. Se você não a tiver, gere um hash novo com o encoder configurado e
substitua na migration de seed.

Existe um segundo tenant (`acme`, login `peixoto`) para tornar visível
qualquer falha de isolamento — com um só, uma consulta sem filtro devolve o
mesmo resultado de uma consulta correta.

---

## 10. Problemas comuns

**`release version 25 not supported`**
JDK menor que 25. `java -version` e volte ao passo 3.

**`Cannot connect to the Docker daemon`**
`sudo systemctl status docker`; se estiver parado, `sudo systemctl enable --now docker`.
Se estiver rodando, você provavelmente não recarregou o grupo — faça logout/login.

**`config-manager: unknown option --add-repo`**
Você usou o comando do dnf4 no dnf5. Veja 5.2.

**Testcontainers não encontra o Docker**
```bash
docker context ls          # confirme qual contexto está ativo
echo $DOCKER_HOST          # normalmente vazio no Docker rootful
```
Com Docker rootless, exporte:
```bash
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/docker.sock
```

**Porta 5432 ou 6380 já em uso**
Existe um serviço local ocupando a porta. Identifique-o antes de decidir se
deve pará-lo. Para PostgreSQL do sistema:
```bash
sudo systemctl stop postgresql
```

**Frontend sobe mas a API dá 404**
O backend não está no ar, ou não está na 8080. O Vite só faz proxy.

---

## Podman em vez de Docker

O Podman é o caminho nativo do Fedora e evita o daemon root. Funciona para
subir o compose:

```bash
sudo dnf install -y podman podman-compose
```

**Mas atenção aos testes:** o Testcontainers fala com o socket do Docker.
Com Podman é preciso habilitar o serviço compatível e apontar o cliente:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

`RYUK_DISABLED` é necessário porque o container de limpeza do Testcontainers
costuma falhar sob Podman rootless — em troca, containers órfãos podem
sobrar e precisam de `podman container prune` de vez em quando.

Recomendação: enquanto o objetivo for **rodar os testes com confiança**, use
Docker. O Podman é uma variável a mais para depurar num momento em que o que
se quer validar é o RLS, não o runtime de container.

---

## Próximo passo depois disso tudo

Ver `contexto/02-estado-atual.md` e executar o próximo prompt liberado no
manifesto canônico.
