# Threat models por fluxo

Um modelo por fluxo, com a mesma estrutura: ativo, fronteira de confiança,
abuso, impacto, controle preventivo, controle detectivo, teste e risco
residual.

Fluxo que o produto ainda não tem aparece **declarado como inexistente**, com a
fronteira que precisará ser tratada. Modelar componente imaginário produz
cobertura de mentira.

Baseline: commit `a603534`.

---

## Superfície real hoje

| Elemento | Quantidade | Observação |
|---|---|---|
| Rotas HTTP | 30 | 9 de autenticação, 20 de domínio, 1 webhook |
| Rotas sem autenticação | 9 | 6 de autenticação pré-sessão, `/v3/api-docs`, `/actuator/health`, `/error` |
| Endpoint WebSocket | 1 | `/ws`, autenticado no frame STOMP CONNECT |
| Webhook de provedor | 1 | Telegram, autenticado por `secret_token` em tempo constante |
| Workers assíncronos | 2 | fila de saída e entrada de webhook |
| Fronteiras de confiança | 5 | navegador, provedor de canal, proxy reverso, PostgreSQL, Redis |

---

## F1 — Login, recuperação e MFA

**Ativo:** credencial do usuário, sessão, segredo TOTP.
**Fronteira:** navegador → aplicação. Entrada não confiável em todos os campos.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| Força bruta de senha | Sequestro de conta | Argon2id com pepper; bloqueio progressivo por tenant, login e origem | Log de bloqueio | `AutenticacaoSeguraTest.rateLimitDoesNotGloballyLockVictim` | Baixo |
| Bloqueio da vítima como negação de serviço | Usuário legítimo travado | Limite global de origem **separado** do limite por login; a vítima não é travada globalmente pelo atacante | — | mesmo teste | Baixo |
| Enumeração de usuário ou empresa | Lista de alvos válidos | Resposta uniforme em login e em recuperação (202 sempre) | — | `LoginRespostaUniformeTest` | Baixo |
| Contorno do bloqueio variando maiúsculas | Força bruta sem limite | Índices `lower()` e chave de bloqueio normalizada — os dois mudam juntos, senão o bloqueio é contornável | — | `AutenticacaoSeguraTest` | Baixo |
| Reuso de token de recuperação | Retomada de conta já recuperada | Uso único, 15 min, armazenado por hash, revoga todas as sessões | Log | `passwordResetIsSingleUseAndRevokesSessions` | Baixo |
| Replay de código TOTP | Segundo fator anulado | Janela com replay bloqueado | Log | `AutenticacaoSeguraTest` | Baixo |
| **Phishing de TOTP em tempo real** | Sequestro completo | **Nenhum** — TOTP não resiste a phishing | — | — | **Aceito**; passkeys/WebAuthn é a saída |
| Cadastro de MFA por terceiro | Segundo fator do atacante | O endpoint é pré-sessão mas **exige empresa, login e senha**: não é bypass, é reautenticação | Log | `AutenticacaoSeguraTest` | Baixo |

## F2 — Tenant e unidade

**Ativo:** todo dado de todos os clientes.
**Fronteira:** token verificado → contexto de tenant → PostgreSQL.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| Informar tenant no corpo, query ou header | Leitura de dado alheio | Tenant vem **apenas** do token; `TenantContext` é preenchido por filtro após verificação da assinatura | — | `TenantAwareDataSourceTest` | Baixo |
| Consulta sem filtro de tenant | Vazamento em massa | RLS `ENABLE + FORCE`; runtime sem `SUPERUSER` e sem `BYPASSRLS`; `set_config` em **toda** aquisição de conexão, vazio quando não há tenant — falha fechada | — | `IsolamentoEntreTenantsTest`, `BancoSegurancaTest` | Baixo |
| Referência cruzada entre tenants | Corrupção silenciosa | Chaves compostas `(tenant_id, id)` | Violação de integridade no banco | `ReferenciasMultiTenantTest` | Baixo |
| Token de um tenant usado no outro | Acesso indevido | Autorização resolve membership no tenant do token; ali ele não existe | Log de negação | `AutorizacaoPorAlcanceTest.membershipDeUmTenantNaoAutorizaNoOutro` | Baixo |
| IDOR por identificador direto | Leitura/escrita de registro alheio | `exigirSobreRegistro` em leitura, escrita e exclusão; recorte de responsável dentro da consulta | Log de negação sem id do alvo | `AutorizacaoPorAlcanceTest` | Baixo |
| Transferir o próprio registro para fora do alcance | Registro órfão inacessível | Verificação **antes e depois** de aplicar | — | `alcanceProprioNaoTransfereRegistroParaForaDoProprioAlcance` | Baixo |
| Gestor de unidade acessando outra unidade | Acesso além do escopo | Escopo `UNIT` **falha fechada** — nenhuma tabela de domínio declara unidade | — | `escopoDeUnidadeNaoDecideSobreRegistroDeDominio` | **Aceito** por ADR-0008; hoje `UNIT` não concede nada |
| Oráculo de existência entre 403 e 404 | Descoberta de identificadores | Permissão da ação é verificada **antes** de buscar o id alvo | — | `AutorizacaoPorAlcanceTest` | Baixo |

