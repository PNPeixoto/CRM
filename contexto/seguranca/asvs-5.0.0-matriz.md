# Matriz ASVS 5.0.0 — nível 2

Fonte, versão e data em [`README.md`](README.md). Alvo: nível 2; nível 3
marcado explicitamente onde aplicado.

**Como ler a coluna evidência:** `execução` significa que um comando foi rodado
e o resultado observado nesta execução ou registrado em sessão anterior;
`inspeção` significa que a afirmação vem da leitura do código e não de
comportamento observado. A diferença importa: inspeção não pega
comportamento em runtime que contradiz o que o código parece dizer.

Baseline: commit `a603534`, suíte backend com 112 testes verdes, frontend com
56.

---

## V1 — Encoding and Sanitization

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Injeção SQL | sim | JPQL com parâmetros nomeados; nenhuma concatenação de entrada em consulta. `ContactRepository.buscar` recebe o termo como parâmetro, inclusive no `LIKE` | `IsolamentoEntreTenantsTest`, `ReferenciasMultiTenantTest` | execução |
| `ORDER BY` dinâmico | **não** | Nenhum endpoint aceita campo de ordenação do usuário; a ordenação é fixa em cada consulta | — | inspeção |
| Injeção de comando de SO | **não** | A aplicação não invoca processo externo | — | inspeção |
| XSS refletido/armazenado | sim | React escapa por padrão; nenhum `dangerouslySetInnerHTML` no código; CSP sem `unsafe-eval` e sem `unsafe-inline` em `script-src` | cobertura indireta pelos testes de componente | inspeção |
| Deserialização insegura | sim | Jackson com `fail-on-unknown-properties: true`; DTOs são records de campos explícitos; sem tipagem polimórfica habilitada | `GlobalExceptionHandlerTest` | execução |

## V2 — Validation and Business Logic

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Validação de entrada | sim | Jakarta Validation nos DTOs (`@NotBlank`, `@Size`, `@Email`); teto de página de 100 em `ContactController` | `ModeloOrganizacionalTest` | execução |
| Invariante de domínio | sim | Dinheiro em centavos (`BIGINT`), nunca ponto flutuante; transição de etapa passa por `moverPara` | `FunilPorSegmentoTest`, `SegmentPresetCatalogTest` | execução |
| Mass assignment | sim | DTO por caso de uso; entidade nunca é corpo de requisição; campo desconhecido — inclusive `tenantId` — é **rejeitado** e não ignorado | `ReferenciasMultiTenantTest` | execução |
| Limite de taxa em lógica de negócio | **parcial** | Existe em login (por tenant, login e origem). **Ausente** nos demais endpoints — ver `SEC-003` | `AutenticacaoSeguraTest` | execução |

## V3 — Web Frontend Security

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| CSP | sim | `default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'`, `form-action 'self'`; `connect-src` lista `wss:` explicitamente porque `'self'` não cobre o esquema WebSocket | — | inspeção |
| `style-src 'unsafe-inline'` | sim | Aceito conscientemente — ver `SEC-007` | — | inspeção |
| CSRF | sim | Double-submit com `CookieCsrfTokenRepository`; `SpaCsrfTokenRequestHandler`; isento apenas onde não há sessão a proteger (login, refresh, MFA, reset, webhook, handshake WS) | `AutenticacaoSeguraTest.cookieCsrfAndRevokeAll` | execução |
| Clickjacking | sim | `frame-options: DENY` mais `frame-ancestors 'none'` | — | inspeção |
| Armazenamento de token no cliente | sim | Access token **em memória**, nunca em `localStorage`; refresh em cookie `HttpOnly` | testes do cliente HTTP no frontend | execução |
| `X-XSS-Protection` | sim | Desabilitado deliberadamente: depreciado, ignorado por navegador moderno e origem de vulnerabilidade própria nos antigos | — | inspeção |

