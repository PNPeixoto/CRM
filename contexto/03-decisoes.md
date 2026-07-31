# Registro de decisões

> **Append-only.** Nunca edite nem apague uma entrada.
> Decisão revertida entra como entrada NOVA referenciando a antiga.
>
> Formato:
>
> ## AAAA-MM-DD — Título
> **Decisão:** o que foi escolhido
> **Alternativas descartadas:** o que foi considerado e por que não
> **Consequência:** o que isso fecha ou obriga daqui em diante

## 2026-07-27 — Monólito modular em vez de microsserviços

**Decisão:** Spring Modulith, aplicação única, fronteira por pacote.
**Alternativas descartadas:** microsserviços — custo operacional
incompatível com equipe de uma pessoa.
**Consequência:** fronteira só existe se for verificada no CI
(`ApplicationModules.verify()`).

## 2026-07-27 — Registro central de rotas com status por página

**Decisão:** `app/routes.ts` alimenta roteador e navegação, com campo
`status` controlando o placeholder "em produção".
**Alternativas descartadas:** rotas espalhadas pelos componentes — a
navegação sairia do ar com o roteador sem ninguém perceber.
**Consequência:** adicionar página é adicionar uma linha; nunca duplicar
a lista de rotas em outro lugar.

## 2026-07-27 — Modo claro como padrão, tokens semânticos preparados para escuro

**Decisão:** modo claro é o padrão. Os design tokens nascem nomeados por
**papel** (`--surface-base`, `--surface-raised`, `--text-muted`,
`--border-subtle`), nunca por valor (`--gray-100`). A paleta escura é
definida junto, mas o seletor de tema só é entregue depois do P0.

**Alternativas descartadas:**
- *Escuro como padrão* — contraria o briefing de UX sem ganho técnico, e
  quebra o white label: `#4B2ED4` dá 7,95:1 sobre branco (AAA) e 2,32:1
  sobre `#15121F` (reprova AA). Cor escolhida livremente pelo tenant não
  tem como ter variante clareada pré-computada.
- *Claro apenas, sem estrutura para escuro* — economiza pouco agora e
  transforma "adicionar escuro" em reescrita da paleta inteira.

**Premissa corrigida no caminho:** o registro anterior de que "o protótipo
usa fundo escuro" estava errado. As 12 ocorrências de `#15121F` no
protótipo são sidebar, painel do login, moldura do mockup mobile e preview
de white label. O canvas é `#F6F6FA` com texto `#17171F`. O protótipo já
era claro com shell de navegação escuro. Não havia conflito com o briefing.

**Consequência:** nenhum hex literal em componente. Cor que não existir
como token vira token antes de ser usada. Toda cor semântica (sucesso,
erro, alerta, informação) precisa de par claro/escuro definido no momento
em que entra na paleta, não depois.

## 2026-07-27 — Cor principal `#4B2ED4`

**Decisão:** a cor principal é `#4B2ED4` (índigo), a mesma já aplicada em
todas as telas do protótipo.

**Alternativas descartadas:** `#6D4EF0`, registrado em `00-projeto.md` §7 —
dá 5,25:1 sobre branco: passa WCAG AA para texto normal, reprova AAA e fica
marginal em botão primário com texto branco. `#4B2ED4` dá 7,95:1 (AAA).
Pelo princípio "segurança e robustez antes de estética", vence o contraste.

**Consequência:** `00-projeto.md` §7 corrigido. `#6D4EF0` não deve reaparecer
em nenhum lugar. A variante do índigo para superfície escura ainda precisa
ser calculada quando a paleta escura for gerada — `#4B2ED4` puro não serve
sobre fundo escuro.

## 2026-07-27 — WhatsApp pela Cloud API oficial da Meta

**Decisão:** o canal WhatsApp usa a **Cloud API oficial**. A bridge não
oficial está descartada em definitivo, não adiada.

**Alternativas descartadas:** bridge por leitura de QR Code
(Baileys / WhatsApp Web). Funcionaria local hoje, sem burocracia e sem custo
por mensagem, mas viola os termos de uso da Meta e expõe o número do cliente
a banimento sem aviso. Para uma plataforma vendida a franqueadoras, o cliente
perder o número por causa da nossa escolha é evento do qual o produto não se
recupera. O risco é do cliente e o ganho é nosso — assimetria inaceitável.

