# Matriz ASVS 5.0.0 — nível 2

Fonte, versão e data em [`README.md`](README.md). Alvo: nível 2; nível 3
marcado explicitamente onde aplicado.

**Como ler a coluna evidência:** `execução` significa que um comando foi rodado
e o resultado observado nesta execução ou registrado em sessão anterior;
`inspeção` significa que a afirmação vem da leitura do código e não de
comportamento observado. A diferença importa: inspeção não pega
comportamento em runtime que contradiz o que o código parece dizer.

Baseline atualizado no Prompt 16: suíte backend com 187 testes verdes e
frontend com 128.

---

## V1 — Encoding and Sanitization

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Injeção SQL | sim | JPQL com parâmetros nomeados; nenhuma concatenação de entrada em consulta. `ContactRepository.buscar` recebe o termo como parâmetro, inclusive no `LIKE` | `IsolamentoEntreTenantsTest`, `ReferenciasMultiTenantTest` | execução |
| `ORDER BY` dinâmico | sim | Contatos aceita somente `ordenarPor=nome`; qualquer outro campo é rejeitado antes da consulta | `ContratosTransversaisTest` | execução |
| Injeção de comando de SO | **não** | A aplicação não invoca processo externo | — | inspeção |
| XSS refletido/armazenado | sim | React escapa por padrão; nenhum `dangerouslySetInnerHTML` no código; CSP sem `unsafe-eval` e sem `unsafe-inline` em `script-src` | cobertura indireta pelos testes de componente | inspeção |
| Deserialização insegura | sim | Jackson com `fail-on-unknown-properties: true`; DTOs são records de campos explícitos; sem tipagem polimórfica habilitada | `GlobalExceptionHandlerTest` | execução |

## V2 — Validation and Business Logic

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Validação de entrada | sim | Jakarta Validation nos DTOs; página 0–100 rejeita em vez de truncar; filtro tem 100 caracteres; corpo global tem 1 MiB | `ModeloOrganizacionalTest`, `ContratosTransversaisTest`, `HttpProtectionFilterTest` | execução |
| Invariante de domínio | sim | Dinheiro em centavos (`BIGINT`), nunca ponto flutuante; transição de etapa passa por `moverPara` | `FunilPorSegmentoTest`, `SegmentPresetCatalogTest` | execução |
| Mass assignment | sim | DTO por caso de uso; entidade nunca é corpo; campo desconhecido é rejeitado. A propriedade efetiva também é conferida no profile de produção | `ReferenciasMultiTenantTest`, `ContratosTransversaisTest`, `ProductionConfigurationTest` | execução |
| Limite de taxa em lógica de negócio | sim nas portas públicas atuais | Login mantém bloqueio por tenant/login/origem; autenticação pública e webhook têm ainda janela por origem | `AutenticacaoSeguraTest`, `HttpProtectionFilterTest` | execução |

## V3 — Web Frontend Security

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| CSP nas respostas de API | sim | `default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`, `base-uri 'self'`, `form-action 'self'`; `connect-src` lista `wss:` explicitamente porque `'self'` não cobre o esquema WebSocket | — | inspeção |
| CSP no documento da SPA | sim | `<meta http-equiv>` em `index.html`, mais exigência documentada de cabeçalho no servidor de estáticos — `frame-ancestors` é ignorada em meta | `browser-security.contract.test.ts` | **execução, no navegador**: violação `script-src-elem` com `disposition: enforce` ao injetar script inline, e `img-src` ao carregar recurso externo |
| `style-src 'unsafe-inline'` | sim | Aceito conscientemente — ver `SEC-007`. O F4A confirmou que a folga não alcança `script-src` | `browser-security.contract.test.ts` | execução |
| Ausência de execução genérica | sim | Sem `eval`, `new Function`, `innerHTML`, `dangerouslySetInnerHTML` ou `document.write` em nenhum arquivo de produção | `browser-security.contract.test.ts` | execução |
| Trusted Types | **avaliado, não imposto** | Não há sumidouro de DOM perigoso para bloquear; `<meta>` não suporta `Report-Only`, então impor sem observar arriscaria indisponibilidade. Caminho documentado | — | inspeção |
| CSRF | sim | Double-submit com `CookieCsrfTokenRepository`; `SpaCsrfTokenRequestHandler`; isento apenas onde não há sessão a proteger (login, refresh, MFA, reset, webhook, handshake WS) | `AutenticacaoSeguraTest.cookieCsrfAndRevokeAll` | execução |
| Clickjacking | sim | `frame-options: DENY` mais `frame-ancestors 'none'` | — | inspeção |
| Armazenamento de token no cliente | sim | Access token **em memória**, nunca persistido; refresh em cookie `HttpOnly`. `localStorage` guarda só preferência de menu, com a chave derivada por hash | `AuthContext.test.tsx`, `browser-security.contract.test.ts` | execução, mais conferência no navegador: `localStorage` e `sessionStorage` vazios, único cookie legível é o de CSRF |
| Redirecionamento aberto | sim | `destinoInternoSeguro` valida o retorno pós-login por allowlist de forma, com confirmação de origem depois da normalização do parser | `destinoSeguro.test.ts`, 12 casos | execução |
| `X-XSS-Protection` | sim | Desabilitado deliberadamente: depreciado, ignorado por navegador moderno e origem de vulnerabilidade própria nos antigos | — | inspeção |

