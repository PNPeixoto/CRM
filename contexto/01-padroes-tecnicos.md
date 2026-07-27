# CRM PNP — Padrões Técnicos

> Complementa o documento de descrição do projeto. Aquele define **o que** é o
> produto; este define **como** ele é construído. Em conflito entre os dois,
> vale a seção de princípios inegociáveis da descrição.

---

## 1. Princípios de escrita de código

### Regras gerais

- **Nome revela intenção.** `calcularTaxaEntrega()` e não `calc()`. Se o nome
  precisa de comentário para ser entendido, o nome está errado.
- **Método faz uma coisa.** Se o nome tem "e" no meio, são dois métodos.
- **Sem número mágico.** Constante nomeada ou configuração.
- **Sem comentário que explica o quê.** Comentário só para explicar *por quê* —
  regra de negócio não óbvia, decisão contraintuitiva, workaround de bug de
  terceiro (com link).
- **Falhar cedo e alto.** Validar na entrada do método, retornar cedo, evitar
  aninhamento profundo.
- **Imutabilidade por padrão.** `record` para DTOs e value objects, campos
  `final`, coleções defensivas na saída.
- **Nunca retornar `null` de método público.** `Optional`, coleção vazia ou
  exceção de domínio.
- **Exceção de domínio, não genérica.** `ContatoDuplicadoException`, não
  `RuntimeException`. Handler global traduz para resposta HTTP.

### Camadas

```
controller  →  application (use case)  →  domain  →  repository
```

- **Controller** só faz: receber DTO, validar formato, chamar caso de uso,
  mapear resposta. Zero regra de negócio, zero query.
- **Application** orquestra: transação, autorização, chamada ao domínio,
  publicação de evento.
- **Domain** contém a regra. Não conhece Spring, HTTP nem JPA.
- **Repository** só acessa dados.

Entidade JPA **nunca** é serializada direto na resposta HTTP. DTO de entrada e
DTO de saída são classes separadas — juntar os dois cria *mass assignment*, onde
o cliente manda um campo que não deveria poder alterar.

### Tamanhos como sinal de alerta

Não são regras rígidas, são gatilhos para revisar:

| Item | Sinal de alerta |
|---|---|
| Método | acima de 30 linhas |
| Classe | acima de 300 linhas |
| Parâmetros | acima de 4 (usar objeto de comando) |
| Aninhamento | acima de 3 níveis |

### Regra da abstração

Não criar interface, factory, generic ou camada extra antes de existir o
**segundo caso de uso concreto**. Abstração criada por antecipação quase sempre
abstrai a coisa errada, e desfazer custa mais que criar depois.

---

## 2. Estrutura de pacotes e fronteiras de módulo

Organização **por funcionalidade**, não por camada técnica:

```
br.com.pnp.crm
├── shared/              # tipos comuns, exceções base, tenant context
├── identity/
│   ├── api/             # público para outros módulos (interfaces + DTOs)
│   └── internal/        # implementação, entidades, repositórios
├── conversation/
│   ├── api/
│   └── internal/
└── ...
```

Regras do Spring Modulith:

- Um módulo **só enxerga** o pacote `api` de outro. `internal` é privado.
- Módulo **nunca** injeta repositório ou entidade de outro módulo.
- Comunicação entre módulos por **evento de aplicação**, não chamada direta,
  sempre que a operação puder ser assíncrona.
- Teste de arquitetura (`ApplicationModules.verify()`) roda no CI e quebra o
  build se a fronteira for violada. Sem isso, o monólito modular vira monólito
  comum em poucas semanas e ninguém percebe.

---

## 3. Segurança

### Autenticação

- Senha com **Argon2id** (ou BCrypt custo ≥ 12 se houver limitação de ambiente).
  Nunca MD5, SHA-1, SHA-256 puro.
- Token de acesso **curto** (15 min) + refresh token rotativo, revogável,
  armazenado com hash no banco.
- Refresh token em cookie `HttpOnly`, `Secure`, `SameSite=Strict`. Token em
  `localStorage` é exfiltrável por XSS.
- MFA (TOTP) obrigatório para superadministrador e administrador da
  franqueadora; opcional para os demais.