**Consequência:**
- Passa a existir pré-requisito comercial e jurídico: CNPJ, Meta Business
  verificado, WABA, número dedicado e token de system user permanente.
  Sem isso o canal não conecta, e isso não é contornável por código.
- Custo por mensagem entra no produto. A Meta cobra por template entregue
  desde 01/07/2025, e **a partir de 01/10/2026** passa a cobrar também as
  respostas livres dentro da janela de 24h, hoje gratuitas. Faltam ~2 meses.
  Medição de custo por tenant deixa de ser P1 e vira requisito de nascença
  do módulo `channel` — sem ela não há como repassar nem limitar consumo.
- A janela de 24h precisa ser estado conhecido e exibido: fora dela só
  template aprovado. O composer bloqueia texto livre com a janela fechada.
- Ordem de construção preservada: chat ao vivo (P0) primeiro, porque valida
  conversa, fila, atribuição, tempo real e histórico sem depender de
  aprovação de terceiro. O WhatsApp entra pela mesma porta `ChannelAdapter`.

**Referência:** ver `PROMPT-PROXIMA-SESSAO.md` para o checklist de
pré-requisitos da Meta e o faseamento.

## 2026-07-28 — Sem `spring-boot-docker-compose`; o compose da raiz é o único

**Decisão:** a dependência `spring-boot-docker-compose` sai do `pom.xml` e o
`backend/compose.yaml` gerado pelo Spring Initializr é apagado. O
`docker-compose.yml` da raiz passa a ser o único descritor de ambiente, subido
manualmente.

**Alternativas descartadas:** manter a dependência e apenas apagar o
`compose.yaml`. Funcionaria hoje, mas a dependência procura o arquivo por
convenção — qualquer `compose.yaml` que reapareça no diretório do módulo volta
a sequestrar `spring.datasource.*` em silêncio. O arquivo do Initializr
declarava `mydatabase`/`myuser`/`secret` e `postgres:latest`: a aplicação
conectaria num banco vazio e errado **sem erro visível**, porque a
sobrescrita é comportamento documentado, não falha.

**Consequência:** subir o ambiente vira passo explícito
(`docker compose up -d` na raiz) e a configuração de conexão passa a vir só de
`application.yml` + variáveis de ambiente. Trocamos conveniência por
previsibilidade — é o lado certo para uma origem de dados.

## 2026-07-28 — Nome do pacote `br.com.pnp.crm` confirmado como definitivo

**Decisão:** `br.com.pnp.crm` e `groupId` `br.com.pnp` ficam. Confirmado antes
da primeira migration, que era o ponto de não-retorno barato.

**Alternativas descartadas:** renomear para um nome comercial definitivo.
Adiado em 2026-07-27 e agora encerrado — o custo do rename cresce a cada
tabela criada e a cada referência em migration aplicada.

**Consequência:** a partir da V1 aplicada, renomear o pacote deixa de ser
`find/replace` e passa a exigir migration de compatibilidade. Se o nome
comercial mudar, o pacote não precisa acompanhar: nome de pacote é endereço
interno, não marca.

## 2026-07-28 — BouncyCastle fixo em 1.85 e OAuth2 Resource Server só pelo JWT

**Decisão:** `org.bouncycastle:bcprov-jdk18on` entra com versão fixa
(`1.85`, propriedade `bouncycastle.version`) e
`spring-boot-starter-oauth2-resource-server` entra sem que nenhum fluxo OAuth2
seja configurado.

**Alternativas descartadas:**
- *Deixar o BouncyCastle sem versão* — impossível: o BOM do Spring Boot 4.1
  **não** gerencia BouncyCastle (verificado no `spring-boot-dependencies`), o
  build não resolveria.
- *Declarar `nimbus-jose-jwt` sozinho* — traz a biblioteca de JOSE mas não a
  infraestrutura. Sobraria escrever à mão validação de assinatura, expiração,
  `nbf` e conversão para `Authentication` — código de segurança novo,
  sem testes, para economizar uma dependência.

**Consequência:** o `Argon2PasswordEncoder` só falha em **tempo de execução**
se o BouncyCastle sumir do classpath — a aplicação sobe normalmente e quebra
no primeiro login. Remover essa dependência exige rodar o teste de login.
E o resource server fica configurado com `JwtDecoder` próprio: se alguém
adicionar `issuer-uri` ao `application.yml`, o Spring passa a buscar
configuração remota de um provedor OAuth2 que não existe.

