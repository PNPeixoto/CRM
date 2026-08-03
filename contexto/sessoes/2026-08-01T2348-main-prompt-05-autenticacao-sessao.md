# Sessão — backend:05 autenticação e sessão

Data: 2026-08-01 23:48 (America/Montevideo)
Branch: `main`
Baseline: `793777d + working-tree`

## Resultado

Prompt 05 concluído. A autenticação cobre senha, rate limit, sessão rotativa,
recuperação, MFA administrativo e revogação sem persistir tokens em claro.

## Mudanças

- Argon2id com salt, pepper externo, política Unicode de 15–200 caracteres e
  hash fictício com o mesmo custo para caminhos inexistentes.
- Access token de 15 minutos; refresh rotativo por hash, família, detecção de
  reuso, uma hora de inatividade e 24 horas absolutas.
- Cookie mínimo e seguro, logout da família e revogação de todos os dispositivos.
- Reset uniforme, token de 256 bits/15 minutos/uso único e revogação de sessões.
- TOTP para OWNER/ADMIN/SUPERADMIN, segredo AES-256-GCM, bloqueio de replay e
  dez códigos de recuperação de uso único armazenados por hash.
- V11 com RLS/`FORCE`, privilégios mínimos e funções de resolução restritas.
- ADR-0007 e `backend/AUTENTICACAO.md` com a matriz do Prompt 05 no Gate B.

## Evidências

- NIST SP 800-63B-4 consultado em fonte oficial em 2026-08-01.
- Benchmark Argon2id: média local de 103 ms em cinco amostras após aquecimento.
- Suíte integral: 82 testes, 0 falhas/erros/ignorados, PostgreSQL 17 e Redis 7 reais.
- Migração limpa V1–V11 e atualização até V11 verificadas.
- Testes de abuso cobrem enumeração, rate limit, CSRF, cookies, refresh
  concorrente/reusado, timeout de sessão, reset, MFA e recovery code.

## Gate B

Prompt 05 concluído. O Gate B permanece aberto até os Prompts 06 e 07 e as
provas frontend F4/F4A correspondentes.

## Próximo

Executar `backend:06` ou `frontend:F4A`, conforme a trilha escolhida.
