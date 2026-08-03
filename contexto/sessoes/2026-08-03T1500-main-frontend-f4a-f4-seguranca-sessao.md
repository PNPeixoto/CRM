# Sessão 2026-08-03 — F4A e F4, segurança do navegador e sessão

- Branch: `main`
- Commit base: `c27aeb9`
- Ambiente: Windows 11, Node 24.18.0, npm 11.16.0, Chrome via painel de preview
- Responsável: PNPeixoto, com Claude Code

## F4A — Segurança do navegador e supply chain

### O que já estava certo

A varredura não encontrou `eval`, `new Function`, `innerHTML`,
`dangerouslySetInnerHTML`, `document.write`, `target="_blank"` nem esquema de
URL executável. O access token já vivia só em memória, e o único uso de
`localStorage` é a preferência de menu, com a chave derivada por hash.

Isso mudou o trabalho: em vez de corrigir, o F4A **fixou o estado atual contra
regressão**. Cada um desses itens é fácil de reintroduzir sem má intenção — um
`dangerouslySetInnerHTML` para renderizar negrito, um token guardado "só para
não perder a sessão no F5". Nenhum aparece em revisão como problema de
segurança; aparece como conveniência.

### O que foi acrescentado

- **CSP no documento**, em `index.html`. O build do Vite não emite script
  inline nem `eval` — verificado no `dist` — então `script-src 'self'` é
  aplicável sem `unsafe-inline`.
- **Teste de contrato** `browser-security.contract.test.ts`, nove verificações
  sobre o texto-fonte. Grosseiro de propósito: um analisador de AST seria mais
  preciso e mais fácil de contornar sem perceber.
- **Cadeia de build fixada**: `engines`, `packageManager`, `.nvmrc`, e
  `build.sourcemap: false` explícito. O padrão do Vite já é `false`, mas padrão
  muda de versão sem ninguém notar.
- **`frontend/SEGURANCA-NAVEGADOR.md`**, separando o que a aplicação garante do
  que depende de quem serve os estáticos.

### Trusted Types — avaliado, não imposto

Não foi habilitado, e o motivo está registrado: não há sumidouro de DOM
perigoso para bloquear, e `<meta>` não suporta `Report-Only`, então impor sem
período de observação arriscaria indisponibilidade em produção por um caminho
que ninguém mediu. O caminho de promoção está documentado.

### Correção de uma afirmação minha

Escrevi no documento que nenhuma dependência declarava script de instalação.
Ao verificar, encontrei **uma**: `fsevents`, binding nativo do macOS,
dependência opcional do watcher do Vite. Corrigido no documento, com o motivo
concreto de ser inerte aqui — opcional, restrito a um sistema fora do caminho
de build, e instalação por `npm ci` a partir do lockfile.

## F4 — Sessão e refresh

### Achado corrigido: redirecionamento aberto

O destino de retorno pós-login vinha de `location.state` **sem validação**. O
estado do histórico é gravável por qualquer código da página, e `//host.externo`
é protocolo-relativo: o navegador o resolve como host externo. O
redirecionamento partiria de uma tela de login legítima, que é exatamente o que
empresta credibilidade a um phishing.

`destinoInternoSeguro` valida por allowlist de forma — barra única inicial, sem
caractere de controle, confirmação de origem depois da normalização do parser,
e recusa de `/login` para não criar laço. Lista negra erraria a cada codificação
nova inventada.

Registrado como `SEC-014` no backlog de segurança.

### O que já existia e foi provado

O refresh já era *single-flight*, e agora existe teste que o demonstra: dez
requisições expiradas concorrentes produzem **um** refresh. O valor disso não é
desempenho — sem single-flight, dez renovações paralelas girariam a família de
refresh tokens e a **detecção de reuso derrubaria a sessão do usuário
legítimo**. É uma falha que só aparece sob concorrência, ou seja, em produção.

### O que foi acrescentado

Sessão entre abas por `BroadcastChannel`. A carga é apenas `{tipo: 'saiu'}` —
o canal é legível por qualquer script da mesma origem, então um token publicado
ali estaria disponível a um XSS em qualquer aba aberta, inclusive as esquecidas
de ontem. O receptor valida a forma da mensagem em vez de confiar.

O canal nasce e morre dentro do efeito: criá-lo no corpo do componente
sobreviveria à limpeza do StrictMode, e a remontagem herdaria um canal fechado.

## Evidências

| verificação | resultado |
|---|---|
| suíte frontend | 86 testes em 18 arquivos, todos verdes (antes: 56 em 14) |
| lint | 0 erros, 3 avisos conhecidos de Fast Refresh |
| build de produção | sem script inline, sem source map, CSP presente no `dist` |
| CSP no navegador | violação `script-src-elem` com `disposition: enforce` ao injetar script inline; `img-src` ao carregar recurso externo |
| armazenamento no navegador | `localStorage` e `sessionStorage` vazios; único cookie legível é `XSRF-TOKEN`, que não é credencial |
| guarda de rota | `/contatos` sem sessão redireciona a `/login` guardando o destino |

Os controles novos foram exercitados no caminho negativo: injetei
`dangerouslySetInnerHTML` e escrita de token em `localStorage` num arquivo de
produção, e o teste de contrato reprovou os dois. Arquivo restaurado em
seguida.

## Nota sobre a medição da CSP

Uma primeira tentativa mediu `eval` como não bloqueado. Era artefato do método:
`javascript_tool` executa em contexto de devtools, isento da CSP da página. A
medição correta injeta o código **no contexto da página**, e ali o script nem
chega a executar — o navegador reporta a violação antes. Registro isto porque
a conclusão errada seria "a CSP não funciona".

## Veredito do Gate B

**Fechado nos dois lados.** Backend aprovado no Prompt 07; frontend aprovado
aqui.

## Limite que permanece

`frame-ancestors` é **ignorada** pelo navegador quando vem em `<meta>`. Ela
precisa vir como cabeçalho do servidor de estáticos, que ainda não existe no
repositório. Enquanto isso, a proteção contra clickjacking do documento da SPA
depende de configuração externa não versionada. Está no documento de segurança
do navegador como exigência explícita, não como detalhe.