## 2026-07-28 — O login identifica o tenant por slug digitado

**Decisão:** a tela de login tem três campos: **empresa** (slug), login e
senha. O slug é traduzido em `tenant_id` no backend.

**Alternativas descartadas:**
- *Resolver por subdomínio (host)* — mais elegante e é o destino final, mas
  hoje não existe domínio por tenant, e em `localhost` seria preciso um
  mapeamento especial só para desenvolvimento. Configuração que só existe em
  dev é a que vaza para produção.
- *Login globalmente único* — eliminaria o campo, mas obrigaria o segundo
  cliente a descobrir que "joao" já foi levado por um cliente que ele nem
  sabe que existe. Vaza a existência de outros tenants e engessa a adoção.

**Isto não viola "tenant_id nunca vem do cliente".** O que o cliente informa
é um apelido público, não um identificador de autorização, e ele não concede
nada sozinho — quem concede é a senha. O `tenant_id` real nunca entra na
requisição.

**Consequência:** `tenant.slug` passa a ser identificador público e estável.
Trocar o slug de um cliente derruba o login de todos os usuários dele, então
ele não pode acompanhar mudança de nome comercial. Quando o subdomínio
existir, o frontend preenche o campo automaticamente a partir do host e a API
não muda.

## 2026-07-28 — RLS forçado, aplicado na obtenção da conexão

**Decisão:** toda tabela com `tenant_id` recebe `ENABLE` **e** `FORCE ROW
LEVEL SECURITY`. O `app.tenant_id` é definido em **toda** conexão entregue
pelo pool, por um `DataSource` delegante (`TenantAwareDataSource`), inclusive
com valor vazio quando não há tenant no contexto.

**Alternativas descartadas:**
- *Só `ENABLE`, sem `FORCE`* — armadilha silenciosa: o usuário `crm` é dono
  das tabelas, e o Postgres **ignora toda política para o dono** a menos que
  `FORCE` esteja ativo. O isolamento pareceria configurado e não existiria.
- *Aspecto sobre `@Transactional`* — só cobre o que passa pelo aspecto. Uma
  consulta fora de transação, um `JdbcTemplate` direto ou um repositório que
  alguém esqueceu de anotar escapariam. A obtenção da conexão é o único ponto
  por onde tudo passa obrigatoriamente.

**Consequência:** conexão sem tenant no contexto enxerga **zero** linhas, e
não as do usuário anterior do pool — falha fechada. Em troca, toda operação
legítima precisa de tenant no contexto, inclusive migrations de dados: o seed
de `db/dev` faz `SET LOCAL app.tenant_id` antes de cada bloco de INSERT. Uma
migration que só funcione com RLS desligado é a migration que está errada.

## 2026-07-28 — Duas funções `SECURITY DEFINER` para o que antecede o tenant

**Decisão:** `resolve_tenant_id_por_slug(text)` e
`resolve_tenant_id_por_refresh_hash(text)` são `SECURITY DEFINER` com
`search_path` fixo. São o único caminho para obter um `tenant_id` sem
contexto de tenant.

**Por que precisam existir:** login e refresh acontecem **antes** de haver
tenant. Com RLS ativo na tabela `tenant`, um SELECT normal devolveria vazio,
e nenhum dos dois fluxos funcionaria.

**Alternativas descartadas:**
- *Deixar `tenant` fora do RLS* — expõe a carteira de clientes da plataforma
  a qualquer consulta que escape do filtro em código.
- *Embutir o `tenant_id` no valor do cookie de refresh* — funcionaria e seria
  auto-verificável, mas criaria um lugar no sistema onde `tenant_id` vindo do
  cliente é aceito. Uma exceção documentada hoje é a regra esquecida amanhã.

**Consequência:** duas funções privilegiadas passam a existir e precisam ser
tratadas como superfície de segurança. Cada uma responde a uma única pergunta
e devolve só um uuid — não listam, não aceitam filtro, não expõem atributo.
`search_path` fixo é obrigatório nelas: sem isso, o chamador cria uma tabela
`tenant` num schema próprio e faz a função privilegiada ler o objeto dele.
**Nenhuma função `SECURITY DEFINER` nova entra no projeto sem entrada aqui.**

## 2026-07-28 — Pepper adotado no Argon2id

