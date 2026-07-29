# Reaproveitamento do FinUp

> Análise do projeto `finup-app-builder` (`com.peixoto.finup`) feita em
> 2026-07-27, para decidir o que entra no CRM PNP e o que não entra.
> Consulta sob demanda, não leitura obrigatória.

## O que é o FinUp

Gestão financeira para pequenos negócios. Spring Boot 3.4 / Java 21 / Gradle
no backend, React 19 + Vite + TanStack Router + shadcn/ui no frontend.
Originalmente Supabase, migrado para backend Java próprio — o escopo de dados
por usuário "substitui o RLS do Supabase" (palavras do README).

É o projeto anterior mais próximo do CRM: mesma linguagem, mesmo framework,
mesma stack de frontend, e já resolveu autenticação inteira.

## Veredito rápido

| Ativo | Veredito | Motivo em uma linha |
|---|---|---|
| Componentes shadcn/ui (46) | **Aproveitar** | maior economia isolada; Radix dá acessibilidade que o §7 exige |
| Cabeçalhos de segurança (CSP, HSTS, etc.) | **Aproveitar** | atende a seção "Superfície HTTP" quase sem mudança |
| CORS com lista branca por env | **Aproveitar** | default vazio e seguro quando a variável falta |
| Hierarquia de exceções + handler global | **Aproveitar** | é o padrão que o CRM já exige |
| `lib/api.ts` (cliente HTTP) | **Aproveitar** | falta só o refresh no 401 |
| Rota protegida via `/me` no `beforeLoad` | **Adaptar** | padrão certo, roteador diferente |
| Argon2 + pepper | **Adaptar** | pepper é ganho real, mas invalida o hash já gerado |
| CSRF double-submit | **Adaptar** | necessário no FinUp por causa do `SameSite=None` |
| Interface `RateLimitService` | **Adaptar** | interface serve, implementação não |
| Rate limit e blacklist em memória | **Não trazer** | quebra com mais de uma instância |
| Estrutura de pacotes por camada técnica | **Não trazer** | quebra `ApplicationModules.verify()` |
| `InputSanitizer` | **Não trazer** | camada errada, e destrói conteúdo de cliente |
| `ddl-auto=create-drop`, ausência de Flyway | **Não trazer** | viola regra dura do projeto |
| JWT de 2h sem refresh | **Não trazer** | decisão já tomada: 15 min + refresh rotativo |
| Lombok | **Não trazer** | `record` do Java 25 cobre o caso dos DTOs |
| Ausência de testes | **Não trazer** | zero testes no FinUp; o CRM tem teste obrigatório |

---

## Aproveitar direto

### Componentes shadcn/ui — a maior economia

`frontend/src/components/ui/` tem 46 componentes prontos: `table`, `dialog`,
`select`, `command`, `sidebar`, `sheet`, `form`, `popover`, `tabs`,
`dropdown-menu`, `chart`, entre outros. O CRM tem **zero** componentes hoje.

Tailwind 4 nos dois projetos, então as classes transferem. E como são
construídos sobre Radix, vêm com navegação por teclado, gestão de foco e
semântica ARIA — exatamente o que `00-projeto.md` §7 exige em WCAG e que é
caro de fazer à mão.

Dois cuidados na hora de trazer:

- Os componentes usam variáveis CSS (`var(--bg-primary)` aparece no layout do
  FinUp). Remapeie para os tokens semânticos decididos em `03-decisoes.md`
  (`--surface-base`, `--surface-raised`, `--text-strong`, `--brand`) antes de
  usar, senão você importa a paleta do FinUp junto.
- Traga sob demanda, não a pasta inteira. Componente não usado é superfície
  de manutenção sem contrapartida.

### Cabeçalhos de segurança

`SecurityConfig.java` já configura CSP, HSTS com `includeSubDomains` e um ano
de `max-age`, `frameOptions.deny()`, `nosniff` e
`Referrer-Policy: strict-origin-when-cross-origin`. É praticamente a lista da
seção "Superfície HTTP" do `01-padroes-tecnicos.md`.