## V4 — API and Web Service

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Contrato explícito | sim | OpenAPI 3.1 determinístico, snapshot versionado; alteração de contrato reprova o build | `OpenApiContractTest` | execução |
| CORS | sim | Lista branca por variável; **vazio = nenhuma origem**, falha fechada | — | inspeção |
| Erro padronizado | sim | RFC 9457 com `correlationId`; filtro atribui a correlação antes da autenticação e a inclui também no sucesso | `GlobalExceptionHandlerTest`, `HttpProtectionFilterTest` | execução |
| Método HTTP e verbo seguro | sim | Leitura em `GET`, mutação em `POST`/`PUT`/`DELETE`; `@Transactional(readOnly = true)` nas leituras | — | inspeção |
| Exposição do contrato | sim | Snapshot fica no build; API docs e Swagger UI estão desabilitados no profile de produção | `ProductionConfigurationTest` | execução |
| Idempotência de escrita repetível | sim | Envio de mensagem aceita chave opaca persistida; replay devolve o mesmo recurso e conteúdo divergente retorna conflito | `ContratosTransversaisTest` | execução |
| Histórico extenso | sim | Mensagens usam keyset `(createdAt,id)`, lote máximo de 100 e preservam ordem cronológica do contrato | `ContratosTransversaisTest` | execução |
| Requisição HTTP de saída | sim | Conectores aprovados; HTTPS, anti-SSRF A/AAAA, DNS pinning, redirects/cookies/retry automático desativados, timeout e teto de resposta | `PoliticaAntiSsrfTest`, `ClienteHttpSeguroTest` | execução |

## V5 — File Handling

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Upload, tipo, tamanho, varredura, armazenamento | parcial | Mídia de canal fica em quarentena privada, limitada a 20 MiB e validada por magic bytes; promoção depende de scanner externo | `QuarentenaDeMidiaTest`, `AssinaturaDeMidiaTest` | execução |

O scanner antimalware permanece uma fronteira externa. Sem promoção explícita,
o arquivo continua indisponível; este é o risco residual de V5.

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
| Encerramento | sim | Logout limpa o cookie; `revoke-all` encerra todos os dispositivos. No cliente, sair anuncia às demais abas por `BroadcastChannel` — apenas o verbo `{tipo:'saiu'}`, nunca token nem dado pessoal | `AutenticacaoSeguraTest.cookieCsrfAndRevokeAll`, `AuthContext.test.tsx`, `sessao.test.ts` | execução |
| Renovação concorrente | sim | Refresh *single-flight*: dez requisições expiradas simultâneas aguardam a mesma tentativa. Sem isso, dez renovações paralelas girariam a família de refresh tokens e a detecção de reuso derrubaria a sessão do usuário legítimo | `sessao.test.ts` | execução |
| Ausência de laço de autenticação | sim | Uma repetição por cadeia; rotas `/auth/*` não disparam renovação | `sessao.test.ts` | execução |
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
| Deriva entre ambiente e código | sim | Readiness `schemaVersion` compara a versão esperada pela imagem à maior migration estrutural aplicada e falha nos dois sentidos | `SchemaVersionHealthIndicatorTest`, `ContratosTransversaisTest` | execução |
| Action de CI imutável | sim | Toda entrada `uses:` está fixada por SHA completo; Dependabot acompanha `github-actions` | inspeção de `ci.yml` | inspeção |