**Decisão:** o `PasswordEncoder` concatena `APP_PEPPER` à senha antes do
Argon2id. A aplicação **não sobe** sem a variável definida — sem valor padrão
em `application.yml`.

**Alternativas descartadas:** não usar pepper, o que manteria válido o hash
de seed já calculado. O ganho do pepper é assimétrico demais para abrir mão:
o salt vive no banco ao lado do hash, e quem leva o dump leva os dois; o
pepper nunca esteve lá.

**Consequência, e ela é séria:** o pepper **não é rotacionável**. Trocá-lo
invalida todas as senhas existentes de uma vez e exige redefinição por todos
os usuários — não é rotação transparente. O valor de produção precisa ser
tratado como dado de sobrevivência do sistema: perdê-lo equivale a perder
todas as senhas. Um default em `application.yml` seria herdado em silêncio em
produção, e pepper público não é pepper — daí a ausência deliberada de
padrão, com o profile `dev` fornecendo o seu em `application-dev.yml`.

## 2026-07-28 — Access token de 15 min sem revogação; refresh rotativo por família

**Decisão:** access token JWT HS256 de 15 minutos, **sem** lista de revogação.
Refresh token de 256 bits guardado só como SHA-256, rotacionado a cada uso,
agrupado por `family_id`. Apresentar um token já rotacionado revoga a família
inteira.

**Alternativas descartadas:**
- *Blacklist de access token* (padrão do FinUp) — devolve a cada requisição o
  custo de consulta que o JWT existe para evitar, e só reduz a janela de abuso
  de 15 minutos para segundos.
- *Argon2 no refresh token* — o segredo já tem 256 bits de entropia; não há
  dicionário a encarecer, e o refresh roda a cada 15 minutos.

**Consequência:** um access token roubado vale por até 15 minutos e não há
como cortá-lo antes disso — o corte imediato é do refresh. E a revogação de
família **derruba a vítima junto com o atacante**: não há como distinguir os
dois, e por isso o comportamento correto é logout dos dois. O frontend precisa
serializar refreshes concorrentes, senão várias abas disparam rotações
simultâneas e o próprio sistema se interpreta como roubo.

## 2026-07-28 — Tema claro fixo até o seletor existir

**Decisão:** a paleta escura está definida em `index.css` mas **não** é
ativada por `prefers-color-scheme`. Só entra com `data-theme="dark"` no
elemento raiz.

**Alternativas descartadas:** seguir a preferência do sistema operacional.
Foi como ficou na primeira implementação e contrariava a decisão de
2026-07-27: entregaria o tema escuro agora, sem o usuário pedir e sem seletor
para ele voltar atrás.

**Consequência:** o bloco `:root[data-theme="dark"]` fica **fora** de
`@layer`, porque o bloco `:root` do tema claro também está — regra sem camada
vence regra em camada independentemente de especificidade, e dentro de
`@layer base` o tema escuro seria silenciosamente ignorado. Quando o seletor
for construído, ele só precisa escrever o atributo; o CSS não muda.

## 2026-07-29 — Telegram antes do WhatsApp como primeiro provedor externo

**Decisão:** a ordem de canais passa a ser chat ao vivo → **Telegram** →
WhatsApp Cloud API. O que era Fase 3 (WhatsApp) vira Fase 4.

**O motivo principal não é o Telegram ser mais fácil.** É a regra da
abstração deste projeto: só abstrair com o segundo caso de uso concreto na
mão. O `LiveChatAdapter` **não é um provedor externo de verdade** — não tem
webhook, não tem `external_id` de terceiro, não tem API de mídia, não reenvia
evento. Desenhar a porta `ChannelAdapter` olhando só para ele significa
abstrair a coisa errada, e a descoberta viria no meio da integração com a
Meta.

O Telegram é o primeiro provedor real e o mais barato de errar: webhook
autenticado, `external_id` externo, mídia com link expirável e reenvio de
evento. Errar a porta ali custa uma tarde; errar com o WhatsApp custa a fase
inteira.

**Alternativas descartadas:**
- *Manter o WhatsApp como primeiro provedor* — trava o cronograma técnico nos
  pré-requisitos comerciais da Meta (CNPJ, Business verificado, WABA, número
  dedicado, cartão cadastrado), que levam semanas e não são contornáveis por
  código.