Duas mudanças obrigatórias no CRM:

- **CSP precisa liberar WebSocket.** O `connect-src 'self'` do FinUp bloqueia
  a conexão STOMP. Precisa virar `connect-src 'self' ws: wss:` — e em produção,
  só `wss:`.
- **Remova o `X-XSS-Protection`.** O header está depreciado e removido dos
  navegadores modernos; o valor recomendado hoje é `0`. Manter
  `ENABLED_MODE_BLOCK` não protege nada e, em navegadores antigos, o filtro
  chegou a introduzir vulnerabilidade própria.

### CORS

O `parseAllowedOrigins()` devolve lista vazia quando a variável não está
definida — ou seja, na ausência de configuração **nenhuma origem é aceita**,
em vez do `*` que costuma aparecer. É o comportamento certo e atende à regra
"nunca `*` com credenciais".

### Hierarquia de exceções

`ConflictException`, `InvalidArgumentException`, `ResourceNotFoundException`,
`UnauthorizedException` + `ErrorResponseDTO` + `GlobalExceptionHandler`. É
exatamente o padrão que o CRM pede: exceção de domínio nomeada, handler global
traduzindo para HTTP. Traga a forma; os nomes específicos do CRM serão outros
(`ContatoDuplicadoException`, `JanelaDeAtendimentoFechadaException`).

### `lib/api.ts`

Cliente HTTP enxuto: `credentials: "include"`, `Content-Type` só quando há
corpo, leitura do cookie CSRF para mutações, `ApiError` com status, tratamento
de 204 e de corpo não-JSON. Bom código.

Falta uma coisa para o CRM: **interceptar 401, chamar `/api/auth/refresh` uma
única vez e repetir o request**. Sem o "uma única vez", refresh expirado vira
laço infinito.

---

## Adaptar

### Argon2 + pepper — atenção, isto invalida o hash já gerado

O FinUp usa `Argon2PasswordEncoder(16, 32, 1, 65536, 3)` — 64 MiB e 3
iterações, bem acima do padrão do Spring — e envolve o encoder para
concatenar um **pepper** (`APP_PEPPER`) à senha antes do hash.

O pepper é ganho real: ele vive em variável de ambiente, não no banco. Quem
rouba só o dump do banco não consegue testar senhas offline, porque falta um
segredo que nunca esteve lá.

**Consequência direta:** o hash que está em `PROMPT-PROXIMA-SESSAO.md` foi
gerado **sem pepper**. Se o CRM adotar pepper, aquele hash não valida mais.
Os parâmetros (`m`, `t`, `p`) não são problema — o `Argon2PasswordEncoder` lê
os parâmetros da própria string codificada. O pepper é, porque muda a entrada.

Se adotar pepper, gere o hash assim, com o `PasswordEncoder` já configurado:

```
System.out.println(passwordEncoder.encode("12345"));
```

e substitua o valor na migration de seed.

### CSRF double-submit — entenda antes de copiar

O FinUp emite o cookie de autenticação com
`sameSite(cookieSecure ? "None" : "Lax")`. Ou seja: **em produção o cookie é
`SameSite=None`**, o ajuste mais permissivo que existe — enviado em toda
requisição cross-site. Foi necessário porque frontend e API ficam em origens
diferentes. E é justamente por isso que o CSRF double-submit precisou existir.

O CRM está numa posição melhor. `01-padroes-tecnicos.md` já prevê proxy
reverso na frente, o que permite servir frontend e API na **mesma origem** e
usar `SameSite=Strict`, como o padrão manda. Com `Strict`, o vetor de CSRF
praticamente desaparece.

Recomendação: mantenha o double-submit mesmo assim, como defesa em
profundidade — mas **não** copie o `SameSite=None`. Se você copiar o cookie do
FinUp sem olhar, troca uma decisão de segurança já tomada por uma pior, em
silêncio.

