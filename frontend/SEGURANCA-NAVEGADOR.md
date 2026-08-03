# Segurança do navegador

Produto do Prompt frontend F4A. Descreve o que a aplicação garante sozinha e o
que **depende de quem serve os arquivos estáticos** — a distinção importa,
porque metade destes controles não é código do frontend.

## Content Security Policy

A política está em `index.html` como `<meta http-equiv>`. Isso é defesa em
profundidade e não substitui o cabeçalho.

```
default-src 'self';
script-src 'self';
style-src 'self' 'unsafe-inline';
img-src 'self' data: blob:;
font-src 'self' data:;
connect-src 'self' wss:;
object-src 'none';
base-uri 'self';
form-action 'self'
```

**O servidor de estáticos precisa enviar a mesma política como cabeçalho, mais
`frame-ancestors 'none'`.** O navegador **ignora** `frame-ancestors` quando ela
vem em `<meta>`; sem o cabeçalho, a aplicação continua embutível em iframe de
terceiro e o clickjacking segue possível mesmo com a meta presente.

Também precisam vir do servidor de estáticos, porque não existem em meta:

| Cabeçalho | Valor | Motivo |
|---|---|---|
| `Content-Security-Policy` | igual à meta, mais `frame-ancestors 'none'` | a meta não cobre `frame-ancestors` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | o backend já envia nas respostas de API; o host dos estáticos precisa enviar também |
| `X-Content-Type-Options` | `nosniff` | impede que um `.js` servido com tipo errado seja reinterpretado |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | evita vazar caminho autenticado para terceiro |

### Por que `script-src` não aceita inline

O build do Vite não emite script inline nem `eval`: o `dist/index.html` traz um
único `<script type="module" src>` externo. Verificado nesta execução. É o que
torna `script-src 'self'` aplicável sem `unsafe-inline` — e é onde execução de
código de fato acontece.

`style-src` aceita inline porque React e Tailwind injetam estilo em atributo.
Aceito conscientemente; consta como `SEC-007` no backlog de segurança.

### Trusted Types — avaliado, não imposto

A diretiva `require-trusted-types-for 'script'` **não foi habilitada**. O
motivo é o que o próprio prompt pede: só tornar obrigatório quando a
compatibilidade estiver comprovada.

O que foi verificado: a aplicação não tem nenhum sumidouro de DOM perigoso —
sem `innerHTML`, sem `dangerouslySetInnerHTML`, sem `eval`, sem `new Function`.
Ou seja, hoje Trusted Types não teria o que bloquear, e o ganho seria contra
regressão futura, não contra risco presente.

O que impede habilitar agora: `<meta>` não suporta `Content-Security-Policy-
Report-Only`, então não há como observar violação antes de impor. Habilitar
direto no cabeçalho, sem período de observação, arrisca quebrar a aplicação em
produção por um caminho que ninguém mediu.

**Caminho:** quando o servidor de estáticos estiver definido, publicar
`Content-Security-Policy-Report-Only: require-trusted-types-for 'script'`,
observar, e só então promover. Enquanto isso, o teste de contrato em
`src/browser-security.contract.test.ts` cobre a mesma regressão — de forma mais
grosseira, porém sem risco de indisponibilidade.

## Armazenamento do navegador

**Nada sensível é persistido.** A política, verificada por teste:

| Local | Conteúdo permitido |
|---|---|
| Memória | access token — e somente aqui |
| Cookie `HttpOnly` | refresh token, inacessível ao JavaScript |
| `localStorage` | apenas preferência de interface, com a chave derivada por hash do id do usuário |
| `sessionStorage`, IndexedDB, URL, histórico | nada |

O access token nunca é persistido: ao recarregar, a sessão é reconstruída pelo
endpoint de refresh. É o que faz um XSS que rode uma vez não render credencial
duradoura.

A chave de preferência de menu é `crm-pnp:ui:menu-recolhido:v1:<hash>`. O hash
existe para não expor o identificador do usuário a quem inspecione o
armazenamento; autorização nunca depende dele.

## CSRF

A proteção **não** depende só de `SameSite`.

- O cookie de refresh é `HttpOnly`, `Secure`, `SameSite=Strict` e com `Path`
  restrito.
- Toda escrita envia `X-XSRF-TOKEN` lido do cookie `XSRF-TOKEN`, no padrão
  double-submit. O backend valida.
- O token de CSRF **não é credencial**: ele só prova que a requisição foi
  montada por código da própria origem, e por isso o cookie que o carrega é
  legível por JavaScript de propósito.
- CORS usa allowlist explícita; ausência de configuração significa **nenhuma**
  origem, não todas.

`SameSite=Strict` sozinho seria insuficiente porque não cobre navegador antigo,
nem sub-domínio comprometido da mesma origem-site.

## Cadeia de build

| Item | Valor | Onde |
|---|---|---|
| Node | `>=24.0.0 <25` | `engines` do `package.json`, `.nvmrc` |
| npm | `11.16.0` | `packageManager` do `package.json` |
| Lockfile | `lockfileVersion: 3`, versionado | `package-lock.json` |
| Instalação em CI | `npm ci` — reprodutível, falha se o lockfile divergir | `.github/workflows/ci.yml` |
| Source maps | **não emitidos** em produção | `build.sourcemap: false`, explícito |
| Varredura de dependência | bloqueia alto e crítico sem exceção nomeada | job `seguranca` |
| Varredura de segredo | gitleaks sobre o histórico completo | job `seguranca` |

A política de severidade e exceção está em
`.github/security/excecoes-de-dependencia.json`: cada exceção exige
responsável, prazo e fundamento, e o CI **reprova quando o prazo vence**.

### Scripts de instalação

Dos 249 pacotes do lockfile, **um** declara script de instalação: `fsevents`,
binding nativo de sistema de arquivos do macOS, dependência opcional do
watcher do Vite. Ele não é instalado no CI (Linux) nem nesta máquina
(Windows), porque o próprio pacote é restrito por sistema operacional.

Não é aceitável por ser conhecido, e sim por três motivos verificáveis: é
opcional, é restrito a um sistema que não está no caminho de build, e a
instalação em CI usa `npm ci` a partir do lockfile — não há resolução dinâmica
que possa trazer outro pacote no lugar dele.

Se algum dia o build passar a rodar em macOS, este item deixa de ser inerte e
precisa ser reavaliado.

## O que este documento não garante

- Não houve teste de intrusão nem varredura dinâmica.
- A verificação da CSP **no ambiente implantado** depende do servidor de
  estáticos, que ainda não existe no repositório. Enquanto ele não existir, a
  meta é o que há, e ela não cobre `frame-ancestors`.
- Acessibilidade e WCAG pertencem ao F10.
