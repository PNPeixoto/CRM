# Gates verificáveis

Cada gate só é aprovado com evidência reproduzível. Vídeo complementa, mas não
substitui comando, teste e artefato. O registro deve conter commit, ambiente,
data/hora UTC, comando, resultado, artefato e responsável.

## Gate A — Fundação

- ambientes documentados e reproduzíveis;
- build e testes base verdes;
- migrations em PostgreSQL real;
- papéis de migration/runtime separados;
- RLS validado com runtime sem `SUPERUSER`/`BYPASSRLS`;
- modelo organizacional e escopos aprovados.

## Gate B — Segurança do núcleo

- login, recuperação, MFA administrativo e sessões testados;
- autorização cobre ação, escopo e registro;
- testes de IDOR, mass assignment e troca de unidade;
- matriz ASVS aplicável com implementação, teste e evidência;
- threat models dos fluxos críticos;
- secret scan e dependency scan sem achado crítico aceito informalmente.
- `frontend:F4A` comprova CSP, defesa CSRF/XSS, persistência segura e supply chain;
- `frontend:F4` comprova refresh single-flight, logout entre abas e guards.

## Gate C — Produto utilizável

- contratos HTTP padronizados e limites impostos pelo servidor;
- shell acessível em WCAG 2.2 AA;
- slices P0 exercitados ponta a ponta;
- onboarding/segmentos não confundem navegação com autorização;
- jornadas críticas E2E reproduzíveis.
- `frontend:F1`/`F2` consolidam tokens, componentes e shell sem implementação
  paralela ao Prompt 09;
- cada slice de interface tem execução `frontend:F7:<dominio>` e testes no PR.

## Gate D — Omnichannel

- webhook autentica e persiste antes do sucesso;
- idempotência inbound/outbound comprovada;
- adapters têm contract tests com fixtures oficiais versionadas;
- mídia isolada, limitada, validada e retida por política;
- inbox pagina por cursor, reconecta sem lacunas e reautoriza WebSocket;
- `frontend:F8` comprova reconciliação, deduplicação e fallback adaptativo;
- automações e conector HTTP cumprem quotas e controles de segurança.

## Gate E — Operação e conformidade

- auditoria, retenção e direitos do titular testados;
- entitlements, medição, billing e relatórios reconciliáveis;
- SLOs, alertas e runbooks exercitados;
- backup restaurado com RPO/RTO medidos e integridade validada;
- rollback de aplicação e migration ensaiado;
- CI/CD produz artefato rastreável e promove por ambiente.
- `frontend:F10`–`F13` auditam WCAG, performance, cobertura e telemetria
  minimizada; testes de base continuam sendo entregues desde `frontend:F0A`.

## Gate F — Integrações privadas e escala

- agente privado sem shell arbitrário, com jobs/updates assinados;
- arquitetura consolidada sem violações de módulo;
- otimizações justificadas por baseline antes/depois;
- carga representativa registra latência, erro e saturação;
- escala horizontal preserva sessão, tempo real, filas e idempotência.