## F3 — Webhook de provedor

**Ativo:** mensagem de cliente, integridade da caixa de entrada.
**Fronteira:** internet pública → aplicação, **sem sessão e sem cookie**.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| Webhook forjado | Mensagem falsa na caixa do cliente | `secret_token` conferido com `MessageDigest.isEqual` (tempo constante) | Log de conexão desconhecida | `InboundMessageTest`, `TradutorDeUpdateTelegramTest` | Baixo |
| Ataque de tempo sobre a comparação | Descoberta do segredo | Comparação em tempo constante, não `equals` | — | inspeção | Baixo |
| Enumeração de `channelConnectionId` | Mapa de conexões | Resposta uniforme para conexão inexistente e para assinatura inválida | Log | `InboundMessageTest` | Baixo |
| Perda de mensagem por falha após ACK | Mensagem do cliente sumida | **Persiste antes de confirmar**; só então responde 200 — ADR-0003 | Evento pendente na tabela | `IngestaoTransacionalTest` | Baixo |
| Reentrega duplicada pelo provedor | Mensagem repetida | Idempotência por identificador de evento | — | `InboundMessageTest` | Baixo |
| Corpo malicioso muito grande | Exaustão de memória | Teto global de 1 MiB, contando bytes mesmo sem `Content-Length` | Resposta 413 correlacionada | `HttpProtectionFilterTest` | Baixo |
| Rajada por uma origem | Exaustão de recurso | Janela por origem nas rotas públicas e webhook | 429 com `Retry-After` e correlação | `HttpProtectionFilterTest` | Baixo; produção horizontal exigirá contador distribuído |
| CSRF no webhook | — | Não se aplica: não há navegador, sessão nem cookie neste caminho | — | — | — |

## F4 — WebSocket e tempo real

**Ativo:** conteúdo de conversa em tempo real.
**Fronteira:** navegador → broker STOMP. O RLS **não protege aqui**: quem
publica é a aplicação, e o broker não conhece banco.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| Handshake de origem estrangeira | Conexão a partir de site de terceiro | Lista branca de origem no endpoint STOMP — a política de mesma origem **não** se aplica a WebSocket | — | inspeção | Baixo |
| Token na query string | Segredo em log de proxy e `Referer` | Token viaja no frame CONNECT, não na URL | — | `EscutaDeTopicoTest` | Baixo |
| SUBSCRIBE no tópico do vizinho | Escuta de outro cliente | Tenant do destino conferido contra o do token | Log de recusa | `inscricaoNoTopicoDoVizinhoNemChegaAConsultarPermissao` | Baixo |
| SUBSCRIBE sem permissão | Escuta sem acesso à caixa | Permissão revalidada a cada SUBSCRIBE, e não só no CONNECT | Log de recusa | `inscricaoSemPermissaoEhRecusadaMesmoNoProprioTenant` | Baixo |
| Destino fora do padrão | Alcance de broker interno | Lista exata de destinos; caminho desconhecido é **recusado**, não liberado | Log | `destinoDesconhecidoDoMesmoTenantEhRecusadoSemConsultarPermissao` | Baixo |
| Permissão revogada com conexão aberta | Escuta após desligamento | Revalidação por SUBSCRIBE fecha a janela de novas inscrições. **Inscrição já ativa continua** até reconectar | — | — | **Médio** — ver `SEC-011` |

## F5 — Workers assíncronos

**Ativo:** mensagem em trânsito, integridade da fila.
**Fronteira:** processo interno; sem entrada externa direta.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| Dupla entrega por concorrência | Mensagem duplicada ao cliente | `FOR UPDATE SKIP LOCKED` | — | `FilaDeSaidaTest` | Baixo |
| Reprocessamento infinito | Fila travada, custo | Backoff exponencial e teto de tentativas como dead-letter | Contagem de tentativas | `FilaDeSaidaTest` | Baixo |
| Worker operando fora do tenant | Vazamento entre clientes | Reserva e processamento dentro do contexto do tenant do registro | — | `IngestaoTransacionalTest` | Baixo |
| Exceção do provedor carregando corpo para o log | Conteúdo de cliente em log | **Parcial** — ver `SEC-009` | — | — | **Baixo** |

## F6 — Pipeline e cadeia de suprimentos