- Bloqueio progressivo por tentativa de login, por conta **e** por IP.
- Resposta de login errado é sempre idêntica, independentemente de o e-mail
  existir — senão vira enumeração de usuários.

### Autorização

- Verificada **no backend, em toda requisição**, na camada de aplicação. A UI
  esconder o botão é cosmético.
- Checagem em três eixos: **ação** (criar/editar/excluir/exportar), **escopo**
  (tenant/região/unidade/equipe) e **registro** (é dono? é responsável?).
- `tenant_id` vem do token verificado. Se aparecer no body ou na query string,
  é ignorado — e o incidente é registrado na auditoria.
- RLS ativo em todas as tabelas com `tenant_id`, com `SET LOCAL app.tenant_id`
  no início da transação, feito por interceptor central.
- Testes automatizados obrigatórios: usuário do tenant A **não** consegue ler,
  editar nem exportar dado do tenant B, em nenhum endpoint.

### Entrada e saída

- Bean Validation nos DTOs para formato; validação de regra no domínio.
- **Sempre** query parametrizada. Nunca concatenação de SQL, nem em filtro
  dinâmico — usar Criteria API ou builder com bind.
- Ordenação e paginação vindas do cliente: campo de ordenação validado contra
  lista branca. `ORDER BY` com string do usuário é injeção.
- Upload: validar tipo por *magic bytes*, não por extensão nem `Content-Type`;
  limite de tamanho; renomear arquivo; armazenar fora do diretório servido;
  servir por URL assinada e temporária.
- Resposta nunca inclui campo que o perfil não pode ver — filtrar no servidor,
  não no cliente.

### Segredos

- Nunca no código, nunca no repositório, nunca em log, nunca em resposta de API,
  nunca em mensagem de erro.
- No código, apenas o **nome**: `META_APP_SECRET`, `TELEGRAM_BOT_TOKEN`,
  `DB_PASSWORD`, `JWT_SIGNING_KEY`, `AGENT_ENROLLMENT_SECRET`.
- Segredo de integração armazenado no CRM: criptografado em repouso com chave
  separada da aplicação, com rotação prevista.
- Toda credencial de ambiente de teste é marcada explicitamente:
  `# TESTE — TROCAR ANTES DE PRODUÇÃO`.
- Resposta de API externa passa por **sanitização** antes de virar log ou tela —
  ela pode ecoar o token enviado.

### Superfície HTTP