- *Ir direto do LiveChat ao WhatsApp* — deixaria a porta validada por um único
  caso concreto, e um que não exercita webhook nem mídia externa.

**Consequência:**
- O Telegram **não exercita** quatro coisas que voltam na Fase 4: HMAC sobre o
  corpo cru (com o detalhe de precisar dos bytes antes do Jackson), a máquina
  de estados da janela de 24h, o ciclo de aprovação de template e a medição de
  custo por tenant. Nenhuma delas fica mais barata por causa desta ordem.
- **Risco assumido:** o Telegram não é o canal comercial do produto —
  franquia brasileira vive no WhatsApp. Esta é reordenação de construção, não
  de prioridade de produto, e o perigo é ela fazer o WhatsApp parecer distante.
  Mitigação: a verificação do Meta Business começa **em paralelo, agora**,
  justamente porque leva semanas. O Telegram ocupa o tempo de espera.
- O webhook do Telegram usa `secret_token` no header
  `X-Telegram-Bot-Api-Secret-Token`, não HMAC. Comparação em tempo constante
  do mesmo jeito.
- *Long polling* (`getUpdates`) fica **descartado** mesmo em desenvolvimento,
  embora dispensasse tunnel: criaria dois caminhos de entrada, e o de produção
  seria o menos testado.

## 2026-07-29 — Terceira função `SECURITY DEFINER`, para a entrada de mensagem

**Decisão:** `resolve_tenant_id_por_channel_connection(uuid)` entra na V2,
com `search_path` fixo, seguindo a regra criada em 2026-07-28 de que nenhuma
função privilegiada entra sem registro aqui.

**Por que precisa existir:** a ingestão de mensagem acontece **sem sessão**.
O webhook do Telegram é chamado pelo provedor, e o visitante do chat ao vivo
não é usuário do CRM. Nos dois casos o único dado disponível é a conexão de
canal, que está sob RLS — um SELECT normal devolveria vazio e nenhuma
mensagem entraria no sistema.

**Alternativas descartadas:**
- *Deixar `channel_connection` fora do RLS* — a tabela lista por qual número e
  qual bot cada cliente atende; é informação de cliente e precisa de
  isolamento como qualquer outra.
- *Colocar o `tenant_id` na URL do webhook* — voltaria a aceitar tenant vindo
  de fora, exatamente o que foi recusado no refresh token.

**Consequência:** a função resolve **roteamento, não autorização**. A
autenticidade da requisição continua vindo da assinatura do provedor
(`secret_token` no Telegram, HMAC na Meta) e nunca desta função — se algum
dia alguém tratar "a função respondeu" como "a requisição é legítima", o
endpoint de webhook vira porta aberta. A brecha é estreita: recebe um uuid de
conexão e devolve um uuid de tenant, e uuid v7 não é enumerável na prática.

## 2026-07-29 — Uma conversa ativa por interlocutor, garantida pelo banco

**Decisão:** índice único parcial em
`conversation (channel_connection_id, external_contact_id)` restrito a
`status <> 'CLOSED'`.

**Alternativas descartadas:** checar em código antes de inserir. Não resolve:
quando alguém manda três mensagens seguidas — o caso normal, não o raro — as
transações concorrentes leem "não existe" antes de qualquer uma inserir, e o
atendimento aparece dividido em duas conversas na tela. Só o banco arbitra
corrida.

**Consequência:** reabrir atendimento com um contato exige encerrar a conversa
anterior (`status = 'CLOSED'`), senão a inserção falha. O código de ingestão
precisa tratar a violação de unicidade como "alguém criou primeiro, use a
existente", e não como erro.

## 2026-07-29 — `ChannelAdapter` com dois métodos apenas

**Decisão:** a porta expõe `tipo()` e `enviar()`. Recebimento **não** está na
interface: cada adaptador expõe o próprio endpoint de webhook, com a
autenticação daquele provedor, e traduz para `InboundMessage`.

**Alternativas descartadas:**
- *Um método `receber()` genérico* — teria que aceitar o formato de todos os
  provedores, que é o oposto de normalizar.
- *Consulta de janela de atendimento na porta* — só o WhatsApp tem janela de
  24h. Telegram e chat ao vivo seriam obrigados a implementar um conceito que
  não existe no provedor deles.