**Ativo:** integridade do artefato entregue.
**Fronteira:** dependência de terceiro, ação de CI de terceiro, imagem base.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| Dependência vulnerável entrando sem revisão | Falha herdada | `npm audit` no frontend; Trivy na imagem final cobre dependências Java transitivas e pacotes do sistema | Job `seguranca` falha em alta/crítica | CI versionado; execução externa pendente em `SEC-016` | Baixo |
| Vulnerabilidade conhecida ignorada com `\|\| true` | Controle desligado na prática | Verificador exige **exceção nomeada** com responsável e prazo, e reprova quando o prazo vence | — | verificado nos dois caminhos negativos nesta execução | Baixo |
| Segredo versionado por engano | Credencial pública | gitleaks sobre o histórico completo (`fetch-depth: 0`) | — | 25 commits varridos, sem vazamento | Baixo |
| Exceção de segredo ampla demais | Segredo real escondido | Allowlist casa **valor literal e caminho**; segredo novo no mesmo arquivo continua reprovando | — | verificado por injeção de segredo falso nesta execução | Baixo |
| Ação de CI comprometida | Execução arbitrária no pipeline | Dependabot acompanha `github-actions` | — | — | Médio — ações ainda referenciadas por tag móvel, não por SHA |
| Imagem base desatualizada | CVE herdada do sistema | Dependabot acompanha `docker`; imagem escaneada | `docker scout`: 0 críticas, 0 altas, 9 médias | execução | Baixo |
| Falha de infraestrutura confundida com falha de código | Diagnóstico errado, tempo perdido | Preflight único do Docker antes da suíte; gate rápido explicitamente isento | Mensagem única com instrução de recuperação | gate completo com 123 testes | Baixo |

## F7 — Automação e conector HTTP

**Ativo:** credenciais de integração, rede interna e integridade do efeito
externo. **Fronteira:** definição aprovada → DNS/egress → sistema externo.

| Abuso | Impacto | Preventivo | Detectivo | Teste | Residual |
|---|---|---|---|---|---|
| URL aponta para rede privada ou metadata | SSRF e furto de credencial de infraestrutura | Origem HTTPS estática; todos os A/AAAA privados, reservados, locais e metadata bloqueados | `HTTP_DESTINATION_BLOCKED` sem URL | `PoliticaAntiSsrfTest` | Baixo |
| DNS muda após validação | Rebinding para rede interna | Snapshot DNS aprovado é fixado no resolver da conexão; produção exige proxy que repita a política | Falha sanitizada de DNS/egress | `fixaPrimeiraResolucaoEImpedeDnsRebinding` | Baixo; configuração incorreta do proxy é risco operacional |
| Redirect salta para alvo proibido | Contorno da política inicial | Redirect desativado | `HTTP_REDIRECT_BLOCKED` | `ClienteHttpSeguroTest.naoSegueRedirect` | Baixo |
| Template executa classe, arquivo ou rede | Execução remota | Substituição finita de identificadores técnicos; sem SpEL, JS, shell ou reflexão | Rejeição antes de publicar/executar | `TemplateHttpSeguroTest` | Baixo |
| Retry duplica efeito | Mutação repetida no sistema remoto | Métodos mutáveis exigem header e chave determinística; tentativa concluída converge | Hash e status sanitizados | `ConectorHttpSeguroIntegracaoTest` | Baixo quando o destino honra idempotência |
| Segredo aparece em preview ou trilha | Exposição de API key | AES-GCM, resposta write-only, resolução só no envio; diagnóstico sem request/response | — | `ConectorHttpSeguroIntegracaoTest` | Baixo |
| Resposta lenta/grande esgota recursos | Indisponibilidade | Timeout, limite de bytes, concorrência e orçamento Redis por tenant/conector | Códigos e duração sanitizados | `ClienteHttpSeguroTest` | Baixo |

---

## Fluxos ainda inexistentes

Declarados para que a ausência seja deliberada e não esquecimento. Cada um traz
a fronteira que precisará ser tratada no prompt correspondente.

| Fluxo | Situação | Fronteira a tratar quando existir | Prompt |
|---|---|---|---|
| **Exportação** | Nenhum endpoint exporta | Autorização revalidada **na execução**, não só na criação; o arquivo gerado é um segundo canal de vazamento e precisa de escopo e expiração | 21 |
| **Job agendado** | Não existe | Job roda sem usuário; precisa de identidade própria e de revalidar autorização a cada execução, porque quem o criou pode ter perdido o acesso | 21 |
| **Billing** | Não existe | Manipulação de valor, replay de webhook de pagamento, idempotência de cobrança | 20 |
| **Agente privado** | Não existe | Enrollment, rotação de credencial, execução remota — a maior ampliação de superfície do roteiro | 25, 25B, 25C |

Enquanto não existirem, a revalidação de autorização exigida pelo Prompt 06
para job e exportação é herdada por `Autorizacao`; o lugar de comprová-la é o
Prompt 21.
