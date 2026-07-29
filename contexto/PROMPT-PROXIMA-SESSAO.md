# Prompt de continuação — Claude Code

> Cole o bloco da seção "Prompt" no Claude Code, na raiz do repositório.
> O resto deste arquivo é contexto de apoio, para você e para consulta.

---

## Prompt

```
Leia CLAUDE.md e os três arquivos obrigatórios de contexto antes de qualquer
coisa. Confira o estado real do código: se contradisser 02-estado-atual.md,
o código vence e você corrige o arquivo.

Leia também contexto/05-reaproveitamento-finup.md. Existe um projeto anterior
meu, o FinUp (pasta finup-app-builder), com autenticação e biblioteca de
componentes já resolvidas. Esse arquivo diz item a item o que aproveitar, o
que adaptar e o que NÃO trazer, com o motivo. Siga o veredito de lá; se
discordar de algum, me diga antes de contrariar.

Objetivo desta sessão: autenticação funcionando ponta a ponta e a porta de
canal montada, nesta ordem. Não pule fase. Fase seguinte só começa com a
anterior verde (compila, testes passam, você validou manualmente).

Explique cada escolha técnica enquanto trabalha: o porquê, as alternativas
descartadas e o trade-off aceito. O objetivo aqui é também aprendizado.

FASE 0 — Destravar o ambiente

0.1 Apague backend/compose.yaml. É sobra do Spring Initializr e declara
    mydatabase/myuser/secret. Como spring-boot-docker-compose está no
    classpath, o Spring Boot sobe ESSE arquivo e sobrescreve
    spring.datasource.*, fazendo a aplicação conectar no banco errado sem
    erro visível. Ou apague o arquivo, ou remova a dependência
    spring-boot-docker-compose do pom.xml e use só o docker-compose.yml da
    raiz. Recomendo remover a dependência: controle explícito vale mais que
    conveniência aqui.

0.2 Adicione ao pom.xml, explicando cada uma:
    - org.bouncycastle:bcprov-jdk18on  (Argon2PasswordEncoder exige)
    - spring-boot-starter-oauth2-resource-server  (traz nimbus-jose-jwt e a
      infraestrutura de JwtDecoder — não vamos usar OAuth2, só o JWT)

0.3 Antes de escrever a primeira migration, pare e me avise. Renomear o
    pacote br.com.pnp.crm é trivial agora e caro depois. Quero confirmar o
    nome antes de a primeira migration ser aplicada.

FASE 1 — Autenticação (P0)

Padrão completo do 01-padroes-tecnicos.md. Sem atalho.

1.1 Migrations Flyway, estrutura separada de dados:
    - V1__estrutura_inicial.sql: tabelas tenant, app_user, refresh_token
    - Toda tabela de negócio com id, tenant_id, created_at, updated_at,
      created_by, updated_by, deleted_at
    - Chave primária UUID v7 gerada NA APLICAÇÃO. O docker-compose fixa
      postgres:17-alpine e uuidv7() só existe do Postgres 18 em diante.
      Não use DEFAULT uuidv7().
    - RLS ativo em toda tabela com tenant_id, desde já
    - Índice em toda coluna de filtro, composto sempre começando por tenant_id
    - app_user.login é único por tenant

1.2 Migration de seed, em pasta separada carregada só no profile dev
    (spring.flyway.locations com classpath:db/migration e, só em dev,
    classpath:db/dev). Assim é impossível vazar para produção por esquecimento.

    Usuário de teste, hash Argon2id já calculado com os parâmetros padrão do
    Spring Security (m=16384, t=2, p=1, salt 16B, hash 32B):

    login: peixoto
    hash: $argon2id$v=19$m=16384,t=2,p=1$wPuzkFrp5SPOeq7bq5dcoA$gG+jqTGwZKUcQhQ49sU4ISOLvDQW2Kt/gVNF3vXRyw0

    ATENÇÃO: este hash foi gerado SEM pepper. O FinUp usa Argon2 com pepper
    (APP_PEPPER concatenado à senha antes do hash) e eu recomendo adotar —
    ver 05-reaproveitamento-finup.md. Se adotar, este hash NÃO valida mais e
    você precisa regerar com o encoder já configurado:
        System.out.println(passwordEncoder.encode("12345"));
    Os parâmetros m/t/p não são problema: o Argon2PasswordEncoder lê os
    parâmetros da própria string. O pepper é, porque muda a entrada.

    A senha em texto NUNCA entra em arquivo nenhum. Marque a migration com
    -- TESTE — TROCAR ANTES DE PRODUÇÃO

1.3 Módulo identity. Traga do FinUp (adaptando, ver 05-reaproveitamento):
    SecurityConfig (cabeçalhos CSP/HSTS/nosniff/Referrer-Policy + CORS por
    env), SpaCsrfTokenRequestHandler, CsrfCookieFilter, a hierarquia de
    exceções e o GlobalExceptionHandler. Três correções obrigatórias:
      a) CSP precisa liberar WebSocket: connect-src 'self' ws: wss:
         (em produção, só wss:) — senão o STOMP não conecta na Fase 2
      b) remova o X-XSS-Protection: header depreciado, valor recomendado é 0
      c) cookie com SameSite=Strict, NÃO None. O FinUp usa None porque tem
         origens separadas; nós temos proxy reverso e mesma origem.
    NÃO traga: rate limit e blacklist em memória, InputSanitizer, Lombok,
    nem a estrutura de pacotes por camada.

    - Argon2PasswordEncoder. Avalie adotar o pepper do FinUp (APP_PEPPER em
      variável de ambiente): quem rouba só o dump do banco não consegue
      testar senhas offline. Se adotar, acrescente APP_PEPPER ao .env.example.
    - Access token JWT de 15 min, assinado com JWT_SIGNING_KEY (variável de
      ambiente, só o nome no código)
    - Refresh token rotativo: valor aleatório de 256 bits, armazenado no banco
      SÓ COMO HASH, revogável, com família para detectar reuso. Reuso de
      refresh token já usado revoga a família inteira — é o sinal de roubo.
    - Refresh em cookie HttpOnly, Secure, SameSite=Strict, Path=/api/auth
    - tenant_id sai do token verificado. Se vier no body ou query string,
      ignore E registre na auditoria.
    - Bloqueio progressivo por login e por IP, em Redis
    - Resposta de login inválido IDÊNTICA para usuário inexistente e senha
      errada: mesmo corpo, mesmo status, mesmo tempo de resposta. Compare a
      senha contra um hash dummy quando o usuário não existir, senão o tempo
      de resposta vira enumeração de usuários.

    Camadas: controller recebe DTO e chama caso de uso; application orquestra
    transação e autorização; domain tem a regra; repository só acessa dados.
    DTO de entrada e de saída separados. Entidade JPA nunca serializada na
    resposta.

1.4 Endpoints: POST /api/auth/login, POST /api/auth/refresh,
    POST /api/auth/logout, GET /api/auth/me

1.5 Frontend:
    - Traga os componentes shadcn/ui do FinUp
      (finup-app-builder/frontend/src/components/ui/), SOB DEMANDA — só os que
      a tela de login precisar agora. Não copie a pasta inteira. Remapeie as
      variáveis CSS deles para os tokens semânticos do CRM antes de usar,
      senão você importa a paleta do FinUp junto.
    - Cliente HTTP central: adapte finup-app-builder/frontend/src/lib/api.ts.
      Ele já tem credentials: 'include', cookie CSRF nas mutações, ApiError
      com status e tratamento de 204. Falta acrescentar: no 401, chamar
      /api/auth/refresh UMA ÚNICA VEZ e repetir o request. Sem o "uma única
      vez", refresh expirado vira laço infinito.
    - Access token em memória, NUNCA em localStorage — é exfiltrável por XSS
    - Contexto de autenticação + componente de rota protegida. router.tsx
      hoje deixa todas as rotas abertas.
    - LoginPage.tsx real, consumindo a API. Hoje é só um <h1>.
    - Erro genérico na tela. Detalhe só no log, com id de correlação.
    - Antes de estilizar: crie os tokens semânticos em index.css conforme
      03-decisoes.md (--surface-base, --surface-raised, --text-strong,
      --text-muted, --border-subtle, --brand). Cor principal #4B2ED4.
      Nenhum hex literal em componente.

1.6 Testes obrigatórios, com Testcontainers e Postgres real:
    - usuário do tenant A não lê nem edita dado do tenant B
    - login com senha errada e login com usuário inexistente devolvem
      resposta idêntica
    - refresh token usado duas vezes revoga a família
    - ApplicationModules.verify() passa

PARE AQUI e me mostre o resultado antes de seguir.

FASE 2 — Porta de canal + chat ao vivo (P0)

Só depois da Fase 1 verde.

2.1 Módulo conversation: conversation, message.
    UNIQUE (channel_connection_id, external_id) na tabela de mensagem — todo
    provedor reenvia webhook, e sem essa constraint mensagem duplicada na
    tela é questão de tempo.

2.2 Módulo channel: porta ChannelAdapter com InboundMessage e OutboundMessage
    normalizados. Nenhum campo específico de provedor vaza para o domínio —
    o que for específico vive em payload JSONB, para diagnóstico.

    NÃO crie interface genérica além dessa. Regra da abstração: só abstraia
    com o segundo caso de uso concreto na mão.

2.3 LiveChatAdapter funcional. WebSocket + STOMP, autenticando no handshake
    e reautorizando por assinatura de tópico — usuário não se inscreve em
    tópico de outro tenant. Tempo real é otimização: o cliente precisa
    recuperar o estado por REST ao reconectar.

FASE 3 — WhatsApp Cloud API oficial

Só depois da Fase 2 verde E com os pré-requisitos da Meta resolvidos
(estão listados em PROMPT-PROXIMA-SESSAO.md). Se eu ainda não tiver as
credenciais, construa tudo e teste com payload simulado — a validação de
assinatura é testável offline.

3.1 Recepção de webhook, nesta ordem exata:
    1. validar assinatura ANTES de ler o corpo como JSON
    2. responder 200 imediatamente
    3. gravar o evento cru em tabela de entrada
    4. processar de forma assíncrona
    Processar dentro do request é o erro clássico: provedor que não recebe
    200 rápido reenvia e desativa o webhook.

    GET de verificação: conferir hub.verify_token e devolver hub.challenge.
    POST: HMAC-SHA256 do corpo CRU com META_APP_SECRET, comparado com o
    header X-Hub-Signature-256 em tempo constante (MessageDigest.isEqual).
    Atenção: você precisa dos bytes crus, antes do Jackson. Use
    @RequestBody byte[] ou um filtro que preserve o corpo — se você
    reserializar o JSON parseado, a assinatura nunca vai bater.

    Endpoint de webhook SEM autenticação de sessão: a autenticidade vem da
    assinatura, não do usuário.

3.2 Envio por outbox com worker. Nunca chamada direta ao provedor dentro da
    transação da conversa. Retry com backoff exponencial e fila morta após
    N tentativas. Guardar o external_id retornado, para casar com os eventos
    de entrega e leitura que chegam depois.

3.3 Janela de 24h como estado conhecido e exibido. Fora dela, só template
    aprovado. O composer bloqueia texto livre com a janela fechada — senão o
    atendente digita, envia e a mensagem falha em silêncio.

3.4 Medição de custo por tenant desde a primeira mensagem. Não é P1.
    A Meta cobra por template entregue desde 01/07/2025 e, a partir de
    01/10/2026, passa a cobrar também as respostas livres dentro da janela
    de 24h. Sem medição por tenant não há como repassar nem limitar consumo.

3.5 Mídia: baixar assim que o webhook chega, validar tipo por magic bytes
    (não por extensão nem Content-Type), validar tamanho, renomear, guardar
    em storage próprio e servir por URL assinada. Link da Meta expira em
    poucos dias — guardar só o link é perder o anexo.

REGRAS QUE NÃO SE NEGOCIAM

- Nunca confiar no frontend. Validação, autorização e cálculo sempre no
  backend.
- Segurança antes de estética.
- Segredo só pelo NOME da variável de ambiente, nunca por valor — não no
  código, não em log, não em resposta de API, não em mensagem de erro.
- ddl-auto continua validate. Migration aplicada é imutável; correção é
  migration nova.
- Query sempre parametrizada. Campo de ordenação validado contra lista
  branca — ORDER BY com string do usuário é injeção.
- Nunca retornar null de método público: Optional, coleção vazia ou exceção
  de domínio. Exceção de domínio nomeada, nunca RuntimeException.
- Módulo só enxerga o pacote api de outro. Nunca injetar repositório ou
  entidade de outro módulo.
- Log nunca registra senha, token, segredo nem conteúdo de mensagem de
  cliente.
- Ao tomar decisão que fecha porta (biblioteca, formato de dado, desenho de
  tabela), registre em 03-decisoes.md NO MOMENTO da decisão. É append-only.
- Ao fim: reescreva 02-estado-atual.md por inteiro (máx. 150 linhas) e crie
  contexto/sessoes/AAAA-MM-DD.md.

Se algo que eu pedi criar dívida técnica ou estiver errado, me diga com o
motivo concreto em vez de executar.
```