`SpaCsrfTokenRequestHandler` e `CsrfCookieFilter` transferem como estão.

### Rota protegida

O padrão do `_authenticated/route.tsx` está certo: antes de renderizar,
chama `/me`; se falhar, redireciona para o login. A verificação é do servidor,
não de um booleano no cliente.

O código não transfere — o FinUp usa TanStack Router com rotas por arquivo, o
CRM usa `react-router-dom` 7 com registro central em `app/routes.ts`. Refaça
com `loader` ou um componente de guarda. E lembre: isso é conveniência de UX.
A fronteira continua sendo o backend rejeitando.

### `RateLimitService`

A interface (`isBlocked`, `recordAttempt`, `resetAttempts`) é uma boa
abstração e transfere. A implementação, não — ver abaixo.

---

## Não trazer

### Rate limit e blacklist em memória

`InMemoryRateLimitService` e `InMemoryTokenBlacklistService` usam
`ConcurrentHashMap`. Dois problemas:

1. **Não funcionam com mais de uma instância.** Cinco tentativas por instância
   viram quinze com três instâncias atrás de um balanceador. O
   `01-padroes-tecnicos.md` já exige Redis para rate limit.
2. **Crescimento sem limite.** O mapa só descarta entrada expirada quando
   aquela chave específica é acessada de novo. Um atacante variando a chave
   (e-mail aleatório por tentativa) enche a memória sem nunca disparar a
   limpeza.

No CRM: Redis, com TTL nativo — que resolve os dois de uma vez.

### Estrutura de pacotes

O FinUp organiza por camada técnica: `business/`, `controller/`,
`infrastructure/`. O CRM organiza **por funcionalidade**, com `api/` e
`internal/` por módulo, e `ApplicationModules.verify()` quebrando o build se a
fronteira for violada.

Copiar a estrutura do FinUp reprova o teste de arquitetura no CI. Copie as
classes, nunca o desenho de pacotes.

### `InputSanitizer` — camada errada, e perigoso neste domínio

Ele remove tags HTML e caracteres "perigosos" das strings que chegam do
frontend. Isso é sanitização na **entrada**, e está errado por dois motivos.

O primeiro é geral: a defesa contra XSS é codificação **na saída**, dependente
do contexto onde o dado é renderizado. O React já escapa por padrão. Limpar na
entrada dá falsa confiança — o `cleanStrict()` remove qualquer caractere fora
de `[letras, números, espaço, .,-/()°ºª]`, o que descarta `@`, `#`, `+`, `&`,
`:` e aspas.

O segundo é específico e mais grave: **o CRM armazena conteúdo de mensagem de
cliente**. Um cliente que escreva "orçamento p/ 3 unidades — R$ 2.500 + frete?"
teria o `+` e o `?` removidos. Um contato "Müller & Söhne" viraria "Müller
Söhne". Um cliente colando um link teria o link destruído. Sanitizar a entrada
aqui não é defesa, é corrupção silenciosa do dado que o produto existe para
guardar.

No CRM: valide **formato** com Bean Validation, guarde o texto como veio,
escape na renderização.

### `ddl-auto=create-drop` e ausência de Flyway

`application-dev.properties` usa `create-drop`; não existe uma migration no
projeto. O CRM exige Flyway sempre e `validate` em todo perfil. Regra dura,
sem exceção.

### JWT de 2 horas sem refresh

Token de 2h com blacklist no logout é mais fraco que 15 min + refresh
rotativo, e ainda obriga a consultar a blacklist a cada requisição — o que
anula o motivo de o JWT ser stateless. A decisão do CRM já está tomada.

### Lombok

`@RequiredArgsConstructor` e afins. Com Java 25 e `record` para DTOs, o ganho
some, e é um processador de anotação a menos para quebrar em upgrade de JDK.

### Zero testes

O FinUp tem JaCoCo configurado e **nenhum teste**. O CRM tem cinco categorias
de teste obrigatórias, entre elas isolamento entre tenants — que é justamente
o que o FinUp não tem como testar, porque não tem multi-tenancy.

