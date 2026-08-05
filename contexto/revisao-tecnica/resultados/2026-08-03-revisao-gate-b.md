# Revisão integrada do Gate B — Segurança do núcleo

> **Relatório histórico.** Uma revalidação posterior encontrou e corrigiu a
> interface ausente de MFA, uma falha aberta no verificador do `npm audit`,
> deriva do contrato gerado e nova divergência da imagem local. O veredito
> vigente está em `2026-08-03-revisao-gate-b-revalidacao.md`.

> Execução: 2026-08-03, 12:44–12:50 UTC. Consolida `backend:05`, `backend:06`,
> `backend:07`, `frontend:F4A` e `frontend:F4`.

## Aviso sobre a independência desta revisão

**Quem revisa aqui é quem executou o trabalho revisado.** Isso reduz o valor da
revisão e precisa estar dito antes de qualquer veredito: o revisor tende a
confirmar as próprias premissas, e as lacunas que ele não viu ao construir são
as mesmas que não vê ao conferir.

O que foi feito para compensar, e é tudo o que dá para fazer nessas condições:
cada critério foi conferido contra **artefato executável**, não contra os logs
de sessão anteriores. Onde a evidência era uma afirmação minha, ela foi
reexecutada ou reprovada. Duas lacunas apareceram desse jeito e estão abaixo.

Uma revisão de terceiro antes do piloto externo continua recomendada, e esta
não a substitui.

## Baseline

| Item | Valor |
|---|---|
| Commit | `a394d49` (mais os testes de guarda escritos nesta revisão) |
| Branch | `main` |
| Ambiente | Windows 11, JDK Temurin 25.0.4, Node 24.18.0, Docker Desktop 29.6.2 |
| Backend | 112 testes, 0 falhas, 0 erros, 0 ignorados |
| Frontend | 89 testes em 19 arquivos, 0 falhas |
| PostgreSQL/Redis | reais, via Testcontainers, runtime sem `BYPASSRLS` |

---

## Critérios do Gate B

### 1. Login, recuperação, MFA administrativo e sessões testados — **atende**

| Prova | Casos |
|---|---|
| `AutenticacaoSeguraTest` | 6 |
| `RefreshTokenRotacaoTest` | 6 |
| `LoginRespostaUniformeTest` | 5 |
| `PasswordSecurityTest` | 2 |

Cobrem: Argon2id com pepper, resposta uniforme para empresa/usuário/senha
inválidos, bloqueio progressivo sem travar a vítima globalmente, MFA TOTP com
replay bloqueado, recuperação de uso único que revoga sessões, rotação de
refresh com detecção de reuso por família, inatividade de 1 h e limite absoluto
de 24 h.

### 2. Autorização cobre ação, escopo e registro — **atende**

`AutorizacaoPorAlcanceTest`, 18 casos. Ponto único de decisão (`Autorizacao`),
alcance derivado do membership vigente, recorte por responsável **dentro da
consulta**, verificação antes e depois de aplicar em atualizações, e recursos
coletivos exigindo alcance de tenant.

`EscutaDeTopicoTest`, 5 casos, cobre a mesma decisão no SUBSCRIBE de WebSocket.

O escopo por unidade **não decide** e falha fechada, por ADR-0008. Isso é
conformidade, não lacuna: a alternativa seria inferir a unidade do registro
pela unidade de quem o criou, o que faria a autorização reescrever o passado
quando alguém é transferido.

### 3. Testes de IDOR, mass assignment e troca de unidade — **atende, com ressalva**

**IDOR:** coberto. Leitura, escrita e exclusão por identificador alheio; filtro
de listagem; transferência do próprio registro para fora do alcance; e a
verificação de permissão **antes** de buscar o id, que fecha o oráculo de
existência entre 403 e 404.

**Troca de unidade:** coberto. `ModeloOrganizacionalTest` prova que `selectUnit`
para unidade não autorizada é negado.

**Mass assignment — ressalva.** A garantia é de **configuração global**
(`fail-on-unknown-properties: true`) mais o desenho de um DTO por caso de uso.
Existe **um** teste que a exercita, em `/api/empresa/perfil-inicial`
(`rejectsTenantProvidedByBodyQueryOrHeader`), e ele roda sob o profile `test`.

