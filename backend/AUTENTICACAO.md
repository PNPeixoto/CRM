# Autenticação e sessão

Baseline implementada pelo Prompt 05. A fonte vigente consultada em 2026-08-01
foi o **NIST SP 800-63B-4**, parte do SP 800-63-4 final:

- <https://pages.nist.gov/800-63-4/sp800-63b.html>
- <https://pages.nist.gov/800-63-4/sp800-63b/authenticators/>
- <https://pages.nist.gov/800-63-4/sp800-63b/passwords/>
- <https://csrc.nist.gov/pubs/sp/800/63/4/final>

## Senhas

- Novas senhas e redefinições exigem ao menos 15 caracteres Unicode, depois de
  normalização NFC, aceitam até 200 e não exigem composição artificial.
- Uma blocklist inicial rejeita valores comuns; ela deve evoluir para uma fonte
  mantida e versionada antes da produção pública.
- O hash é Argon2id com salt aleatório automático, `m=32768`, `t=3`, `p=1` e
  saída de 32 bytes. O pepper vem de `APP_PEPPER` e nunca do banco.
- Benchmark local em 2026-08-01, Java 25.0.4/Windows: cinco amostras após
  aquecimento, média de 103 ms. Reproduzir com
  `mvnw.cmd -Pcarga -Dtest=Argon2BenchmarkTest test` no ambiente de deploy.
- O hash fictício usado contra conta inexistente é gerado pelo encoder real na
  subida. Assim, elevar parâmetros não reabre um canal temporal por engano.

## Login e rate limit

Empresa, login, senha e conta inativa falham com o mesmo status, código e
mensagem; caminhos sem linha também pagam Argon2. Login e IP são normalizados.

O bloqueio efetivo usa dois eixos: par `tenant+login+origem` e origem global.
O contador isolado da conta é apenas sinal de risco, não bloqueio. Isso impede
que um atacante negue serviço à vítima repetindo o login dela a partir de outra
origem, sem liberar força bruta da mesma origem.

## Sessões

- Access token HS256: 15 minutos, com `tid`, `sid` e `amr`.
- Refresh: 256 bits aleatórios, somente SHA-256 no banco, cookie `HttpOnly`,
  `Secure` em produção, `SameSite=Strict`, sem `Domain` e path `/api/auth`.
- Rotação é serializada no PostgreSQL. Reuso revoga a família inteira.
- Limites: uma hora de inatividade e 24 horas de duração absoluta. O cookie
  nunca recebe validade maior que a linha persistida.
- Logout revoga a família corrente. `/api/auth/sessions/revoke-all` revoga todos
  os dispositivos. Access tokens já emitidos permanecem válidos por no máximo
  15 minutos; o Prompt 06 deve exigir consulta/step-up onde corte imediato for
  requisito de uma ação de alto impacto.
- O access token permanece somente em memória no frontend; refresh nunca vai
  ao corpo nem fica acessível ao JavaScript.

## Recuperação de senha

`POST /api/auth/password-reset/request` sempre devolve 202 sem corpo. O código
tem 256 bits, validade de 15 minutos, fica somente como hash e invalida pedidos
anteriores. A confirmação é de uso único, aplica a política atual e revoga todas
as sessões.

Produção falha na subida se a entrega estiver ativa sem URL HTTPS. O adaptador
envia ao serviço configurado apenas `{destinatario, codigo}`; não há provedor
inventado. Desenvolvimento e testes desativam entrega e jamais escrevem o
código em log. Variáveis:

- `PASSWORD_RESET_DELIVERY_ENABLED=true`
- `PASSWORD_RESET_DELIVERY_URL=https://...`

## MFA

TOTP é obrigatório para papéis `OWNER`, `ADMIN` e `SUPERADMIN`, conforme decisão
já aprovada no repositório; é opcional para demais pessoas. Antes do cadastro,
perfil administrativo não recebe access nem refresh token.

- Segredo TOTP de 160 bits, SHA-1/6 dígitos/30 segundos, janela ±1 período.
- Reuso do mesmo período é negado por `last_used_step`.
- Segredo cifrado com AES-256-GCM e chave exclusiva `MFA_SECRET_KEY`.
- Recadastro exige senha e, se já houver autenticador, o fator atual.
- Dez códigos de recuperação com 80 bits cada, guardados apenas como SHA-256,
  consumidos uma vez e com uso registrado sem o valor.
- O JWT registra `amr=[pwd,otp]`, deixando o Prompt 06 aplicar step-up a ações
  sensíveis sem redesenhar a sessão.

TOTP não é resistente a phishing. Passkeys/WebAuthn permanecem uma evolução
deliberada, sem acoplar o modelo atual a um provedor de federação.

## Matriz do Prompt 05 no Gate B

| Controle | Implementação | Evidência automatizada |
|---|---|---|
| Login indistinguível e custo uniforme | `AutenticacaoService` + hash fictício real | `LoginRespostaUniformeTest` |
| Argon2id, salt e pepper | `PasswordEncoderConfig` | `PasswordSecurityTest`, `Argon2BenchmarkTest` |
| Rate limit sem lock global da vítima | `BloqueioProgressivoService` | `AutenticacaoSeguraTest#rateLimitDoesNotGloballyLockVictim` |
| Cookie e defesa CSRF | `AuthController`, `SecurityConfig` | `AutenticacaoSeguraTest#cookieCsrfAndRevokeAll` |
| Rotação/reuso/concorrência | lock pessimista e família | `RefreshTokenRotacaoTest` |
| Inatividade/duração absoluta | V11 + `RefreshTokenService` | `AutenticacaoSeguraTest#refreshHasInactivityAndAbsoluteLimits` |
| Reset único e revogação | V11 + `PasswordResetService` | `AutenticacaoSeguraTest#passwordResetIsSingleUseAndRevokesSessions` |
| MFA administrativo/recovery | V11 + `MfaService` | `AutenticacaoSeguraTest#administrativeMfaAndSingleUseRecoveryCode` |
| Privilégio e RLS das novas tabelas | V11 | `BancoSegurancaTest`, `MigracaoDeAtualizacaoTest` |

Isto conclui a parcela do Prompt 05, não o Gate B inteiro. Autorização/IDOR,
matriz ASVS, threat models, scans e provas frontend pertencem aos Prompts 06/07
e F4/F4A.