---

## Ausência estrutural: multi-tenancy

Vale dizer explicitamente porque muda o que dá para copiar. O FinUp escopa
dado **por usuário**, com `tenant_id` inexistente. O CRM precisa de
`tenant_id` desde o primeiro commit, RLS no Postgres e hierarquia
Franqueadora → Regiões → Unidades → Equipes → Usuários.

Isso significa que **nenhum service, repository ou entidade do FinUp
transfere**. `TransactionService`, `ClientService` e os repositórios assumem
um filtro que no CRM tem forma diferente e é reforçado pelo banco. O que
transfere é a camada de segurança e a de apresentação — não a de domínio.

---

## Achados de segurança no FinUp

Encontrados durante a análise. Não afetam o CRM, mas afetam o FinUp.

### `.env` versionado no git

O `.gitignore` lista `.env`, mas o arquivo **já estava rastreado** quando a
regra foi criada — e `.gitignore` não afeta arquivo já rastreado. Ele está no
histórico, nos commits `db89bec` e `827cbd0`.

O conteúdo é de severidade baixa: só `SUPABASE_URL`, `SUPABASE_PROJECT_ID` e
`SUPABASE_PUBLISHABLE_KEY`. A chave publishable é feita para ir ao navegador
(o próprio arquivo a expõe como `VITE_`), e a proteção real dela é a política
de RLS do Supabase, não o sigilo. `JWT_SECRET` e `APP_PEPPER` **não** estão
lá — esses vêm do ambiente. Ou seja: não há vazamento crítico.

O problema é o mecanismo, não o conteúdo de hoje:

```
git rm --cached .env
git commit -m "Remove .env do rastreamento"
```

Enquanto o arquivo estiver rastreado, qualquer segredo adicionado a ele será
commitado automaticamente, e o `.gitignore` vai continuar dando a impressão
de que está protegido. Se o projeto Supabase estiver abandonado, vale
desativá-lo.

Para o CRM, isso vira item de pipeline: **verificação de segredo vazado no
diff** já está na lista de CI do `01-padroes-tecnicos.md`. Vale ativar cedo.

### `X-Forwarded-For` pega o valor mais à esquerda

Em `AuthController.getClientIp()`, quando `trustForwardedHeaders` é `true`, o
código usa `xff.split(",")[0]`. Esse é o valor que o **cliente** controla:
basta mandar `X-Forwarded-For: 1.2.3.4` para trocar de identidade a cada
tentativa e furar o rate limit.

O default é `false`, então hoje está seguro. Mas no dia em que subir atrás de
proxy e alguém ligar a flag, o rate limit por IP deixa de existir. O correto é
contar a partir da direita, descartando um número conhecido de proxies
confiáveis.

### O que o FinUp acertou

Justiça: `UsuarioService.autenticar()` captura qualquer exceção e lança sempre
`UnauthorizedException("E-mail ou senha incorretos.")`. Mensagem idêntica para
usuário inexistente e senha errada — e o `DaoAuthenticationProvider` do Spring
ainda executa um hash contra valor dummy quando o usuário não existe, o que
equaliza o tempo de resposta. Enumeração de usuários fechada, pelos dois
vetores. É o comportamento que o CRM exige.

E o `DataInitializer` já é `@Profile({"dev","test"})` com senha de teste — o
mesmo padrão adotado para o seed do CRM.

---

## Resumo do impacto

O que muda no plano da próxima sessão:

- **Fase 1** fica mais curta: cabeçalhos, CORS, CSRF, hierarquia de exceções e
  `api.ts` vêm prontos ou quase. O que continua do zero é multi-tenancy, RLS,
  refresh token rotativo e rate limit em Redis.
- **Fase 1.5** ganha os componentes de UI de graça, o que antecipa a tela de
  login real e o layout do inbox.
- **Nada** do domínio do FinUp transfere.
- Se adotar pepper, **regere o hash do seed** antes de escrever a migration.