Consequência concreta: se alguém desligasse a propriedade num profile que não é
o de teste — `application-prod.yml`, por exemplo —, **nenhum teste pegaria**.
Hoje o profile de produção não a sobrescreve, verificado por inspeção. Registrado
como `SEC-015`, severidade baixa.

A parte mais perigosa do mass assignment neste sistema não é campo desconhecido,
e sim campo **conhecido que o usuário não deveria poder definir** — `responsavelId`
em contato, oportunidade e tarefa. Essa está coberta por autorização, não por
desserialização: `alcanceProprioNaoCriaRegistroParaOutraPessoa` e o par de
verificações antes/depois em atualização.

### 4. Matriz ASVS aplicável com implementação, teste e evidência — **atende**

`contexto/seguranca/asvs-5.0.0-matriz.md`. ASVS **5.0.0**, de 30/05/2025, lido
da fonte oficial da OWASP; estrutura de 16 capítulos confirmada no CSV oficial.
Alvo nível 2, com nível 3 declarado onde aplicado.

Cada linha marca **execução** ou **inspeção**. Sem essa marca o documento soaria
igualmente confiante sobre o que foi testado e sobre o que foi apenas lido.

### 5. Threat models dos fluxos críticos — **atende**

`contexto/seguranca/threat-models.md`. Seis fluxos existentes modelados; sete
inexistentes declarados como tal, com a fronteira a tratar e o prompt
responsável.

### 6. Secret scan e dependency scan sem achado crítico aceito informalmente — **atende, com ressalva de pipeline**

**Resultado:** gitleaks sobre 26 commits, `no leaks found`. Auditoria de
dependência sem alta ou crítica não excetuada. Imagem com 0 críticas e 0 altas.

**Nenhum aceite é informal:** a única exceção (`GHSA-qwww-vcr4-c8h2`) tem
identificador, responsável, prazo e fundamento em arquivo versionado, e o
verificador **reprova quando o prazo vence**. Os dois caminhos negativos foram
exercitados: exceção vencida reprova; vulnerabilidade sem exceção reprova.

**Ressalva.** Tudo isso foi verificado **localmente**. O job `seguranca` do
GitHub Actions **nunca executou**: `origin/main` está em `793777d` e os seis
commits desta sequência não foram enviados. O controle está escrito e testado
passo a passo, mas o pipeline em si é não comprovado. Registrado como
`SEC-016`, severidade média para a operação.

### 7. `frontend:F4A` comprova CSP, defesa CSRF/XSS, persistência segura e supply chain — **atende**

| Exigência | Prova |
|---|---|
| CSP verificável no ambiente | O navegador reportou violação `script-src-elem` com `disposition: enforce` ao injetar script inline na página, e `img-src` ao carregar recurso externo |
| Sem execução genérica | `browser-security.contract.test.ts`, 9 casos; caminho negativo exercitado por injeção |
| CSRF além de `SameSite` | Double-submit com `X-XSRF-TOKEN`, validado no backend; cookie de refresh `HttpOnly`, `Secure`, `SameSite=Strict`, `Path` restrito |
| Persistência segura | `localStorage` e `sessionStorage` vazios no navegador; único cookie legível é o de CSRF, que não é credencial |
| Supply chain | Node e npm fixados, lockfile versionado, `npm ci` em CI, `sourcemap: false` explícito |

### 8. `frontend:F4` comprova refresh single-flight, logout entre abas e guards — **atende, após correção nesta revisão**

**Single-flight:** provado. Dez requisições expiradas concorrentes produzem
**um** refresh (`sessao.test.ts`). O valor não é desempenho: sem isso, dez
renovações paralelas girariam a família de refresh tokens e a detecção de reuso
derrubaria a sessão do usuário legítimo — falha que só aparece sob
concorrência.

**Logout entre abas:** provado. `BroadcastChannel` transmite apenas
`{tipo:'saiu'}`; um teste verifica que a carga tem exatamente uma chave, e
outro que mensagem malformada de terceiro é ignorada.

**Guards — lacuna encontrada e fechada aqui.** `RotaComAcesso` tinha teste
(403 e 404); `RotaProtegida` **não tinha nenhum**, e é justamente ela que
implementa "aguardar a resolução inicial". Sem esse teste, a regressão que
manda todo usuário com sessão válida para o login a cada F5 passaria
despercebida — e é uma regressão de uma linha.