**Consequência:** quando o WhatsApp entrar (Fase 4), a janela de 24h será
modelada onde ela existe, e não empurrada para dentro da porta. Se algum dia
um segundo provedor tiver janela, aí sim vira conceito da porta — com o
segundo caso concreto na mão, como a regra manda.

## 2026-07-29 — Quarta função `SECURITY DEFINER`, e a previsão anterior estava errada

**Correção de registro.** A entrada de hoje sobre a terceira função dizia
"terceira e — pela previsão de hoje — última". Errado: o worker da fila de
saída exigiu uma quarta, `reservar_mensagens_para_envio`. Fica a lição de não
declarar teto para uma categoria que ainda está crescendo.

**Decisão:** o worker reserva mensagens por função `SECURITY DEFINER` que
devolve apenas `(message_id, tenant_id)`, usando `FOR UPDATE SKIP LOCKED`.

**Por que precisa existir:** o worker roda em segundo plano, sem sessão e sem
tenant no contexto — com RLS ativo ele enxergaria zero linhas e nenhuma
mensagem sairia. E ele não pode escolher um tenant antes de consultar, porque
descobrir *em quais tenants há mensagem pendente* é exatamente o que ele
precisa fazer.

**Alternativas descartadas:**
- *Papel de banco com `BYPASSRLS` para tarefas de fundo* — é a resposta de
  manual e foi seriamente considerada. Descartada porque cria uma conexão que
  ignora **todas** as políticas de **todas** as tabelas. A função ignora o RLS
  de uma tabela só, para uma pergunta só, e devolve só identificadores; o
  conteúdo é lido depois, já sob a política do tenant certo.
- *Varrer tenant a tenant* — a tabela `tenant` também está sob RLS, então o
  problema apenas se desloca.

**Consequência:** `FOR UPDATE SKIP LOCKED` passa a ser o que sustenta o
escalonamento horizontal do envio — sem ele, duas instâncias reservam as
mesmas linhas e o cliente final recebe a mensagem duplicada. E o contador de
tentativa é incrementado na **reserva**, não na conclusão: um processo que
morre no meio do envio ainda consome uma tentativa, senão uma mensagem que
derruba o worker seria reprocessada para sempre, travando a fila atrás dela.

## 2026-07-29 — Fila morta sem estado próprio

**Decisão:** a "fila morta" é o teto de tentativas na cláusula da consulta
(`attempt_count < p_max_tentativas`), não um novo valor de `status`.

**Alternativas descartadas:** um status `DEAD` ou uma tabela separada. Ambos
exigiriam uma transição de estado extra que pode falhar, e criariam a
pergunta "o que aparece na conversa quando a mensagem está morta". Com o teto,
a mensagem permanece `FAILED` — que é o que o atendente precisa ver — e
simplesmente deixa de ser candidata.

**Consequência:** reprocessar uma mensagem morta é zerar `attempt_count`, não
mudar status. Quando existir tela de reenvio, é essa a operação. E o número de
tentativas deixa de ser detalhe interno: ele é a fronteira entre "vai tentar
de novo" e "não vai mais", então mudá-lo em produção reabre mensagens que já
estavam encerradas.

## 2026-07-29 — Credencial de canal cifrada com AES-256-GCM e chave própria

**Decisão:** credenciais de canal (token do bot, segredo do webhook, tokens da
Meta) vivem em `channel_credential`, cifradas com AES-256-GCM. A chave é
`CHANNEL_SECRET_KEY`, **separada** de `APP_PEPPER` e `JWT_SIGNING_KEY`.

**Alternativas descartadas:**
- *AES-CBC* — não é autenticado. Quem tiver escrita no banco altera bytes do
  texto cifrado e a aplicação decifra lixo sem perceber. GCM detecta.
- *Reaproveitar `APP_PEPPER` ou `JWT_SIGNING_KEY`* — faria um único vazamento
  comprometer senhas, sessões e credenciais de integração de uma vez.
- *Coluna em `channel_connection`* — a conexão é lida em toda ingestão de
  mensagem; o segredo só é necessário no envio. Em tabela separada, a consulta
  comum nunca carrega material sensível para a memória.

**Consequência:** `key_version` existe em cada linha para permitir rotação
incremental — linhas antigas continuam decifráveis pela chave antiga enquanto
as novas já usam a nova. E o que isto **não** protege: quem tem execução no
servidor lê a variável e decifra tudo. A proteção é contra vazamento de dump,
que é o incidente comum.