## V4 — API and Web Service

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Contrato explícito | sim | OpenAPI 3.1 determinístico, snapshot versionado; alteração de contrato reprova o build | `OpenApiContractTest` | execução |
| CORS | sim | Lista branca por variável; **vazio = nenhuma origem**, falha fechada | — | inspeção |
| Erro padronizado | sim | RFC 9457 com `correlationId`; detalhe interno não é apresentado | `GlobalExceptionHandlerTest` | execução |
| Método HTTP e verbo seguro | sim | Leitura em `GET`, mutação em `POST`/`PUT`/`DELETE`; `@Transactional(readOnly = true)` nas leituras | — | inspeção |
| Exposição do contrato | **achado** | `/v3/api-docs/**` é público em todos os profiles — ver `SEC-001` | — | inspeção |

## V5 — File Handling

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Upload, tipo, tamanho, varredura, armazenamento | **não — superfície inexistente** | Nenhum endpoint recebe ou serve arquivo. Mídia de canal ainda não foi construída | — | inspeção |

O capítulo inteiro fica **em aberto por ausência de superfície**. Quando mídia
entrar (Prompt 13), V5 precisa ser reavaliado por completo: tipo declarado
versus conteúdo real, tamanho, nome, caminho de armazenamento fora da raiz web
e varredura. Registrado como `SEC-010`.

## V6 — Authentication

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Armazenamento de senha | sim | Argon2id com pepper externo; parâmetros medidos (média 103 ms local) | `PasswordSecurityTest`, `Argon2BenchmarkTest` | execução |
| Resposta uniforme | sim | Login não distingue empresa inexistente, usuário inexistente e senha errada | `LoginRespostaUniformeTest` | execução |
| Bloqueio progressivo | sim | Por tenant, login e origem, com limite global de origem que **não** trava a vítima globalmente; chave normalizada para não ser contornável por caixa | `AutenticacaoSeguraTest.rateLimitDoesNotGloballyLockVictim` | execução |
| MFA | sim **(nível 3)** | TOTP obrigatório para OWNER/ADMIN/SUPERADMIN; segredo AES-256-GCM; replay bloqueado; dez códigos de recuperação de uso único por hash | `AutenticacaoSeguraTest` | execução |
| Recuperação de senha | sim | Resposta uniforme (202 sempre); token de 256 bits, 15 min, armazenado por hash; uso único; revoga todas as sessões | `AutenticacaoSeguraTest.passwordResetIsSingleUseAndRevokesSessions` | execução |
| Resistência a phishing | **não atendido** | TOTP não é resistente a phishing por natureza. Passkeys/WebAuthn é a evolução — risco aceito e declarado | — | inspeção |

## V7 — Session Management

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Sessão sem estado no servidor | sim | `SessionCreationPolicy.STATELESS`; nenhuma `HttpSession` | — | inspeção |
| Validade do access token | sim | 15 minutos | `RefreshTokenRotacaoTest` | execução |
| Rotação de refresh e detecção de reuso | sim **(nível 3)** | Rotação a cada uso; reuso revoga a **família** inteira; hash SHA-256, nunca o valor | `RefreshTokenRotacaoTest` | execução |
| Inatividade e limite absoluto | sim | 1 hora de inatividade, 24 horas absolutas | `AutenticacaoSeguraTest.refreshHasInactivityAndAbsoluteLimits` | execução |
| Encerramento | sim | Logout limpa o cookie; `revoke-all` encerra todos os dispositivos | `AutenticacaoSeguraTest.cookieCsrfAndRevokeAll` | execução |
| Revogação imediata | **parcial** | Revogar refresh não invalida access token já emitido; janela máxima de 15 min. Mitigado em autorização: perda de membership nega na requisição seguinte | `AutorizacaoPorAlcanceTest.semMembershipVigenteNadaEhAutorizado` | execução |