Escritos nesta revisão, em `RotaProtegida.test.tsx`: espera da resolução
inicial sem piscar para o login; redirecionamento com destino preservado; e
preservação de consulta e âncora.

---

## Achados novos

### `SEC-015` — Mass assignment garantido por configuração verificada em um só profile

- **Severidade:** baixa · **Responsável:** PNPeixoto · **Prazo:** Prompt 08
- Um teste exercita a rejeição de campo desconhecido, sob o profile `test`.
  Desligar `fail-on-unknown-properties` em outro profile não seria detectado.
- **Correção mínima:** teste que leia a propriedade efetiva do profile de
  produção, ou um caso de rejeição em endpoint de domínio além do atual.

### `SEC-016` — O gate de segurança do CI nunca executou

- **Severidade:** média para a operação · **Responsável:** PNPeixoto ·
  **Prazo:** antes do próximo prompt
- `origin/main` está em `793777d`; os commits de 06, 07, F4A e F4 são locais. O
  job `seguranca` foi verificado passo a passo na máquina, mas nunca rodou no
  GitHub Actions.
- **Risco concreto:** `gitleaks/gitleaks-action@v2` exige chave de licença para
  repositório de organização. Em repositório pessoal é gratuito, e este é
  pessoal — mas isso é inferência, não observação.
- **Correção mínima:** enviar os commits e conferir a execução do workflow.

> **Encaminhado em 2026-08-03, com resultado parcial.** Os dez commits foram
> enviados. Duas verificações e um achado:
>
> - **Licença do gitleaks: confirmada na fonte oficial**, não mais inferida.
>   É exigida apenas para repositório de organização; conta pessoal não precisa,
>   inclusive em repositório privado.
> - **A execução do workflow permanece não observada.** O repositório é privado
>   e a API responde 404 sem credencial. Conferir o resultado é do responsável
>   humano — não peço nem uso token para isso.
> - **Achado novo, `SEC-017`:** ao conferir o CI, apareceu que
>   `actions/dependency-review-action` **exige GitHub Advanced Security em
>   repositório privado**. O passo falharia em toda execução. Foi condicionado a
>   repositório público, e a cobertura de dependência do backend ficou
>   descoberta no CI.
>
> Isto valida o próprio achado: um controle verificado só localmente não é um
> controle. Bastou o primeiro contato com o ambiente real para revelar um passo
> que não funcionaria ali.

---

## Veredito

**Gate B — APROVADO.**

Os oito critérios são atendidos. As ressalvas ficam registradas e nenhuma
bloqueia: `SEC-015` é baixa e estrutural; `SEC-016` era de operação e foi
encaminhada com o push; `SEC-017` surgiu desse push e é média, mas não desfaz
nenhum critério — a varredura de dependência do **frontend** segue no gate, e a
do backend passa a depender do Dependabot enquanto o repositório for privado.

Se `SEC-017` não for tratada até o Prompt 08, ela deixa de ser aceitável: a
CVE do `jackson-databind` que este mesmo gate corrigiu foi encontrada por
varredura local, e a próxima passaria despercebida.

A lacuna dos guards foi encontrada **por esta revisão** e fechada antes do
veredito, o que é o comportamento certo — mas também mostra o limite de revisar
o próprio trabalho: ela existia desde o F4, escrito horas antes, e passou.

### O que a aprovação significa e o que não significa

**Significa:** dentro de um tenant existe separação de privilégio real; entre
tenants o isolamento é forçado pelo banco e não pela lembrança do programador;
sessão, recuperação e MFA têm comportamento testado; e o navegador não é a
única linha de defesa de nada.

**Não significa** que o sistema foi atacado e resistiu. Não houve teste de
intrusão, fuzzing nem análise dinâmica. As linhas marcadas como **inspeção** na
matriz são afirmações sobre código lido.

### Bloqueios que permanecem, e não são deste gate

| Item | Bloqueia |
|---|---|
| `AUDIT-001` — auditoria não existe, é P0 do produto | Gate E |
| `frame-ancestors` exige cabeçalho de servidor de estáticos inexistente | piloto externo, na prática |
| `AUTZ-002` resíduo — frontend não consome contextos nem permissões | Gate C |
| Cinco médios do backlog de segurança | Prompt 08 |