- HTTPS obrigatório, HSTS ativo.
- CORS com lista branca explícita de origens. Nunca `*` com credenciais.
- Cabeçalhos: `Content-Security-Policy`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy`, `X-Frame-Options`.
- Rate limit por IP, por usuário e por tenant, em Redis.
- Mensagem de erro genérica para o cliente; detalhe só no log com ID de
  correlação. Stack trace nunca chega ao navegador.

### Dados pessoais (LGPD)

- Minimização: não coletar o que não se usa.
- Campos sensíveis (CPF, documento) criptografados em repouso, nunca em
  listagem, acesso registrado em auditoria.
- Exportação e exclusão de dados do titular precisam existir como
  funcionalidade, não como favor manual.
- Log **nunca** registra conteúdo de mensagem de cliente por padrão.

---

## 4. Dados e migrations

- **Flyway sempre.** `ddl-auto` fica em `validate` — nunca `update` nem
  `create`.
- Migration é imutável depois de aplicada em qualquer ambiente. Correção é nova
  migration.
- Migration de dados separada de migration de estrutura.
- Toda tabela de negócio: `id`, `tenant_id`, `created_at`, `updated_at`,
  `created_by`, `updated_by`.
- Chave primária: `UUID v7` (ordenável no tempo, sem expor volume) ou `BIGSERIAL`
  interno com UUID público. Nunca expor ID sequencial em URL de API pública.
- Exclusão é **lógica** (`deleted_at`) para entidades de negócio. Exclusão
  física só no expurgo por retenção.
- Dinheiro em centavos (`INTEGER`/`BIGINT`). Timestamp em `TIMESTAMPTZ`, UTC.
- Índice em toda coluna usada em filtro de listagem, sempre começando por
  `tenant_id` nos índices compostos.

---

## 5. Tempo real e escala horizontal

- WebSocket + STOMP para inbox, presença, digitação e notificação.
- **Atenção:** o broker simples embutido do Spring é em memória e não funciona
  com mais de uma instância. Ao escalar horizontalmente, é preciso um broker
  STOMP externo (RabbitMQ com plugin STOMP, por exemplo) ou uma ponte
  pub/sub própria. Decidir isso **antes** de subir a segunda instância — não é
  troca de configuração, muda o desenho.
- WebSocket autentica no *handshake* e reautoriza por assinatura de tópico. Um
  usuário não pode se inscrever em tópico de outro tenant ou de outra unidade.
- Estado de sessão fora da aplicação (Redis), para que qualquer instância possa
  atender qualquer requisição.
- Entrega em tempo real é **otimização**, não fonte da verdade: o cliente
  precisa conseguir recuperar o estado por REST ao reconectar.

---

## 6. Integração de canais

### Modelo normalizado

Todos os canais convergem para o mesmo modelo interno. O domínio de conversa
**não sabe** se a mensagem veio do WhatsApp ou do Telegram.

```
ChannelAdapter (porta)
├── WhatsAppCloudAdapter
├── InstagramAdapter
├── TelegramAdapter
└── LiveChatAdapter
```

Cada adaptador traduz o formato do provedor para `InboundMessage` /
`OutboundMessage` normalizados. Nenhum campo específico de provedor vaza para o
domínio — o que for específico vive em `payload` (JSONB) para diagnóstico.

Tabela de mensagem com `UNIQUE (channel_connection_id, external_id)`. Todos os
provedores reenviam webhook; sem essa constraint, mensagem duplicada na tela é
questão de tempo.

### Recepção de webhook — padrão único

1. Validar assinatura **antes** de ler o corpo como JSON
2. Responder `200` imediatamente
3. Gravar o evento cru em tabela de entrada
4. Processar de forma assíncrona

Provedor que não recebe `200` rápido reenvia e pode desativar o webhook.
Processar dentro do request é o erro clássico aqui.

**Verificação por provedor:**

- **WhatsApp e Instagram (Meta):** HMAC-SHA256 no header
  `X-Hub-Signature-256`, com o app secret. Comparação em **tempo constante**.
  O `GET` de verificação responde ao `hub.challenge` conferindo o
  `hub.verify_token`.
- **Telegram:** `secret_token` definido no `setWebhook`, recebido no header
  `X-Telegram-Bot-Api-Secret-Token`. Sem isso, qualquer um posta no endpoint.
- **Chat ao vivo:** token de sessão próprio, emitido pelo backend, com origem
  validada.

Endpoint de webhook **sem autenticação de sessão** — a autenticidade vem da
assinatura, não do usuário.

### Envio

- Fila de saída (outbox) com worker, **nunca** chamada direta ao provedor dentro
  da transação do pedido/conversa.
- Retry com backoff exponencial + fila morta após N tentativas.
- Rate limit por número/conta conectada, respeitando o limite do provedor.
- Toda mensagem enviada guarda o `external_id` retornado, para casar com os
  eventos de entrega/leitura que chegam depois.

### Mídia

Links de mídia da Meta são temporários e exigem token para download. O fluxo
correto é: baixar assim que o webhook chega → validar tipo e tamanho →
armazenar em storage próprio (S3/R2) → servir por URL assinada. Guardar só o
link do provedor significa perder o anexo em poucos dias.

### Restrições de negócio que a arquitetura precisa refletir

- **Janela de 24h (WhatsApp):** fora dela, só é possível enviar template
  aprovado. O sistema precisa **saber e exibir** se a janela está aberta, e o
  composer precisa bloquear texto livre quando estiver fechada — senão o
  atendente digita, envia e a mensagem falha silenciosamente.
- **Templates** precisam de aprovação prévia da Meta e têm categoria
  (marketing, utilidade, autenticação). Cadastro e sincronização de status de
  aprovação são funcionalidade, não detalhe.
- **Instagram:** janela padrão de 24h, extensível para 7 dias com a marcação de
  agente humano. Exige conta profissional vinculada a uma página do Facebook.
- **Telegram:** sem janela e sem template — é o canal mais simples, e por isso
  bom segundo alvo depois do chat ao vivo.
- **Custo por mensagem precisa ser medido por tenant desde o começo.** Desde
  julho de 2025 a Meta cobra por template entregue, e a partir de 1º de outubro
  de 2026 as respostas livres dentro da janela de 24h, hoje gratuitas, passam a
  ser cobradas. Sem medição por tenant, não há como repassar nem limitar
  consumo, e o custo vira prejuízo direto da plataforma.

### Decisão pendente: API oficial vs. bridge não oficial

Boa parte dos concorrentes brasileiros oferece conexão por leitura de QR Code
(bibliotecas tipo Baileys / WhatsApp Web). É mais barato e sem burocracia, mas
**viola os termos de uso da Meta** e expõe o número do cliente a banimento sem
aviso. A API oficial exige CNPJ, verificação de negócio e custo por mensagem,
mas é estável e defensável contratualmente.

Como a arquitetura usa adaptadores, os dois caminhos cabem na mesma porta. A
decisão é comercial e jurídica, não técnica — mas precisa ser tomada com o risco
explícito, porque o cliente perder o número dele por causa da sua plataforma é
um evento do qual o produto não se recupera.

---

## 7. Agente privado

Componente instalado no ambiente do cliente para que credenciais de sistemas
internos (ERP, por exemplo) nunca cheguem ao servidor central.

- **Conexão sempre de saída.** O agente conecta no CRM; o CRM nunca conecta no
  agente. Isso dispensa abrir porta no firewall do cliente, que é o que faz a
  adoção ser possível.
- Registro por token de *enrollment* de uso único e curta validade. Depois do
  registro, o agente recebe credencial própria e o token é queimado.
- Comunicação por mTLS ou token assinado, sempre sobre TLS.
- Heartbeat periódico; agente sem heartbeat aparece como offline no painel e
  gera alerta.
- O CRM central armazena **apenas a referência** ao segredo
  (`{{secret.ERP_API_KEY}}`), nunca o valor. Resolver a referência é
  responsabilidade do agente, localmente.
- Job tem tempo limite, número máximo de tentativas e resultado **sanitizado**
  antes de subir para o CRM.
- Agente valida a origem do comando: só executa integração previamente vinculada
  a ele. Comando arbitrário vindo do servidor é uma porta de execução remota.
- Versão do agente é reportada; versão desatualizada gera aviso no painel.

---

## 8. Docker

### Imagem da aplicação

- Build **multi-stage**: estágio de compilação com JDK, estágio final só com
  JRE. A imagem final não contém código-fonte, Maven/Gradle nem cache de build.
- Base final enxuta (`eclipse-temurin:25-jre-alpine` ou distroless).
- **Usuário não-root** obrigatório (`USER app`). Container rodando como root é
  escalada de privilégio de graça em caso de falha.
- Camadas do Spring Boot extraídas (dependências antes do código da aplicação) —
  build incremental muito mais rápido.
- `HEALTHCHECK` apontando para o endpoint de readiness.
- `.dockerignore` cobrindo `.git`, `target/`, `.env`, `node_modules`.
- **Nenhum segredo em `ARG`, `ENV` ou camada.** Build arg fica no histórico da
  imagem e é recuperável.
- Tag versionada (`1.4.2`), nunca só `latest` em produção — sem tag fixa não
  existe rollback confiável.
- Limites de CPU e memória definidos; JVM configurada para respeitar o limite do
  container.

### Composição

- `docker-compose` para desenvolvimento: app, PostgreSQL, Redis, MailHog.
- Variáveis de ambiente por arquivo `.env` **fora do versionamento**, com um
  `.env.example` versionado contendo apenas os **nomes** das variáveis.
- Volume nomeado para dados do Postgres; nunca dados de produção em volume
  anônimo.
- Rede interna: só o serviço da aplicação e o proxy ficam expostos. Banco e
  Redis nunca publicam porta para fora do host.

---

## 9. Proxy reverso e balanceamento

- Nginx ou Traefik na frente, com **terminação TLS** e renovação automática de
  certificado.
- Repasse correto dos cabeçalhos de upgrade para WebSocket (`Upgrade`,
  `Connection`) — sem isso o tempo real simplesmente não conecta.
- `X-Forwarded-For` / `X-Forwarded-Proto` repassados, e a aplicação configurada
  para confiar neles **apenas** vindos do proxy. Confiar cegamente permite
  falsificar IP de origem e furar rate limit.
- **Sessão fixa (sticky) por cookie** enquanto o broker STOMP for em memória.
- Health checks separados: *liveness* (o processo está vivo?) e *readiness*
  (está pronto para receber tráfego?). Instância em inicialização não pode
  receber requisição.
- Timeout de WebSocket maior que o intervalo de heartbeat, ou a conexão cai
  sozinha a cada poucos minutos.
- Endpoints de administração e métricas **não** expostos publicamente.

---

## 10. Backup e recuperação

O critério não é "existe backup", é "quanto tempo até voltar e quanto se perde".
Definir e escrever os dois números:

- **RPO** (perda aceitável de dados): alvo inicial ≤ 15 minutos
- **RTO** (tempo até voltar): alvo inicial ≤ 4 horas

### Estratégia

- **Backup lógico diário** (`pg_dump -Fc`) para restauração seletiva.
- **Arquivamento de WAL** para *point-in-time recovery* — é o que permite voltar
  ao minuto anterior ao incidente. Só dump diário significa RPO de 24 horas.
- Backup **criptografado antes de sair do servidor**. A chave de criptografia
  nunca é guardada junto com os backups.
- Cópia **fora do provedor principal**. Backup na mesma conta que foi
  comprometida não é backup.
- Storage com versionamento e bloqueio de objeto, para que ransomware não apague
  o histórico.
- Retenção: 7 diários, 4 semanais, 12 mensais (ajustar conforme contrato).
- Anexos e mídia têm backup próprio — o dump do banco não os contém.

### Regra não negociável

**Backup que nunca foi restaurado não é backup.** Restauração automatizada em
ambiente descartável pelo menos uma vez por mês, validando contagem de linhas e
integridade. O relatório do teste fica registrado.

O script de backup precisa **falhar ruidosamente**: alerta ativo quando não
rodar, quando gerar arquivo menor que o esperado ou quando o teste de restauração
falhar. Backup que falha em silêncio é pior que não ter, porque produz confiança
falsa.

---

## 11. Observabilidade

- Log **estruturado** (JSON), com `trace_id`, `tenant_id`, `user_id` em todo
  registro.
- Nunca logar: senha, token, segredo, conteúdo de mensagem de cliente, dado
  pessoal sensível.
- Métricas via Actuator/Micrometer: latência por endpoint, fila de mensagens,
  falha de integração, conexões WebSocket ativas.
- Alertas que importam de verdade: integração caindo, agente offline, fila
  crescendo, taxa de erro subindo, backup falhando, certificado perto de vencer.
- Auditoria é **append-only** e separada do log de aplicação. Log é
  operacional e expira; auditoria é registro de negócio e não expira.

---

## 12. Testes

Obrigatórios, sem exceção:

1. **Isolamento entre tenants** — tenant A não acessa dado de tenant B em
   nenhum endpoint
2. **Autorização** — cada perfil só faz o que pode
3. **Cálculo de valores** — dinheiro, SLA, métrica de relatório
4. **Idempotência** — webhook e envio repetidos não duplicam
5. **Fronteira de módulos** — `ApplicationModules.verify()` no CI

Testcontainers com PostgreSQL real. Mock de banco esconde exatamente os
problemas que importam: RLS, constraint, índice, transação.

Sem meta de cobertura percentual — cobertura alta em código trivial não protege
nada. O critério é: **toda regra de negócio e toda regra de segurança têm
teste**.

---

## 13. CI/CD

Pipeline mínimo, em ordem, quebrando na primeira falha:

1. Compilação
2. Testes unitários e de integração
3. Verificação de fronteira de módulos
4. Análise estática (SpotBugs/PMD) e formatação
5. Verificação de vulnerabilidade em dependências
6. Verificação de segredo vazado no diff
7. Build da imagem Docker
8. Scan de vulnerabilidade da imagem
9. Deploy em homologação
10. Deploy em produção (aprovação manual)

Migration roda no deploy, antes da subida da nova versão, e precisa ser
compatível com a versão anterior durante a janela de troca — senão não existe
rollback.
