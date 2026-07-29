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