---

## Apoio: pré-requisitos da Meta (Fase 3)

A Fase 3 não é destravável por código. Sem estes itens o canal não conecta:

| Item | Onde | Observação |
|---|---|---|
| CNPJ ativo | — | pessoa física não faz verificação de negócio |
| Meta Business verificado | business.facebook.com | leva dias; comece cedo |
| App com WhatsApp | developers.facebook.com | permissão `whatsapp_business_messaging` |
| WABA | Business Manager | WhatsApp Business Account |
| Número dedicado | — | não pode estar em uso no app WhatsApp comum |
| Token de system user | Business Manager | permanente; token de teste expira em 24h |
| URL HTTPS pública | ngrok / cloudflared | webhook não aceita http nem localhost |

Variáveis a acrescentar no `.env.example` (só os nomes):

```
META_ACCESS_TOKEN=
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_WABA_ID=
GRAPH_API_VERSION=
```

`META_APP_SECRET` e `META_VERIFY_TOKEN` já estão lá.

Fixe a versão da Graph API em configuração, nunca no código. Confira a
versão corrente na documentação da Meta antes de subir — versão antiga sai
de suporte com aviso curto, e o valor sendo configuração torna a troca uma
mudança de ambiente, não um deploy.

## Apoio: por que esta ordem

O pedido original era login + WhatsApp funcional. O WhatsApp ficou na Fase 3
por três motivos concretos, nenhum deles estético:

1. **`00-projeto.md` §11** proíbe começar P1 com P0 aberto. WhatsApp é P1,
   chat ao vivo é P0.
2. **A espinha dorsal é a mesma.** Conversa, fila, atribuição, tempo real e
   histórico são exercitados pelo chat ao vivo sem depender de aprovação de
   terceiro. Construir isso primeiro significa que, quando o WhatsApp entrar,
   o que resta é o adaptador — e o adaptador é a parte pequena.
3. **A Fase 3 tem dependência externa de dias a semanas** (verificação de
   negócio na Meta). Deixá-la por último é o que impede o projeto de ficar
   parado esperando aprovação.

## Apoio: sobre a senha de teste

`12345` tem 5 caracteres e reprovaria qualquer política razoável. Está aqui
porque você pediu para testar o fluxo, e o hash Argon2id acima é real e
verificado. Duas salvaguardas no desenho:

- a senha em texto não existe em nenhum arquivo do repositório, só o hash
- a migration de seed fica em `db/dev`, carregada apenas no profile `dev`

Antes de qualquer deploy, a política de senha precisa existir de verdade
(comprimento mínimo, verificação contra lista de senhas vazadas) e este
usuário precisa deixar de existir.