## V8 — Authorization

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Decisão central por ação | sim | `Autorizacao.exigir`; catálogo tipado `Permissao`; nenhum controller reimplementa a regra | `AutorizacaoPorAlcanceTest` | execução |
| Decisão por registro (anti-IDOR) | sim **(nível 3)** | `exigirSobreRegistro`; recorte de alcance próprio **dentro da consulta**; atualização verifica antes e depois de aplicar | `AutorizacaoPorAlcanceTest` (leitura, escrita e exclusão por id alheio) | execução |
| Isolamento entre tenants | sim **(nível 3)** | RLS `ENABLE + FORCE`; runtime sem `SUPERUSER` e sem `BYPASSRLS`; tenant vem de credencial, nunca de corpo, query ou header | `BancoSegurancaTest`, `IsolamentoEntreTenantsTest` | execução |
| Recurso coletivo | sim | Canal, caixa de entrada, relatório e onboarding exigem alcance `TENANT`; `OWN` nunca é promovido a acesso coletivo | `AutorizacaoPorAlcanceTest` | execução |
| Revalidação em canal assíncrono | **parcial** | WebSocket revalida permissão e destino a cada SUBSCRIBE. Job e exportação **não existem** como superfície | `EscutaDeTopicoTest` | execução |
| Escopo por unidade | **não implementado, falha fechada** | Nenhuma tabela de domínio declara unidade; `UNIT` é negado em vez de promovido — ADR-0008 | `AutorizacaoPorAlcanceTest.escopoDeUnidadeNaoDecideSobreRegistroDeDominio` | execução |
| Menu versus permissão | sim | `GET /api/organizacao/permissoes` informa a interface; a decisão continua no backend em cada endpoint | — | inspeção |

## V9 — Self-contained Tokens

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Assinatura verificada | sim | `NimbusJwtDecoder` com HS256; algoritmo fixado, sem negociação pelo token | `AutenticacaoSeguraTest` | execução |
| Claims mínimos | sim | `sub`, `tid`, `login`; sem dado pessoal além do login | — | inspeção |
| Validade curta | sim | 15 minutos | `RefreshTokenRotacaoTest` | execução |
| Chave simétrica | **achado** | HS256 usa a mesma chave para assinar e verificar — ver `SEC-002` | — | inspeção |

## V10 — OAuth and OIDC

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Fluxos OAuth/OIDC | **não — superfície inexistente** | O CRM não é cliente nem provedor OAuth/OIDC; autenticação é local | — | inspeção |

SSO corporativo dispara revisão por novo ADR, conforme ADR-0006.

## V11 — Cryptography

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Cifra em repouso | sim **(nível 3)** | AES-256-GCM em credencial de canal e em segredo TOTP | `BancoSegurancaTest` | execução |
| Separação de chaves | sim | Quatro segredos distintos: pepper, assinatura JWT, chave de canal e chave de MFA. Reaproveitar faria um vazamento comprometer senha, sessão, integração e MFA de uma vez | — | inspeção |
| Aleatoriedade | sim | `SecureRandom` para token de refresh e de recuperação | `RefreshTokenRotacaoTest` | execução |
| Segredo sem valor padrão | sim | `application.yml` declara `${APP_PEPPER}` e demais **sem default**: a aplicação não sobe sem eles | — | inspeção |
| Rotação de chave | **parcial** | Chave de canal e de MFA são rotacionáveis por reencriptação. O **pepper não é** — ver `SEC-008` | — | inspeção |

## V12 — Secure Communication

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| HSTS | sim | `max-age` 31.536.000 com `includeSubDomains` | — | inspeção |
| Cookie seguro | sim | `cookie-secure: true` por padrão; `false` apenas no profile de desenvolvimento | `AutenticacaoSeguraTest` | execução |
| WebSocket sobre TLS | sim | CSP aceita apenas `wss:` fora de desenvolvimento | — | inspeção |
| Terminação TLS | **fora da aplicação** | O contêiner serve HTTP; TLS termina no proxy. `forward-headers-strategy` é `none` por padrão e `native` com allowlist de proxy em produção — sem a allowlist a aplicação não sobe | — | inspeção |