## V14 — Data Protection

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Isolamento de dado por cliente | sim **(nível 3)** | RLS forçado mais integridade composta entre tenants; segundo tenant povoado nos testes para que vazamento seja visível | `IsolamentoEntreTenantsTest`, `ReferenciasMultiTenantTest` | execução |
| Credencial de integração | sim | Resposta write-only; segredos de canal e conector HTTP usam chaves AES-GCM distintas e nunca entram em preview/diagnóstico | `BancoSegurancaTest`, `ConectorHttpSeguroIntegracaoTest` | execução |
| Dado pessoal em log | sim | Log registra identificador, nunca conteúdo de mensagem, senha ou token — 17 arquivos com log auditados | inspeção dos 17 pontos de log | inspeção |
| Retenção e expurgo | **não — pertence ao Prompt 18** | Sem política de retenção implementada | — | inspeção |

## V15 — Secure Coding and Architecture

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Fronteira de módulo | sim | Spring Modulith; `api` exposta por `@NamedInterface`, `internal` fechada; nenhum módulo injeta repositório de outro | `FronteiraDeModulosTest` | execução |
| Ausência de `null` em API pública | sim | `Optional`, coleção vazia ou exceção de domínio nomeada | — | inspeção |
| Função de banco com privilégio | sim **(nível 3)** | Cinco funções `SECURITY DEFINER` com `search_path` fixo | `BancoSegurancaTest` | execução |
| Concorrência | sim | `SKIP LOCKED` nas filas e automações; conector HTTP possui semáforo por tenant e orçamento distribuído no Redis | `FilaDeSaidaTest`, `MotorDeAutomacoesTest`, `ConectorHttpSeguroIntegracaoTest` | execução |

## V16 — Security Logging and Error Handling

| Controle | Aplicável | Implementação | Teste | Evidência |
|---|---|---|---|---|
| Erro sem detalhe interno | sim | RFC 9457; exceção de domínio nomeada; stack trace não vai para a resposta | `GlobalExceptionHandlerTest` | execução |
| Correlação | sim | UUID confiável é criado antes da cadeia, fica no MDC e no cabeçalho de toda resposta; entrada forjada é substituída sem ser refletida | `GlobalExceptionHandlerTest`, `HttpProtectionFilterTest` | execução |
| Log de negação | sim | Registra permissão e usuário e **não** o id do recurso alvo, para o log não virar inventário do que existe | inspeção de `AutorizacaoService.negar` | inspeção |
| Log de evento de segurança | sim | Reuso de refresh, negação e SUBSCRIBE permanecem em log; negação e ações críticas também entram em trilha consultável e sanitizada | `AuditoriaCorporativaTest` criado; reexecução backend pendente | inspeção + execução pendente |
| Trilha de auditoria | **implementada; revalidação pendente** | V22 append-only, RLS forçado, permissão `audit.read`, leitura auditada, catálogo versionado e integridade verificada | `AuditoriaCorporativaTest`, `AuditPage.test.tsx` | frontend executado; backend pendente |
| Log com objeto de exceção | sim | Workers e handler global registram somente correlação, IDs técnicos e tipo; nunca mensagem ou objeto bruto do provedor | inspeção dos pontos `log.error/warn` | inspeção |

---

## Resumo

| Situação | Capítulos |
|---|---|
| Atendido no nível 2 ou acima | V1, V2, V3, V4, V6, V7, V8, V11, V12, V13, V14, V15 |
| Não aplicável por ausência de superfície | V10 |
| Atendido parcialmente | V5 (scanner externo), V9 (`SEC-002`), V16 (revalidação V22 pendente) |

Nível 3 aplicado deliberadamente em: isolamento entre tenants, decisão por
registro, MFA, rotação de refresh com detecção de reuso, cifra em repouso,
separação de privilégio no banco e função com `SECURITY DEFINER`. O critério
foi o mesmo em todos: falha atinge todos os clientes ao mesmo tempo, ou destrói
a capacidade de saber que foi atingida.