## 2026-07-29 — Gravar o evento antes de responder 200

**Decisão:** o webhook grava em `inbound_event` **antes** de devolver `200`,
invertendo a ordem literal do enunciado do projeto ("responder 200
imediatamente, depois gravar o evento cru").

**Motivo:** responder primeiro significa que uma falha na gravação perde a
mensagem para sempre — o Telegram já recebeu a confirmação e não vai
reenviar. Gravar é uma inserção indexada, na casa de milissegundos, muito
abaixo do limite que levaria o provedor a reenviar.

**O que continua fora do request** é o **processamento**, que é a parte lenta
e a que o enunciado realmente quer evitar: interpretar dentro do request faz
um payload inesperado derrubar a resposta, o provedor reenviar e, depois de
algumas falhas, desativar o webhook — o canal inteiro cai por causa de um
formato não previsto.

**Consequência:** `inbound_event` é a única cópia do payload original, porque
o provedor não guarda. É o que permite corrigir o tradutor e reprocessar.

## 2026-07-29 — Comunicação entre `channel` e `conversation` por evento

**Decisão:** o módulo `channel` publica `MensagemNormalizadaEvent`;
`conversation` escuta. `channel` não conhece `conversation`.

**Por que não chamada direta:** `conversation` já depende de `channel.api`
(usa `InboundMessage`, `ChannelAdapter`, `TipoConteudo`). Uma chamada de volta
criaria ciclo entre os módulos, e `ApplicationModules.verify()` reprovaria —
com razão, porque o ciclo impede entender ou extrair qualquer um dos dois
isoladamente.

**Consequência:** o listener é **síncrono** de propósito. O evento nasce
dentro do worker de entrada, que precisa saber se a ingestão funcionou para
decidir entre marcar processado ou deixar o evento voltar. Com listener
assíncrono, o worker marcaria sucesso sem saber o resultado.

## 2026-07-29 — Quinta função `SECURITY DEFINER`, e o gatilho para revisitar

**Decisão:** `reservar_eventos_para_processar` entra na V4, mesma forma da
reserva da fila de saída.

**Registro do padrão:** são cinco. Todas pelo mesmo motivo — processo ou
requisição sem sessão precisando descobrir o tenant. **Se aparecer uma sexta,
a decisão a revisitar é adotar um papel de banco com `BYPASSRLS` para tarefas
de fundo**, em vez de continuar multiplicando funções privilegiadas. O
argumento contra o `BYPASSRLS` (blast radius de todas as tabelas) enfraquece à
medida que o número de funções cresce, porque a superfície somada delas se
aproxima do mesmo efeito com mais lugares para auditar.

## 2026-07-29 — Empresa e login insensíveis a maiúsculas

**Decisão:** o slug do tenant e o login do usuário são comparados sem
distinguir maiúsculas. Os índices únicos em `app_user` passam a ser sobre
`lower(login)` e `lower(email)`; o slug é normalizado na entrada, porque a
coluna já tem CHECK que só aceita minúsculas.

**Motivo, e ele veio de uma pergunta real:** com busca exata, quem digitasse
"PNP" ou "Peixoto" recebia exatamente a mesma resposta de quem errou a senha.
A resposta uniforme existe para impedir enumeração de contas — mas, sem esta
correção, ela vira beco sem saída para o usuário legítimo, que não tem como
descobrir que o problema era só a caixa da letra. Segurança bem-feita não
pode virar armadilha de usabilidade.

**Alternativas descartadas:** afrouxar só a consulta
(`lower(slug) = lower(:slug)`) sem mexer no índice. Uma função aplicada à
coluna faz o planejador ignorar o índice comum — a consulta de login viraria
varredura de tabela a cada tentativa, que é exatamente o caminho que um ataque
de força bruta percorre. Pior: sem índice sobre `lower(login)`, "Joao" e
"joao" poderiam coexistir como dois usuários no mesmo tenant.

**Consequência, e é a parte que quase passou:** a chave de bloqueio
progressivo no Redis usa o login. Com busca insensível e chave sensível,
"peixoto" e "Peixoto" alcançam o mesmo usuário mas contam em contadores
diferentes — o bloqueio por conta seria contornável apenas alternando a
grafia a cada tentativa. A normalização da chave é obrigatória e precisa
acompanhar a da busca; **mudar uma sem a outra reabre o furo.**