## V13 — Configuration

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Superfície administrativa | sim | Actuator expõe apenas `health`; `show-details: never` | — | inspeção |
| Schema imutável | sim | `ddl-auto: validate`, nunca `update`; Flyway com `clean-disabled: true` em produção e `out-of-order: false` | `MigracaoDeAtualizacaoTest` | execução |
| Separação de privilégio no banco | sim **(nível 3)** | `crm_migrator` só durante migration; a aplicação conecta sempre pelo papel restrito | `BancoSegurancaTest` | execução |
| Contêiner endurecido | sim | Não-root (UID 10001), `read_only`, `cap_drop: ALL`, `no-new-privileges`, tmpfs limitada | — | inspeção |
| Segredo em imagem | sim | Nenhum; varredura do repositório sem vazamento | gitleaks sobre 25 commits | execução |
| Deriva entre ambiente e código | **achado** | Nada compara schema esperado com aplicado no boot — ver `SEC-006` | — | execução (ENV-001 aconteceu de fato) |

## V14 — Data Protection

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Isolamento de dado por cliente | sim **(nível 3)** | RLS forçado mais integridade composta entre tenants; segundo tenant povoado nos testes para que vazamento seja visível | `IsolamentoEntreTenantsTest`, `ReferenciasMultiTenantTest` | execução |
| Credencial de integração | sim | Resposta write-only: a API nunca devolve o segredo do canal | `BancoSegurancaTest` | execução |
| Dado pessoal em log | sim | Log registra identificador, nunca conteúdo de mensagem, senha ou token — 17 arquivos com log auditados | inspeção dos 17 pontos de log | inspeção |
| Retenção e expurgo | **não — pertence ao Prompt 18** | Sem política de retenção implementada | — | inspeção |

## V15 — Secure Coding and Architecture

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Fronteira de módulo | sim | Spring Modulith; `api` exposta por `@NamedInterface`, `internal` fechada; nenhum módulo injeta repositório de outro | `FronteiraDeModulosTest` | execução |
| Ausência de `null` em API pública | sim | `Optional`, coleção vazia ou exceção de domínio nomeada | — | inspeção |
| Função de banco com privilégio | sim **(nível 3)** | Cinco funções `SECURITY DEFINER` com `search_path` fixo | `BancoSegurancaTest` | execução |
| Concorrência | sim | `FOR UPDATE SKIP LOCKED` na fila de saída e na entrada de webhook, com backoff exponencial e teto de tentativas | `FilaDeSaidaTest`, `IngestaoTransacionalTest` | execução |

## V16 — Security Logging and Error Handling

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Erro sem detalhe interno | sim | RFC 9457; exceção de domínio nomeada; stack trace não vai para a resposta | `GlobalExceptionHandlerTest` | execução |
| Correlação | sim | `correlationId` em toda resposta de erro e no log correspondente | `GlobalExceptionHandlerTest` | execução |
| Log de negação | sim | Registra permissão e usuário e **não** o id do recurso alvo, para o log não virar inventário do que existe | inspeção de `AutorizacaoService.negar` | inspeção |
| Log de evento de segurança | **parcial** | Reuso de refresh token, negação de autorização e SUBSCRIBE recusado são registrados. Não há trilha consultável | — | inspeção |
| Trilha de auditoria | **não — ausente** | O módulo `audit` não existe. É `AUDIT-001`, P0 do produto, e bloqueia o Gate E | — | execução |
| Log com objeto de exceção | **achado menor** | Alguns pontos registram a exceção do provedor, que pode carregar corpo de resposta — ver `SEC-009` | — | inspeção |

---

## Resumo

| Situação | Capítulos |
|---|---|
| Atendido no nível 2 ou acima | V1, V2 (com ressalva de taxa), V3, V4 (com `SEC-001`), V6, V7, V8, V11, V12, V13, V14, V15 |
| Não aplicável por ausência de superfície | V5, V10 |
| Atendido parcialmente | V9 (`SEC-002`), V16 (auditoria ausente) |

Nível 3 aplicado deliberadamente em: isolamento entre tenants, decisão por
registro, MFA, rotação de refresh com detecção de reuso, cifra em repouso,
separação de privilégio no banco e função com `SECURITY DEFINER`. O critério
foi o mesmo em todos: falha atinge todos os clientes ao mesmo tempo, ou destrói
a capacidade de saber que foi atingida.
